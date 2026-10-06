package fr.gdd.jena.utils;

import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.op.*;
import org.apache.jena.sparql.core.TriplePath;
import org.apache.jena.sparql.expr.ExprAggregator;
import org.apache.jena.sparql.path.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple way to provide clones with new child(ren).
 * All in one place, i.e., some of the cloners do not use the operator to be cloned
 * at all, yet they are implemented here.
 */
public class OpCloningUtil {
    public static OpService clone(OpService service, Op subOp) {return new OpService(service.getService(), subOp, service.getSilent());}
    public static OpDistinct clone(OpDistinct distinct, Op subOp) {return new OpDistinct(subOp);}
    public static OpSlice clone(OpSlice slice, Op subOp) {return new OpSlice(subOp, slice.getStart(), slice.getLength());}
    public static OpOrder clone (OpOrder orderBy, Op subOp) {return new OpOrder(subOp, orderBy.getConditions());}
    public static OpProject clone (OpProject project, Op subOp) {return new OpProject(subOp, new ArrayList<>(project.getVars()));}
    public static OpFilter clone(OpFilter filter, Op subOp) {return OpFilter.filterDirect(filter.getExprs(), subOp);}
    public static OpGroup clone(OpGroup group, Op subOp) {return new OpGroup(subOp, group.getGroupVars(), group.getAggregators());}
    public static OpGroup clone(OpGroup group, List<ExprAggregator> aggregators, Op subOp) {return new OpGroup(subOp, group.getGroupVars(), aggregators);}
    public static OpUnion clone(OpUnion union, Op left, Op right) {return new OpUnion(left, right);}
    public static OpJoin clone(OpJoin join, Op left, Op right) {return (OpJoin) OpJoin.create(left, right);}
    public static OpMinus clone(OpMinus minus, Op left, Op right) {return (OpMinus) OpMinus.create(left, right);}
    public static OpGraph clone(OpGraph graph, Op subop) {
        return new OpGraph(graph.getNode(), subop);
    }
    public static OpExtend clone(OpExtend extend, Op subOp) {return OpExtend.create(subOp, extend.getVarExprList());}
    public static OpConditional clone(OpConditional cond, Op left, Op right) { return new OpConditional(left, right); }
    public static OpLeftJoin clone(OpLeftJoin lj, Op left, Op right) { return OpLeftJoin.createLeftJoin(left, right, lj.getExprs()); }

    public static OpSequence clone(OpSequence sequence, List<Op> subops) {
        return (OpSequence) OpSequence.create().copy(subops);
    }

    public static OpPath clone(OpPath path, Path subop) {
        return new OpPath(new TriplePath(path.getTriplePath().getSubject(), subop, path.getTriplePath().getObject()));
    }

    public static P_Inverse clone(P_Inverse link, Path subPath) { return new P_Inverse(subPath); }
    public static P_Seq clone(P_Seq seq, Path left, Path right) { return new P_Seq(left, right); }
    public static P_Alt clone(P_Alt seq, Path left, Path right) { return new P_Alt(left, right); }
    public static P_OneOrMore1 clone(P_OneOrMore1 oneOrMore, Path subPath) { return new P_OneOrMore1(subPath); }
    public static P_ZeroOrMore1 clone(P_ZeroOrMore1 zeroOrMore, Path subPath) { return new P_ZeroOrMore1(subPath); }

    public static Op2 clone(Op2 op2, Op left, Op right) {
        return switch (op2) {
            case OpJoin join -> clone(join, left, right);
            case OpLeftJoin lj -> clone(lj, left, right);
            case OpUnion union -> clone(union, left, right);
            default -> throw new UnsupportedOperationException("Clone for " + op2);
        };
    }

    public static Op1 clone(Op1 op1, Op newInner) {
        return switch (op1) {
            case OpProject project -> clone(project, newInner);
            case OpFilter filter -> clone(filter, newInner);
            case OpExtend extend -> clone(extend, newInner);
            case OpDistinct distinct -> clone(distinct, newInner);
            case OpGroup group -> clone(group, newInner);
            case OpService slice -> clone(slice, newInner);
            case OpOrder order -> clone(order, newInner);
            default -> throw new UnsupportedOperationException("Clone for " + op1);
        };
    }
}
