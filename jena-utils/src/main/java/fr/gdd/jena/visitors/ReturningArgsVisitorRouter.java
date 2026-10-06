package fr.gdd.jena.visitors;

import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.op.*;
import org.apache.jena.sparql.path.*;

/**
 * Route the visitor to the proper one depending on the type of the `Op`
 * since it's not implemented in each `Op` itself. This probably lose some
 * performance, but it's not meant to be used intensively. This is the version
 * that includes a typed argument.
 */
public class ReturningArgsVisitorRouter {
    public static <R,A> R visit(ReturningArgsOpVisitor<R,A> t, Op op, A args) {
        return switch (op) {
            case OpService o -> t.visit(o, args);
            case OpTriple o -> t.visit(o, args);
            case OpQuad o -> t.visit(o, args);
            case OpGraph o -> t.visit(o, args);
            case OpQuadBlock o -> t.visit(o, args);
            case OpBGP o -> t.visit(o, args);
            case OpSequence o -> t.visit(o, args);
            case OpTable o -> t.visit(o, args);
            case OpLeftJoin o -> t.visit(o, args);
            case OpConditional o -> t.visit(o, args);
            case OpFilter o -> t.visit(o, args);
            case OpDistinct o -> t.visit(o, args);
            case OpUnion o -> t.visit(o, args);
            case OpJoin o -> t.visit(o, args);
            case OpSlice o -> t.visit(o, args);
            case OpOrder o -> t.visit(o, args);
            case OpProject o -> t.visit(o, args);
            case OpGroup o -> t.visit(o, args);
            case OpMinus o -> t.visit(o, args);
            case OpExtend o -> t.visit(o, args);
            case OpPath o -> t.visit(o, args);
            default -> throw new UnsupportedOperationException(op + "\nWith args: " + args.toString());
        };
    }

    public static <R,A> R visit(ReturningArgsPathVisitor<R,A> t, Path path, A args) {
        return switch (path) {
            case P_ReverseLink p -> t.visit(p, args);
            case P_Link p -> t.visit(p, args);
            case P_Inverse p -> t.visit(p, args);
            case P_Seq p -> t.visit(p, args);
            case P_Alt p -> t.visit(p, args);
            case P_OneOrMore1 p -> t.visit(p, args);
            case P_ZeroOrMore1 p -> t.visit(p, args);
            default -> throw new UnsupportedOperationException(path.toString());
        };
    }
}
