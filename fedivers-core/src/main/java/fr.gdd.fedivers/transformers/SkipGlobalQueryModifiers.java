package fr.gdd.fedivers.transformers;

import fr.gdd.jena.utils.OpCloningUtil;
import fr.gdd.jena.visitors.ReturningOpVisitor;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.op.*;

import java.util.ArrayList;
import java.util.List;


public class SkipGlobalQueryModifiers implements ReturningOpVisitor<Op> {

    final List<Op> modifiers = new ArrayList<>();

    /**
     * @param wrapped The query to wrap into the query modifiers
     * @return The wrapped Op with the query modifiers of the query.
     */
    public Op reApply(Op wrapped) {
        if (modifiers.isEmpty()) return wrapped; // nothing to do
        Op transformed = wrapped;
        for  (int i = modifiers.size() - 1; i >= 0 ; --i) {
            transformed = switch (modifiers.get(i)) {
                case OpDistinct distinct -> OpCloningUtil.clone(distinct, transformed);
                case OpSlice slice -> OpCloningUtil.clone(slice, transformed);
                case OpOrder order -> OpCloningUtil.clone(order, transformed);
                case OpProject project -> OpCloningUtil.clone(project, transformed);
                case OpGroup group -> OpCloningUtil.clone(group, transformed);
                default -> throw new UnsupportedOperationException("Query modifier unknown: " + modifiers.get(i));
            };
        }
        return transformed;
    }

    @Override public Op visit(OpDistinct distinct) { modifiers.add(distinct) ; return this.visit(distinct.getSubOp()); }
    @Override public Op visit(OpSlice slice) { modifiers.add(slice); return this.visit(slice.getSubOp()); }
    @Override public Op visit(OpOrder orderBy) { modifiers.add(orderBy); return this.visit(orderBy.getSubOp()); }
    @Override public Op visit(OpProject project) { modifiers.add(project); return this.visit(project.getSubOp());}
    @Override public Op visit(OpGroup groupBy) { modifiers.add(groupBy); return this.visit(groupBy.getSubOp());}

    @Override public Op visit(OpService req) { return req; }
    @Override public Op visit(OpTriple triple) { return triple; }
    @Override public Op visit(OpQuad quad) { return quad; }
    @Override public Op visit(OpGraph graph) { return graph; }
    @Override public Op visit(OpQuadBlock block) { return block; }
    @Override public Op visit(OpQuadPattern quads) { return quads; }
    @Override public Op visit(OpBGP bgp) { return bgp; }
    @Override public Op visit(OpSequence sequence) { return sequence; }
    @Override public Op visit(OpTable table) { return table; }
    @Override public Op visit(OpLeftJoin lj) { return lj; }
    @Override public Op visit(OpConditional cond) { return cond; }
    @Override public Op visit(OpFilter filter) { return filter; }
    @Override public Op visit(OpUnion union) { return union; }
    @Override public Op visit(OpJoin join) { return join; }
    @Override public Op visit(OpMinus minus) { return minus; }
    @Override public Op visit(OpExtend extend) { return extend; }
    @Override public Op visit(OpPath path) { return path; }
}
