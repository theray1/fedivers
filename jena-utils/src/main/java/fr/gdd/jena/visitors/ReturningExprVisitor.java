package fr.gdd.jena.visitors;

import org.apache.jena.sparql.expr.*;

/**
 * Like `Op` and `Path` but for `Expr`.
 * @param <T>
 */
public interface ReturningExprVisitor<T> {
    default T visit(Expr expr) {return ReturningExprVisitorRouter.visit(this, expr);}

    default T visit(ExprFunctionOp efo) {throw new UnsupportedOperationException("ExprFunctionOp");}
    default T visit(ExprFunction0 ef0) {throw new UnsupportedOperationException("ExprFunction0");}
    default T visit(ExprFunction1 ef1) {throw new UnsupportedOperationException("ExprFunction1");}
    default T visit(ExprFunction2 ef2) {throw new UnsupportedOperationException("ExprFunction2");}
    default T visit(ExprFunction3 ef3) {throw new UnsupportedOperationException("ExprFunction3");}
    default T visit(ExprFunctionN efn) {throw new UnsupportedOperationException("ExprFunctionN");}
    default T visit(ExprAggregator ea) {throw new UnsupportedOperationException("ExprAggregator");}
    default T visit(ExprVar ev) {throw new UnsupportedOperationException("ExprVar");}
    default T visit(NodeValue nv) {throw new UnsupportedOperationException("NodeValue");}

}
