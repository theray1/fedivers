package fr.gdd.fedivers.transformers;

import fr.gdd.jena.utils.OpCloningUtil;
import fr.gdd.jena.visitors.ReturningOpBaseVisitor;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.op.*;

import java.util.List;

/**
 * When there is an `OpTable.empty()` (i.e. VALUES () {} ), push it up as much
 * as possible.
 */
public class SimplifyEmpty extends ReturningOpBaseVisitor {

    @Override
    public Op visit(OpSequence sequence) {
        List<Op> children = sequence.getElements().stream().map(this::visit).filter(o -> !o.equals(OpTable.unit())).toList();
        if (children.stream().anyMatch(o -> o.equalTo(OpTable.empty(), null))) {
            return OpTable.empty();
        }
        if (children.isEmpty()) {
            return OpTable.empty();
        }
        if (children.size() == 1) {
            return children.getFirst();
        }
        OpSequence newSequence = OpSequence.create();
        children.forEach(newSequence::add);
        return newSequence;
    }

    @Override
    public Op visit(OpJoin join) {
        final Op left = visit(join.getLeft());
        final Op right = visit(join.getRight());
        if (left.equalTo(OpTable.empty(), null) || right.equalTo(OpTable.empty(), null)) {
            return OpTable.empty();
        }
        return OpJoin.create(left, right);
    }

    @Override
    public Op visit(OpUnion union) {
        final Op left = visit(union.getLeft());
        final Op right = visit(union.getRight());
        if (left.equalTo(OpTable.empty(), null) && right.equalTo(OpTable.empty(), null)) {
            return OpTable.empty();
        }
        if (left.equalTo(OpTable.empty(), null)) { return right; }
        if (right.equalTo(OpTable.empty(), null)) { return left; }
        return OpUnion.create(left, right);
    }

    @Override
    public Op visit(OpFilter filter) {
        final Op inner = visit(filter.getSubOp());
        if (inner.equalTo(OpTable.empty(), null)) return OpTable.empty();
        return OpCloningUtil.clone(filter, inner);
    }

    @Override
    public Op visit(OpProject project) {
        final Op inner = visit(project.getSubOp());
        if (inner.equalTo(OpTable.empty(), null)) return OpTable.empty();
        return OpCloningUtil.clone(project, inner);
    }

    @Override
    public Op visit(OpSlice slice) {
        final Op inner = visit(slice.getSubOp());
        if (inner.equalTo(OpTable.empty(), null)) return OpTable.empty();
        return OpCloningUtil.clone(slice, inner);
    }

    @Override
    public Op visit(OpDistinct distinct) {
        final Op inner = visit(distinct.getSubOp());
        if (inner.equalTo(OpTable.empty(), null)) return OpTable.empty();
        return OpCloningUtil.clone(distinct, inner);
    }

    @Override
    public Op visit(OpService req) {
        final Op inner = visit(req.getSubOp());
        if (inner.equalTo(OpTable.empty(), null)) return OpTable.empty();
        return OpCloningUtil.clone(req, inner);
    }

    @Override
    public Op visit(OpExtend extend) {
        final Op inner = visit(extend.getSubOp());
        if (inner.equalTo(OpTable.empty(), null)) return OpTable.empty();
        return OpCloningUtil.clone(extend, inner);
    }

    @Override
    public Op visit(OpLeftJoin lj) {
        final Op left = visit(lj.getLeft());
        final Op right = visit(lj.getRight());
        if (left.equalTo(OpTable.empty(), null) && right.equalTo(OpTable.empty(), null)) {
            return OpTable.empty();
        }
        if (left.equalTo(OpTable.empty(), null)) { return OpTable.empty(); }
        if (right.equalTo(OpTable.empty(), null)) { return left; }
        return OpCloningUtil.clone(lj, left, right);
    }

    @Override
    public Op visit(OpConditional cond) {
        final Op left = visit(cond.getLeft());
        final Op right = visit(cond.getRight());
        if (left.equalTo(OpTable.empty(), null) && right.equalTo(OpTable.empty(), null)) {
            return OpTable.empty();
        }
        if (left.equalTo(OpTable.empty(), null)) { return OpTable.empty(); }
        if (right.equalTo(OpTable.empty(), null)) { return left; }
        return OpCloningUtil.clone(cond, left, right);
    }

    @Override
    public Op visit(OpMinus minus) {
        final Op left = visit(minus.getLeft());
        final Op right = visit(minus.getRight());
        if (left.equalTo(OpTable.empty(), null) && right.equalTo(OpTable.empty(), null)) {
            return OpTable.empty();
        }
        if (left.equalTo(OpTable.empty(), null)) { return OpTable.empty(); }
        if (right.equalTo(OpTable.empty(), null)) { return left; }
        return OpCloningUtil.clone(minus, left, right);
    }

}
