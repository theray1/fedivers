package fr.gdd.jena.visitors;

import fr.gdd.jena.utils.OpCloningUtil;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.op.*;
import org.apache.jena.sparql.expr.E_Exists;
import org.apache.jena.sparql.expr.E_NotExists;
import org.apache.jena.sparql.expr.Expr;
import org.apache.jena.sparql.expr.ExprFunctionOp;
import org.apache.jena.sparql.path.*;

import java.util.List;

/**
 * A visitor dedicated to returning `Op`. Useful for building back plans.
 */
public class ReturningOpBaseVisitor implements ReturningOpVisitor<Op>, ReturningPathVisitor<Path> {

    public Op visit(Op op) { return ReturningVisitorRouter.visit(this, op); }

    @Override public Op visit(OpService req) { return req; }
    @Override public Op visit(OpTriple triple) { return triple; }
    @Override public Op visit(OpQuad quad) { return quad; }
    @Override public Op visit(OpQuadBlock block) { return block; }
    @Override public Op visit(OpQuadPattern quads) { return quads; }
    @Override public Op visit(OpBGP bgp) { return bgp; }
    @Override public Op visit(OpTable table) { return table; }

    @Override
    public Op visit(OpGraph graph) {
        return OpCloningUtil.clone(graph, this.visit(graph.getSubOp()));
    }

    @Override
    public Op visit(OpSequence sequence) {
        return OpCloningUtil.clone(sequence, this.visit(sequence.getElements()));
    }

    @Override
    public Op visit(OpLeftJoin lj) {
        return OpCloningUtil.clone(lj, ReturningVisitorRouter.visit(this, lj.getLeft()),
                ReturningVisitorRouter.visit(this, lj.getRight()));
    }

    @Override
    public Op visit(OpConditional cond) {
        return OpCloningUtil.clone(cond, ReturningVisitorRouter.visit(this, cond.getLeft()),
                ReturningVisitorRouter.visit(this, cond.getRight()));
    }

    @Override
    public Op visit(OpFilter filter) {
        final var self = this;
        var propagates2existsNotExists = new ReturningExprBaseVisitor() {
            @Override
            public Expr visit(ExprFunctionOp ef) {
                return switch (ef) {
                    case E_Exists exists -> new E_Exists(ReturningVisitorRouter.visit(self, exists.getGraphPattern()));
                    case E_NotExists not_exists  -> new E_NotExists(ReturningVisitorRouter.visit(self, not_exists.getGraphPattern()));
                    default -> ef.deepCopy();
                };
            }
        };
        return OpFilter.filterBy(propagates2existsNotExists.visit(filter.getExprs()) /* modified filter */,
                ReturningVisitorRouter.visit(this,filter.getSubOp()) /* AND modified sub-query */ );
    }

    @Override
    public Op visit(OpUnion union) {
       return new OpUnion(ReturningVisitorRouter.visit(this, union.getLeft()), ReturningVisitorRouter.visit(this, union.getRight()));
    }

    @Override
    public Op visit(OpJoin join) {
        return OpJoin.create(ReturningVisitorRouter.visit(this, join.getLeft()), ReturningVisitorRouter.visit(this, join.getRight()));
    }

    @Override
    public Op visit(OpDistinct distinct) {
        return OpCloningUtil.clone(distinct, ReturningVisitorRouter.visit(this, distinct.getSubOp()));
    }

    @Override
    public Op visit(OpSlice slice) {
        return OpCloningUtil.clone(slice, ReturningVisitorRouter.visit(this, slice.getSubOp()));
    }

    @Override
    public Op visit(OpOrder orderBy) {
        return OpCloningUtil.clone(orderBy, ReturningVisitorRouter.visit(this, orderBy.getSubOp()));
    }

    @Override
    public Op visit(OpProject project) {
        return OpCloningUtil.clone(project, ReturningVisitorRouter.visit(this, project.getSubOp()));
    }

    @Override
    public Op visit(OpGroup groupBy) {
        return OpCloningUtil.clone(groupBy, ReturningVisitorRouter.visit(this, groupBy.getSubOp()));
    }

    @Override
    public Op visit(OpExtend extend) {
        return OpCloningUtil.clone(extend, ReturningVisitorRouter.visit(this, extend.getSubOp()));
    }

    @Override
    public Op visit(OpMinus minus) {
        return OpCloningUtil.clone(minus,
                ReturningVisitorRouter.visit(this, minus.getLeft()),
                ReturningVisitorRouter.visit(this, minus.getRight()));
    }

    @Override
    public Op visit(OpPath path) {
        return OpCloningUtil.clone(path, ReturningVisitorRouter.visit(this, path.getTriplePath().getPath()));
    }

    @Override
    public Path visit(P_Inverse inverse) {
        return OpCloningUtil.clone(inverse, ReturningVisitorRouter.visit(this, inverse.getSubPath()));
    }

    @Override
    public Path visit(P_Seq seq) {
        return OpCloningUtil.clone(seq, ReturningVisitorRouter.visit(this, seq.getLeft()), ReturningVisitorRouter.visit(this, seq.getRight()));
    }

    @Override
    public Path visit(P_Alt alt) {
        return OpCloningUtil.clone(alt, ReturningVisitorRouter.visit(this, alt.getLeft()), ReturningVisitorRouter.visit(this, alt.getRight()));
    }

    @Override
    public Path visit(P_OneOrMore1 oneOrMore) {
        return OpCloningUtil.clone(oneOrMore, ReturningVisitorRouter.visit(this, oneOrMore.getSubPath()));
    }

    @Override
    public Path visit(P_ZeroOrMore1 zeroOrMore) {
        return OpCloningUtil.clone(zeroOrMore, ReturningVisitorRouter.visit(this, zeroOrMore.getSubPath()));
    }

    @Override
    public Path visit(P_Link link) { return link; }

    /**
     * Visit all children and apply the visitor.
     * @param children The children to visit.
     * @return List of `Op` resulting from the visit.
     */
    public List<Op> visit(List<Op> children) {
        return children.stream().map(c -> ReturningVisitorRouter.visit(this, c)).toList();
    }
}
