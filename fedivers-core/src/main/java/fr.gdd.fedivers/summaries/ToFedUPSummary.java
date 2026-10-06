package fr.gdd.fedivers.summaries;

import fr.gdd.jena.visitors.ReturningOpBaseVisitor;
import org.apache.jena.graph.*;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.op.*;
import org.apache.jena.sparql.core.Quad;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.expr.Expr;
import org.apache.jena.sparql.util.ExprUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.function.Function;

/**
 * This is a very specific class that summarizes following FedUP's strategy
 * of summarizing terms. More specifically, on a triple (s p o), for uri it
 * keeps the authority on `s` and `o`, and keep the full p. Literal objects are
 * directed to an "any" node.
 */
public class ToFedUPSummary extends ReturningOpBaseVisitor implements IGraph2SourceDataset {

    // it needs to change variable names to work
    final HashMap<Var, Var> original2modified = new HashMap<>();
    final int modulo;
    Function<Var, Expr> graph2source;

    public ToFedUPSummary() { this(0); this.setGraph2Source("(.+)", "$1"); }
    public ToFedUPSummary(int modulo) { this.modulo = modulo; setGraph2Source("(.+)", "$1"); }

    public ToFedUPSummary setGraph2Source(String from, String to) {
        graph2source = (v) -> ExprUtils.parse(String.format("URI(REPLACE(STR(%s), \"%s\", \"%s\"))", v, from, to));
        return this;
    }

    @Override
    public Function<Var, Expr> getGraph2Source() {
        return graph2source;
    }

    @Override
    public Op visit(OpTriple triple) {
        return new OpTriple(toSummaryTriple(triple.getTriple()));
    }

    @Override
    public Op visit(OpBGP bgp) {
        OpSequence sequence = OpSequence.create();
        bgp.getPattern().getList().forEach(triple -> {
            sequence.add(this.visit(new OpTriple(triple)));
        });
        return sequence;
    }

    @Override
    public Op visit(OpQuad quad) {
        return new OpQuad(new Quad(quad.getQuad().getGraph(),
                toSummaryTriple(quad.getQuad().asTriple())));
    }

    @Override
    public Op visit(OpFilter filter) { // ignored
        return visit(filter.getSubOp());
    }

    /* ******************************** UTILS ******************************** */

    public Node toSummaryTerm (Node node) {
        return switch (node) {
            case Node_URI uri -> {
                try {
                    yield processURI(new URI(uri.getURI())); // `URI` provides better methods than `Node_URI`
                } catch (URISyntaxException e) {
                    yield NodeFactory.createURI("https://donotcare.com/whatever");
                }
            }
            case Node_Blank ignored -> NodeFactory.createBlankNode("_:any");
            case Node_Literal literal -> processLiteral(literal.getLiteralLexicalForm());
            case Node_Variable v -> original2modified.computeIfAbsent(Var.alloc(v.getName()), k ->
                    Var.alloc(String.format("__%s%s", k.getVarName(), original2modified.size())));
            default -> throw new UnsupportedOperationException("Unknown kind of node: " + node);
        };
    }

    public Triple toSummaryTriple (Triple triple) {
        if (modulo < 0) return triple;
        return Triple.create(
                toSummaryTerm(triple.getSubject()),
                triple.getPredicate(), // we leave predicate untouched
                toSummaryTerm(triple.getObject()));
    }

    public Quad toSummaryQuad (Quad quad) {
        return Quad.create(quad.getGraph(), toSummaryTriple(quad.asTriple()));
    }

    protected Node processLiteral (String lexicalForm) {
        return NodeFactory.createLiteralString("any");
    }

    protected Node processURI (URI uri) {
        int hashcode = Math.abs(uri.toString().hashCode());
        if (modulo == 0 || modulo == 1) {
            return NodeFactory.createURI(uri.getScheme() + "://" + uri.getAuthority());
        } else {
            return NodeFactory.createURI(uri.getScheme() + "://" + uri.getAuthority() + "/" + (hashcode % modulo));
        }
    }

    protected Node processURI(Node source, URI uri) {
        return processURI(uri);
    }

}
