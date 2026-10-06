package fr.gdd.fedivers.sources;

import fr.gdd.fedivers.FediversContext;
import fr.gdd.jena.utils.OpCloningUtil;
import fr.gdd.jena.visitors.ReturningOpBaseVisitor;
import fr.gdd.jena.visitors.ReturningOpVisitor;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.op.*;
import org.apache.jena.sparql.expr.E_Exists;

/**
 * Transform a DISTINCT(Project(V, P)) by a:
 * DISTINCT(tp/qp on V) FILTER EXISTS(Project(V, P))
 */
public class ToDistinctByProduceTPQPThenFilterExists implements ReturningOpVisitor<Op> {

    final FediversContext context;

    public ToDistinctByProduceTPQPThenFilterExists(FediversContext context) {
        this.context = context;
    }

    @Override
    public Op visit(OpDistinct distinct) {
        if (distinct.getSubOp() instanceof OpProject project) {
            Op producer = new LeftPicker().visit(project.getSubOp());
            // TODO, assert that all the projected variables are produced
            return OpFilter.filter(new E_Exists(project), new OpDistinct(OpCloningUtil.clone(project, producer)));
        }
        throw new UnsupportedOperationException("DISTINCT without project variables is not handled yet.");
    }

    /**
     * Within a service, picks the leftest triple pattern, with the following
     * rationale: if the join orderer did a good job, it put the most selective
     * triple pattern first.
     */
    public static class LeftPicker extends ReturningOpBaseVisitor {

        @Override
        public Op visit(OpSequence sequence) {
            return sequence.getElements().stream().map(this::visit).filter(s -> !s.equalTo(OpTable.empty(), null))
                    .findFirst().orElse(OpTable.empty());
        }

        @Override
        public Op visit(OpBGP bgp) { return new OpTriple(bgp.getPattern().getList().getFirst()); }
        @Override public Op visit(OpJoin join) { return this.visit((Op2) join); }
        @Override public Op visit(OpMinus minus) { return this.visit((Op2) minus); }
        @Override public Op visit(OpConditional cond) { return this.visit((Op2) cond);  }
        @Override public Op visit(OpLeftJoin lj) {  return this.visit((Op2) lj); }
        @Override public Op visit(OpExtend extend) { return visit(extend.getSubOp()); }
        @Override public Op visit(OpDistinct distinct) { return this.visit(distinct.getSubOp());}
        @Override public Op visit(OpSlice slice) { return this.visit(slice.getSubOp()); }
        @Override public Op visit(OpProject project) { return visit(project.getSubOp()); }
        @Override public Op visit(OpUnion union) { return visit((Op2) union); }
        @Override public Op visit(OpTable table) { return OpTable.empty(); }
        @Override public Op visit(OpOrder orderBy) { return visit(orderBy.getSubOp()); }
        @Override public Op visit(OpFilter filter) { return visit(filter.getSubOp()); }

        public Op visit(Op2 op) {
            final Op left = visit(op.getLeft());
            final Op right = visit(op.getRight());
            if (left.equalTo(OpTable.empty(), null)) return right;
            if (right.equalTo(OpTable.empty(), null)) return left;
            return left;
        }
    }

}
