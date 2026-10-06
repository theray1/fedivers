package fr.gdd.jena.visitors;

import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.op.*;

/**
 * Returns itself instantly, not even copying. Cannot override `visit(Op op)` only
 * since if this `op` is cast, then it goes directly to the corresponding
 * specialized `visit`.
 */
public class ReturningOpIdentity implements ReturningOpVisitor<Op> {
    public Op visit(Op op) { return op; }

    public Op visit(OpService req) { return req; }
    public Op visit(OpTriple triple) { return triple; }
    public Op visit(OpQuad quad) { return quad; }
    public Op visit(OpGraph graph) { return graph; }
    public Op visit(OpQuadBlock block) { return block; }
    public Op visit(OpQuadPattern quads) { return quads; }
    public Op visit(OpBGP bgp) { return bgp; }
    public Op visit(OpSequence sequence) { return sequence; }
    public Op visit(OpTable table) { return table; }
    public Op visit(OpLeftJoin lj) { return lj; }
    public Op visit(OpConditional cond) { return cond; }
    public Op visit(OpFilter filter) { return filter; }
    public Op visit(OpUnion union) { return union; }
    public Op visit(OpJoin join) { return join; }
    public Op visit(OpDistinct distinct) { return distinct; }
    public Op visit(OpSlice slice) { return slice; }
    public Op visit(OpOrder orderBy) { return orderBy; }
    public Op visit(OpProject project) { return project; }
    public Op visit(OpGroup groupBy) { return groupBy; }
    public Op visit(OpMinus minus) { return minus; }
    public Op visit(OpExtend extend) { return extend; }
    public Op visit(OpPath path) { return path; }
}
