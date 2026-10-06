package fr.gdd.jena.visitors;

import org.apache.jena.sparql.expr.*;

/**
 * Copy the expression.
 */
public class ReturningExprBaseVisitor implements ReturningExprVisitor<Expr> {

    public ExprList visit(ExprList exprs)  {
        ExprList result = new ExprList();
        exprs.getList().forEach(e -> result.add(this.visit(e)));
        return result;
    }

    @Override public Expr visit(ExprAggregator ea) { return ea.deepCopy(); }
    @Override public Expr visit(NodeValue nv) { return nv.deepCopy(); }
    @Override public Expr visit(ExprFunction0 ef0) { return ef0.deepCopy(); }
    @Override public Expr visit(ExprFunction1 ef1) { return ef1.copy(this.visit(ef1.getArg())); }
    @Override public Expr visit(ExprFunction2 ef2) { return ef2.copy(this.visit(ef2.getArg1()), this.visit(ef2.getArg2())); }
    @Override public Expr visit(ExprFunctionOp efo) { return efo.deepCopy(); }
    @Override public Expr visit(ExprVar ev) { return ev.deepCopy(); }

    @Override
    public Expr visit(ExprFunction3 ef3) {
        return ef3.copy(this.visit(ef3.getArg1()), this.visit(ef3.getArg2()), this.visit(ef3.getArg3()));
    }

    @Override
    public Expr visit(ExprFunctionN efn) {
        ExprList el = new ExprList();
        efn.getArgs().forEach(a ->  el.add(this.visit(a)));
        return efn.copy(el);
    }

}
