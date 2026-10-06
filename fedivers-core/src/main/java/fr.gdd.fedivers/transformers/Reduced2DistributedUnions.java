package fr.gdd.fedivers.transformers;

import fr.gdd.jena.utils.FlattenUnflatten;
import fr.gdd.jena.utils.OpCloningUtil;
import fr.gdd.jena.visitors.ReturningOpBaseVisitor;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.op.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Class that distribute operators over the unions.
 * For instance Join(Union(tp1,tp2), tp3) becomes Union(Join(tp1, tp3), Join(tp2, tp3)).
 */
public class Reduced2DistributedUnions extends ReturningOpBaseVisitor {

    @Override
    public Op visit(OpUnion union) {
        return OpUnion.create(visit(union.getLeft()), visit(union.getRight()));
    }

    @Override
    public Op visit(OpJoin join) {
        final List<Op> left = FlattenUnflatten.flattenUnion(visit(join.getLeft()));
        final List<Op> right = FlattenUnflatten.flattenUnion(visit(join.getRight()));
        final List<Op> distributed = new ArrayList<>();
        left.forEach(l -> right.forEach(r -> distributed.add(OpJoin.create(l, r))));
        return FlattenUnflatten.unflattenUnion(distributed);
    }

    @Override
    public Op visit(OpSequence sequence) {
        final List<Op> inners = new ArrayList<>();
        sequence.getElements().forEach(this::visit);
        final Op asJoin = FlattenUnflatten.unflattenJoin(inners, false);
        return visit(asJoin);
    }

    @Override
    public Op visit(OpLeftJoin lj) {
        final Op right = visit(lj.getRight());
        final List<Op> left = FlattenUnflatten.flattenUnion(visit(lj.getLeft()));
        final List<Op> distributed = new ArrayList<>();
        left.forEach(l -> {
            distributed.add(OpCloningUtil.clone(lj, l, right));
        });
        return FlattenUnflatten.unflattenUnion(distributed);
    }

    // Filter(E, Union(tp1, tp2)) = Union(Filter(E, tp1), Filter(E, tp2))
    @Override public Op visit(OpFilter filter) { return pushUpUnion(filter); }
    @Override public Op visit(OpProject project) { return pushUpUnion(project); }
    @Override public Op visit(OpExtend extend) { return pushUpUnion(extend); }

    @Override public Op visit(OpOrder orderBy) { return OpCloningUtil.clone(orderBy, visit(orderBy.getSubOp())); }
    @Override public Op visit(OpSlice slice) { return OpCloningUtil.clone(slice, visit(slice.getSubOp())); }



    public Op pushUpUnion(Op1 op1) {
        final List<Op> inner = FlattenUnflatten.flattenUnion(visit(op1.getSubOp()));
        final List<Op> distributed = new ArrayList<>();
        inner.forEach(i -> {
            distributed.add(OpCloningUtil.clone(op1, i));
        });
        return FlattenUnflatten.unflattenUnion(distributed);
    }
}
