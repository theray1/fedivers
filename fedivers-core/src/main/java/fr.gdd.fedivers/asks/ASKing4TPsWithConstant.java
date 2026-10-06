package fr.gdd.fedivers.asks;

import fr.gdd.fedivers.FediversContext;
import fr.gdd.jena.visitors.ReturningOpBaseVisitor;
import fr.gdd.jena.visitors.ReturningOpVisitor;
import org.apache.jena.graph.Node;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.op.*;

import java.util.concurrent.CompletableFuture;

/**
 * Simplify the query by running asks on triple patterns. This is important
 * since (i) pruning empty results early avoids useless calls, and (ii) could
 * allow better factorizing of common prefixes (false positive source being
 * considered as noise).
 * /!\ This creates async calls instead of threads as in FedUP.
 *
 */
public class ASKing4TPsWithConstant extends ReturningOpBaseVisitor {

    final FediversContext context;

    public ASKing4TPsWithConstant(FediversContext context) {
        this.context = context;
    }

    @Override
    public Op visit(OpService req) {
        // even if it returns no results, we keep it to preserve the  structure of the query.
        return new AskingAService(context, req.getService()).visit(req.getSubOp()) ? req : OpTable.empty();
    }

    /**
     * Returns true -> the service might return some results, or not;
     *         false -> for sure the service does not return any result.
     * This is based on ASK queries performed to remote endpoints.
     */
    public record AskingAService (FediversContext context, Node source) implements ReturningOpVisitor<Boolean> {

        @Override
        public Boolean visit(OpTriple triple) {
            boolean hasConstant = !triple.getTriple().getSubject().isVariable() || !triple.getTriple().getObject().isVariable();
            if (!hasConstant) return true;
            // otherwise, perform and/or await an ASK query:
            CompletableFuture<Boolean> result = new ASKRunnable(context.pool4asks, context.asks, source, triple.getTriple()).run();
            return result.join();
        }

        @Override
        public Boolean visit(OpBGP bgp) {
            return bgp.getPattern().getList().stream().allMatch(t -> visit(new OpTriple(t)));
        }

        @Override public Boolean visit(OpTable table) { return true; }

        @Override public Boolean visit(OpProject project) { return visit(project.getSubOp()); }
        @Override public Boolean visit(OpFilter filter) { return visit(filter.getSubOp()); }
        @Override public Boolean visit(OpExtend extend) { return visit(extend.getSubOp()); }
        @Override public Boolean visit(OpDistinct distinct) { return visit(distinct.getSubOp()); }
        @Override public Boolean visit(OpGroup groupBy) { return visit(groupBy.getSubOp()); }
        @Override public Boolean visit(OpSlice slice) { return visit(slice.getSubOp()); }
        @Override public Boolean visit(OpOrder orderBy) { return visit(orderBy.getSubOp()); }
        @Override public Boolean visit(OpConditional cond) { return visit(cond.getLeft()); } // only left matters
        @Override public Boolean visit(OpLeftJoin lj) { return visit(lj.getLeft()); } // only left matters
        @Override public Boolean visit(OpMinus minus) { return visit(minus.getLeft()); } // only left matters

        @Override public Boolean visit(OpJoin join) { return visit(join.getLeft()) && visit(join.getRight()); }
        @Override public Boolean visit(OpUnion union) { return visit(union.getLeft()) || visit(union.getRight()); }

        @Override public Boolean visit(OpSequence sequence) { return sequence.getElements().stream().allMatch(this::visit); }

        @Override
        public Boolean visit(OpPath path) {
            throw new UnsupportedOperationException("Property paths not allowed inside services for now.");
        }

        @Override
        public Boolean visit(OpQuadPattern quads) {
            throw new UnsupportedOperationException("Quad patterns not allows inside services for now.");
        }

        @Override
        public Boolean visit(OpGraph graph) {
            throw new UnsupportedOperationException("Graph not allowed inside services for now.");
        }

        @Override
        public Boolean visit(OpQuadBlock block) {
            throw new UnsupportedOperationException("Quad block not allowed inside services for now.");
        }

        @Override
        public Boolean visit(OpService req) {
            throw new UnsupportedOperationException("Service not allowed inside services for now.");
        }

        @Override
        public Boolean visit(OpQuad quad) {
            throw new UnsupportedOperationException("Quad not allowed inside services for now.");
        }
    }

}
