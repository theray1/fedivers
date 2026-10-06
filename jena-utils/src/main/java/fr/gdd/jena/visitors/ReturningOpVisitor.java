package fr.gdd.jena.visitors;

import org.apache.jena.query.QueryFactory;
import org.apache.jena.sparql.algebra.Algebra;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.op.*;

/**
 * The visitor must implement this interface. Added value compared
 * to the default visitor {@link org.apache.jena.sparql.algebra.OpVisitor}:
 * it returns a type. Remember to use the {@link ReturningVisitorRouter} to call
 * downstream visitors.
 * This is a class instead of an interface so it can call `super.visit` when need
 * be.
 * @param <T> The type of the object returned.
 */
public interface ReturningOpVisitor<T> {
    default T visit(String query) { return ReturningVisitorRouter.visit(this, Algebra.compile(QueryFactory.create(query))); }
    default T visit(Op op) { return ReturningVisitorRouter.visit(this, op); }

    default T visit(OpService req) {throw new UnsupportedOperationException("Req");}
    default T visit(OpTriple triple) {throw new UnsupportedOperationException("OpTriple");}
    default T visit(OpQuad quad) {throw new UnsupportedOperationException("OpQuad");}
    default T visit(OpGraph graph) {throw new UnsupportedOperationException("OpGraph");}
    default T visit(OpQuadBlock block) {throw new UnsupportedOperationException("OpQuadBlock");}
    default T visit(OpQuadPattern quads) {throw new UnsupportedOperationException("OpQuadPattern");}
    default T visit(OpBGP bgp) {throw new UnsupportedOperationException("OpBGP");}
    default T visit(OpSequence sequence) {throw new UnsupportedOperationException("OpSequence");}
    default T visit(OpTable table) {throw new UnsupportedOperationException("OpTable");}
    default T visit(OpLeftJoin lj) {throw new UnsupportedOperationException("OpLeftJoin");}
    default T visit(OpConditional cond) {throw new UnsupportedOperationException("OpConditional");}
    default T visit(OpFilter filter) {throw new UnsupportedOperationException("OpFilter");}
    default T visit(OpUnion union) {throw new UnsupportedOperationException("OpUnion");}
    default T visit(OpJoin join) {throw new UnsupportedOperationException("OpJoin");}
    default T visit(OpDistinct distinct) {throw new UnsupportedOperationException("OpDistinct");}
    default T visit(OpSlice slice) {throw new UnsupportedOperationException("OpSlice");}
    default T visit(OpOrder orderBy)  {throw new UnsupportedOperationException("OpOrder");}
    default T visit(OpProject project) {throw new UnsupportedOperationException("OpProject");}
    default T visit(OpGroup groupBy) {throw new UnsupportedOperationException("OpGroup");}
    default T visit(OpMinus minus) {throw new UnsupportedOperationException("OpMinus");}
    default T visit(OpExtend extend) {throw new UnsupportedOperationException("OpExtend");}
    default T visit(OpPath path) {throw new UnsupportedOperationException("OpPath");}
}
