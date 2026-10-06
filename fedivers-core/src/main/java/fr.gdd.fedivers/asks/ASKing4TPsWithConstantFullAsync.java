package fr.gdd.fedivers.asks;

import fr.gdd.fedivers.FediversContext;
import fr.gdd.jena.utils.OpCloningUtil;
import fr.gdd.jena.visitors.ReturningOpVisitor;
import org.apache.jena.graph.Node;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.op.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Simplify the query by running asks on triple patterns. This is important
 * since (i) pruning empty results early avoids useless calls, and (ii) could
 * allow better factorizing of common prefixes (false positive source being
 * considered as noise).
 * /!\ This creates async calls instead of threads as in FedUP.
 * On Q05, when the window is sufficiently large, the number of asks to do
 * simultaneously increases, and the global execution time decreases
 * from 13s we reach 7s.
 */
public class ASKing4TPsWithConstantFullAsync implements ReturningOpVisitor<CompletableFuture<Op>> {

    final FediversContext context;

    public ASKing4TPsWithConstantFullAsync(FediversContext context) {
        this.context = context;
    }

    @Override
    public CompletableFuture<Op> visit(OpService req) {
        // even if it returns no results, we keep it to preserve the  structure of the query.
        if (req.getService().isVariable()) return CompletableFuture.completedFuture(OpTable.empty());
        return new AskingAService(context, req.getService()).visit(req.getSubOp())
                .thenApply(result -> result ? req : OpTable.empty());
    }

    @Override
    public CompletableFuture<Op> visit(OpFilter filter) {
        return visit(filter.getSubOp()).thenApply(o -> OpCloningUtil.clone(filter, o));
    }

    @Override
    public CompletableFuture<Op> visit(OpJoin join) {
        final CompletableFuture<Op> left = visit(join.getLeft());
        final CompletableFuture<Op> right = visit(join.getRight());
        return CompletableFuture.allOf(left, right).thenApply(_ -> OpJoin.create(left.join(), right.join()));
    }

    @Override
    public CompletableFuture<Op> visit(OpTable table) {
        return CompletableFuture.completedFuture(table);
    }

    @Override
    public CompletableFuture<Op> visit(OpUnion union) {
        final CompletableFuture<Op> left = visit(union.getLeft());
        final CompletableFuture<Op> right = visit(union.getRight());
        return CompletableFuture.allOf(left, right).thenApply(_ -> OpUnion.create(left.join(), right.join()));
    }

    public CompletableFuture<Op> visit(OpSequence sequence) {
        List<CompletableFuture<Op>> handles = new ArrayList<>();
        sequence.getElements().forEach(o -> handles.add(visit(o)));
        return CompletableFuture.allOf(handles.toArray(CompletableFuture[]::new)).thenApply(_ -> {
            OpSequence newSequence = OpSequence.create();
            handles.forEach(h -> newSequence.add(h.join()));
            return newSequence;
        });
    }

    /**
     * Returns true -> the service might return some results, or not;
     *         false -> for sure the service does not return any result.
     * This is based on ASK queries performed to remote endpoints.
     */
    public record AskingAService (FediversContext context, Node source) implements ReturningOpVisitor<CompletableFuture<Boolean>> {

        @Override
        public CompletableFuture<Boolean> visit(OpTriple triple) {
            boolean hasConstant = !triple.getTriple().getSubject().isVariable() || !triple.getTriple().getObject().isVariable();
            if (!hasConstant) return CompletableFuture.completedFuture(true);
            // otherwise, perform and/or await an ASK query:
            return new ASKRunnable(context.pool4asks, context.asks, source, triple.getTriple()).run();
        }

        @Override
        public CompletableFuture<Boolean> visit(OpBGP bgp) {
            List<CompletableFuture<Boolean>> futures =
                    bgp.getPattern().getList().stream()
                            .map(t -> visit(new OpTriple(t)))
                            .toList();

            return CompletableFuture
                    .allOf(futures.toArray(CompletableFuture[]::new))
                    .thenApply(_ -> futures.stream().allMatch(CompletableFuture::join));
        }

        @Override public CompletableFuture<Boolean> visit(OpTable table) { return CompletableFuture.completedFuture(true); }

        @Override public CompletableFuture<Boolean> visit(OpProject project) { return visit(project.getSubOp()); }
        @Override public CompletableFuture<Boolean> visit(OpFilter filter) { return visit(filter.getSubOp()); }
        @Override public CompletableFuture<Boolean> visit(OpExtend extend) { return visit(extend.getSubOp()); }
        @Override public CompletableFuture<Boolean> visit(OpDistinct distinct) { return visit(distinct.getSubOp()); }
        @Override public CompletableFuture<Boolean> visit(OpGroup groupBy) { return visit(groupBy.getSubOp()); }
        @Override public CompletableFuture<Boolean> visit(OpSlice slice) { return visit(slice.getSubOp()); }
        @Override public CompletableFuture<Boolean> visit(OpOrder orderBy) { return visit(orderBy.getSubOp()); }
        @Override public CompletableFuture<Boolean> visit(OpConditional cond) { return visit(cond.getLeft()); } // only left matters
        @Override public CompletableFuture<Boolean> visit(OpLeftJoin lj) { return visit(lj.getLeft()); } // only left matters
        @Override public CompletableFuture<Boolean> visit(OpMinus minus) { return visit(minus.getLeft()); } // only left matters

        @Override public CompletableFuture<Boolean> visit(OpJoin join) {
            final CompletableFuture<Boolean> left = visit(join.getLeft());
            final CompletableFuture<Boolean> right = visit(join.getRight());
            return CompletableFuture.allOf(left, right).thenApply(_ -> left.join() && right.join());
        }
        @Override public CompletableFuture<Boolean> visit(OpUnion union) {
            final CompletableFuture<Boolean> left = visit(union.getLeft());
            final CompletableFuture<Boolean> right = visit(union.getRight());
            return CompletableFuture.allOf(left, right).thenApply(_ -> left.join() || right.join());
        }

        @Override
        public CompletableFuture<Boolean> visit(OpSequence sequence) {
            List<CompletableFuture<Boolean>> futures =
                    sequence.getElements().stream()
                            .map(this::visit)
                            .toList();

            return CompletableFuture
                    .allOf(futures.toArray(CompletableFuture[]::new))
                    .thenApply(_ -> futures.stream().allMatch(CompletableFuture::join));
        }

        @Override
        public CompletableFuture<Boolean> visit(OpPath path) {
            throw new UnsupportedOperationException("Property paths not allowed inside services for now.");
        }

        @Override
        public CompletableFuture<Boolean> visit(OpQuadPattern quads) {
            throw new UnsupportedOperationException("Quad patterns not allows inside services for now.");
        }

        @Override
        public CompletableFuture<Boolean> visit(OpGraph graph) {
            throw new UnsupportedOperationException("Graph not allowed inside services for now.");
        }

        @Override
        public CompletableFuture<Boolean> visit(OpQuadBlock block) {
            throw new UnsupportedOperationException("Quad block not allowed inside services for now.");
        }

        @Override
        public CompletableFuture<Boolean> visit(OpService req) {
            throw new UnsupportedOperationException("Service not allowed inside services for now.");
        }

        @Override
        public CompletableFuture<Boolean> visit(OpQuad quad) {
            throw new UnsupportedOperationException("Quad not allowed inside services for now.");
        }
    }

}
