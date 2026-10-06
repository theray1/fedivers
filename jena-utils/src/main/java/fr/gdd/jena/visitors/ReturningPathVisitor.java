package fr.gdd.jena.visitors;

import org.apache.jena.sparql.path.*;

public interface ReturningPathVisitor<T> {
    default T visit(Path path) { return ReturningVisitorRouter.visit(this, path); }

    default T visit(P_ReverseLink reverse) {throw new UnsupportedOperationException("P_ReverseLink");}
    default T visit(P_Inverse inverse) {throw new UnsupportedOperationException("P_Inverse");}
    default T visit(P_Seq seq) {throw new UnsupportedOperationException("P_Seq");}
    default T visit(P_Link link) {throw new UnsupportedOperationException("P_Link");}
    default T visit(P_Alt alt) {throw new UnsupportedOperationException("P_Alt");}
    default T visit(P_OneOrMore1 oneOrMore) {throw new UnsupportedOperationException("P_OneOrMore1");}
    default T visit(P_ZeroOrMore1 zeroOrMore) {throw new UnsupportedOperationException("P_ZeroOrMore1");}
}
