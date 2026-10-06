package fr.gdd.fedivers.optimizers;

import fr.gdd.jena.visitors.ReturningOpVisitor;
import org.apache.jena.sparql.algebra.op.*;

/**
 * Checks if there exists at least one service in the query to visit.
 */
public class IsWithNestedService implements ReturningOpVisitor<Boolean> {
    @Override public Boolean visit(OpService req) { return true; }
    @Override public Boolean visit(OpTriple triple) { return false; }
    @Override public Boolean visit(OpQuad quad) { return false; }
    @Override public Boolean visit(OpPath path) { return false; }
    @Override public Boolean visit(OpBGP bgp) { return false; }
    @Override public Boolean visit(OpQuadPattern quads) { return false; }
    @Override public Boolean visit(OpTable table) { return false; }
    @Override public Boolean visit(OpQuadBlock block) { return false; }

    @Override public Boolean visit(OpExtend extend) { return this.visit(extend.getSubOp()); }
    @Override public Boolean visit(OpGroup groupBy) { return this.visit(groupBy.getSubOp()); }
    @Override public Boolean visit(OpOrder orderBy) { return this.visit(orderBy.getSubOp()); }
    @Override public Boolean visit(OpSlice slice) {return this.visit(slice.getSubOp()); }
    @Override public Boolean visit(OpFilter filter) { return this.visit(filter.getSubOp()); }
    @Override public Boolean visit(OpGraph graph) { return this.visit(graph.getSubOp()); }
    @Override public Boolean visit(OpDistinct distinct) { return this.visit(distinct.getSubOp()); }
    @Override public Boolean visit(OpProject project) { return this.visit(project.getSubOp()); }

    @Override public Boolean visit(OpJoin join) { return this.visit(join.getLeft()) || this.visit(join.getRight()); }
    @Override public Boolean visit(OpLeftJoin lj) { return this.visit(lj.getLeft()) || this.visit(lj.getRight()); }
    @Override public Boolean visit(OpMinus minus) { return this.visit(minus.getLeft()) || this.visit(minus.getRight()); }
    @Override public Boolean visit(OpConditional cond) { return this.visit(cond.getLeft()) || this.visit(cond.getRight()); }
    @Override public Boolean visit(OpUnion union) { return this.visit(union.getLeft()) || this.visit(union.getRight()); }

    @Override public Boolean visit(OpSequence sequence) { return sequence.getElements().stream().anyMatch(this::visit); }
}