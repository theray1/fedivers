package fr.gdd.jena.visitors;

import fr.gdd.jena.utils.OpCloningUtil;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.op.*;
import org.apache.jena.sparql.expr.E_Exists;
import org.apache.jena.sparql.expr.E_NotExists;
import org.apache.jena.sparql.expr.Expr;
import org.apache.jena.sparql.expr.ExprFunctionOp;
import org.apache.jena.sparql.path.*;

/**
 * A convenience class that visits the operator, building another operator based on
 * an input args. By default, it copies the operator it visits. One must override
 * the desired method to change its behavior.
 * @param <A> The arguments as additional input to the visitor.
 */
public class ReturningArgsOpBaseVisitor<A> implements ReturningArgsOpVisitor<Op,A>, ReturningArgsPathVisitor<Path,A> {

    @Override public Op visit(OpTriple triple, A args) { return triple; }
    @Override public Op visit(OpQuad quad, A args) { return quad; }
    @Override public Op visit(OpBGP bgp, A args) { return bgp; }
    @Override public Op visit(OpTable table, A args) {return table; }
    @Override public Op visit(OpQuadBlock block, A args) { return block; }

    @Override
    public Op visit(OpGraph graph, A args) {
        return OpCloningUtil.clone(graph, this.visit(graph.getSubOp(), args));
    }

    @Override
    public Op visit(OpDistinct distinct, A args) { return OpCloningUtil.clone(distinct, this.visit(distinct.getSubOp(), args)); }

    @Override
    public Op visit(OpService req, A args) {
        return OpCloningUtil.clone(req, this.visit(req.getSubOp(), args));
    }

    @Override
    public Op visit(OpSlice slice, A args) {
        return OpCloningUtil.clone(slice, this.visit(slice.getSubOp(), args));
    }

    @Override
    public Op visit(OpExtend extend, A args) { return OpCloningUtil.clone(extend, this.visit(extend.getSubOp(), args)); }

    @Override
    public Op visit(OpFilter filter, A args) {
        final var self = this;
        var propagates2existsNotExists = new ReturningExprBaseVisitor() {
            @Override
            public Expr visit(ExprFunctionOp ef) {
                return switch (ef) {
                    case E_Exists exists -> new E_Exists(ReturningArgsVisitorRouter.visit(self, exists.getGraphPattern(), args));
                    case E_NotExists not_exists  -> new E_NotExists(ReturningArgsVisitorRouter.visit(self, not_exists.getGraphPattern(), args));
                    default -> ef.deepCopy();
                };
            }
        };
        return OpFilter.filterBy(propagates2existsNotExists.visit(filter.getExprs()) /* modified filter */,
                ReturningArgsVisitorRouter.visit(this,filter.getSubOp(),args) /* AND modified sub-query */ );
    }

    @Override
    public Op visit(OpGroup groupBy, A args) { return OpCloningUtil.clone(groupBy, this.visit(groupBy.getSubOp(), args)); }

    @Override
    public Op visit(OpOrder orderBy, A args) { return OpCloningUtil.clone(orderBy, this.visit(orderBy.getSubOp(), args)); }

    @Override
    public Op visit(OpProject project, A args) { return OpCloningUtil.clone(project, this.visit(project.getSubOp(), args)); }

    @Override
    public Op visit(OpConditional cond, A args) {
        return OpCloningUtil.clone(cond, this.visit(cond.getLeft(), args), this.visit(cond.getRight(), args));
    }

    @Override
    public Op visit(OpUnion union, A args) {
        return OpCloningUtil.clone(union, this.visit(union.getLeft(), args), this.visit(union.getRight(), args));
    }

    @Override
    public Op visit(OpSequence sequence, A args) {
        OpSequence newSequence = OpSequence.create();
        sequence.getElements().stream().map(e -> this.visit(e, args)).forEach(newSequence::add);
        return newSequence;
    }

    @Override
    public Op visit(OpJoin join, A args) {
        return OpJoin.create(this.visit(join.getLeft(), args), this.visit(join.getRight(), args));
    }

    @Override
    public Op visit(OpLeftJoin lj, A args) {
        return OpCloningUtil.clone(lj, this.visit(lj.getLeft(), args), this.visit(lj.getRight(), args));
    }

    @Override
    public Op visit(OpMinus minus, A args) {
        return OpCloningUtil.clone(minus, this.visit(minus.getLeft(), args), this.visit(minus.getRight(), args));
    }

    @Override
    public Op visit(OpPath path, A args) {
        return OpCloningUtil.clone(path, this.visit(path.getTriplePath().getPath(), args));
    }

    @Override
    public Path visit(P_Inverse inverse, A args) {
        return OpCloningUtil.clone(inverse, this.visit(inverse.getSubPath(), args));
    }

    @Override
    public Path visit(P_OneOrMore1 oneOrMore, A args) {
        return OpCloningUtil.clone(oneOrMore, this.visit(oneOrMore.getSubPath(), args));
    }

    @Override
    public Path visit(P_ZeroOrMore1 zeroOrMore, A args) {
        return OpCloningUtil.clone(zeroOrMore, this.visit(zeroOrMore.getSubPath(), args));
    }

    @Override
    public Path visit(P_Seq seq, A args) {
        return OpCloningUtil.clone(seq, this.visit(seq.getLeft(), args), this.visit(seq.getRight(), args));
    }

    @Override
    public Path visit(P_Alt alt, A args) {
        return OpCloningUtil.clone(alt, this.visit(alt.getLeft(), args), this.visit(alt.getRight(), args));
    }

    @Override public Path visit(P_Link link, A args) { return link; }
}
