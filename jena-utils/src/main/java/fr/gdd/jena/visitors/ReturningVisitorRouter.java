package fr.gdd.jena.visitors;

import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.op.*;
import org.apache.jena.sparql.path.*;

/**
 * Route the visitor to the proper one depending on the type of the `Op`/`Path`
 * since it's not implemented in each `Op`/`Path` itself. This probably lose some
 * performance, but it's not meant to be used intensively.
 */
public class ReturningVisitorRouter {
    public static <T> T visit(ReturningOpVisitor<T> t, Op op) {
        return switch (op) {
            case OpService o -> t.visit(o);

            case OpTriple o -> t.visit(o);
            case OpQuad o -> t.visit(o);
            case OpGraph o -> t.visit(o);
            case OpQuadBlock o -> t.visit(o);
            case OpQuadPattern o -> t.visit(o);
            case OpBGP o -> t.visit(o);
            case OpSequence o -> t.visit(o);
            case OpTable o -> t.visit(o);
            case OpLeftJoin o -> t.visit(o);
            case OpConditional o -> t.visit(o);
            case OpFilter o -> t.visit(o);
            case OpDistinct o -> t.visit(o);
            case OpUnion o -> t.visit(o);
            case OpJoin o -> t.visit(o);
            case OpSlice o -> t.visit(o);
            case OpOrder o -> t.visit(o);
            case OpProject o -> t.visit(o);
            case OpGroup o -> t.visit(o);
            case OpMinus o -> t.visit(o);
            case OpExtend o -> t.visit(o);
            case OpPath o -> t.visit(o);
            default -> throw new UnsupportedOperationException(op.toString());
        };
    }

    public static <T> T visit(ReturningPathVisitor<T> t, Path path) {
        return switch (path) {
            case P_ReverseLink p -> t.visit(p);
            case P_Inverse p -> t.visit(p);
            case P_Seq p -> t.visit(p);
            case P_Link p -> t.visit(p);
            case P_Alt p -> t.visit(p);
            case P_OneOrMore1 p -> t.visit(p);
            case P_ZeroOrMore1 p -> t.visit(p);
            default -> throw new UnsupportedOperationException(path.toString());
        };
    }

}
