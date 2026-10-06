package fr.gdd.jena.visitors;

import org.apache.jena.sparql.path.*;

/**
 * Property-paths-related interface for visitors. Don't forget to use the corresponding
 * router when needed.
 * @param <R>  The type of the object returned.
 * @param <A> The type of the argument to be passed.
 */
public interface ReturningArgsPathVisitor<R,A> {
    default R visit(Path path, A args) { return ReturningArgsVisitorRouter.visit(this, path, args); }

    default R visit(P_ReverseLink reverse, A args) {throw new UnsupportedOperationException("P_ReverseLink");}
    default R visit(P_Link link, A args) {throw new UnsupportedOperationException("P_Link");}
    default R visit(P_Seq seq, A args) {throw new UnsupportedOperationException("P_Seq");}
    default R visit(P_Inverse inverse, A args) {throw new UnsupportedOperationException("P_Inverse");}
    default R visit(P_Alt alt, A args) {throw new UnsupportedOperationException("P_Alt");}
    default R visit(P_OneOrMore1 oneOrMore, A args) {throw new UnsupportedOperationException("P_OneOrMore");}
    default R visit(P_ZeroOrMore1 zeroOrMore, A args) {throw new UnsupportedOperationException("P_ZeroOrMore");}
}
