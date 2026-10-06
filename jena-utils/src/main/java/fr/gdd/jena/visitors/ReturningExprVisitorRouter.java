package fr.gdd.jena.visitors;

import org.apache.jena.sparql.expr.*;

/**
 * A router for the visitor on expressions.
 */
public class ReturningExprVisitorRouter {

    public static <T> T visit(ReturningExprVisitor<T> t, Expr expr) {
        return switch (expr) {
            case ExprFunction0 ef -> t.visit(ef);
            case ExprFunction1 ef -> t.visit(ef);
            case ExprFunction2 ef -> t.visit(ef);
            case ExprFunction3 ef -> t.visit(ef);
            case ExprFunctionN ef -> t.visit(ef);
            case ExprFunctionOp ef -> t.visit(ef);
            case ExprAggregator ea -> t.visit(ea);
            case ExprVar ev -> t.visit(ev);
            case ExprTripleTerm etp -> t.visit(etp);
            case NodeValue nv -> t.visit(nv);
            case ExprNone en -> t.visit(en);
            default -> throw new UnsupportedOperationException(expr.toString());
        };
    }

}
