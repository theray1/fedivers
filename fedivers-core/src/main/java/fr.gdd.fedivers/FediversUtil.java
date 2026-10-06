package fr.gdd.fedivers;

import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.Table;
import org.apache.jena.sparql.algebra.TableFactory;
import org.apache.jena.sparql.algebra.Transformer;
import org.apache.jena.sparql.algebra.op.*;
import org.apache.jena.sparql.algebra.table.TableN;
import org.apache.jena.sparql.engine.binding.Binding;
import org.apache.jena.sparql.expr.E_LogicalNot;
import org.apache.jena.sparql.expr.E_LogicalOr;
import org.apache.jena.sparql.expr.Expr;
import org.apache.jena.sparql.expr.ExprList;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BinaryOperator;
import java.util.stream.Stream;

public class FediversUtil {
    public static final Op DONE = OpTable.empty(); // actually produces an empty `VALUES` clause
    public static boolean isDone(Op op) { return op instanceof OpTable table && table.getTable().isEmpty(); }


    public static final BinaryOperator<Op> removeEmptyOfUnion = (l, r) -> {
        if (isDone(l) && isDone(r)) return DONE;
        if (isDone(l)) return r;
        if (isDone(r)) return l;
        return OpUnion.create(l, r);
    };

    /**
     * Utility function that simplify the union when possible.
     */
    public static Op union(Op left, Op right) {
        return removeEmptyOfUnion.apply(left,right);
    }

    /**
     * Utility function that simplify the join when possible.
     */
    public static Op join(Op left, Op right) {
        if (isDone(left) || isDone(right)) return DONE;
        return OpJoin.create(left, right);
    }

    public static Op left_join(Op left, Op right) {
        if (isDone(right) && isDone(left)) return DONE;
        if (isDone(right) && isDone(left)) return left;
        if (isDone(left) && !isDone(right)) return DONE;
        return OpLeftJoin.create(left, right, ExprList.emptyList);
    }

    public static Op slice(Op subOp, long offset, long limit) {
        if (isDone(subOp)) return DONE;
        if (limit <= 0) return DONE;

        switch (subOp) {
            case OpSlice subSlice -> {
                // recursively simplify if another slice below
                subOp = slice(subSlice.getSubOp(), subSlice.getStart(), subSlice.getLength());
                // what happens to offset:
                // s1:  |---o----l-->
                // s2:  |-o-l------->
                limit = Math.min(subSlice.getLength(), limit);
                offset = offset + subSlice.getStart(); // TODO manage overflows

                if (isDone(subOp)) return DONE;
                if (subOp instanceof OpSlice stillSlice) {
                    subOp = stillSlice.getSubOp(); // unwraps
                }
                if (limit <= 0) return DONE;
            }
            case OpTable subTable -> {
                List<Binding> bindingsOfTable = new ArrayList<>();
                subTable.getTable().rows().forEachRemaining(bindingsOfTable::add);
                int lowerBound = Math.min(bindingsOfTable.size(), (int) offset);
                int upperBound = Math.min((int)(offset + limit), bindingsOfTable.size());
                List<Binding> slicedBindings = new ArrayList<>(bindingsOfTable.subList(lowerBound, upperBound));
                Table slicedTable = TableFactory.create(subTable.getTable().getVars());
                slicedBindings.forEach(slicedTable::addBinding);
                if (slicedTable.isEmpty()) return DONE; // we remove projected variables as well
                return OpTable.create(slicedTable);
            }
            default -> {}
        }

        if (offset == 0) { offset = Long.MIN_VALUE; }

        return new OpSlice(subOp, offset, limit);
    }

    public static Op filter(ExprList exprs, Op subOp) {
        if (isDone(subOp)) return DONE;
        // TODO check if the condition is always true or always false
        return OpFilter.filterDirect(exprs, subOp);
    }

    public static Op filter(OpFilter toReplace, Op newSubOp) { return filter(toReplace.getExprs(), newSubOp); }

    public static Op minus(Op left, Op right) {
        if (isDone(left)) return DONE;
        if (isDone(right)) return left;
        return OpMinus.create(left, right);
    }

    public static Op asValues(Binding binding) {
        TableN bindingsAsVar = new TableN(new ArrayList<>(binding.varsMentioned()));
        bindingsAsVar.addBinding(binding);
        return OpTable.create(bindingsAsVar);
    }
}
