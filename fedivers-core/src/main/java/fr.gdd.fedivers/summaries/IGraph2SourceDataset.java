package fr.gdd.fedivers.summaries;

import fr.gdd.jena.visitors.ReturningOpVisitor;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.expr.Expr;

import java.util.function.Function;

public interface IGraph2SourceDataset extends ReturningOpVisitor<Op> {

    /**
     * @return The function that returns an expression transforming a graph
     *         into an actual source.
     *         When null, no transformation should be performed: the graph
     *         is the endpoint.
     */
    default Function<Var, Expr> getGraph2Source() { return null; };

}
