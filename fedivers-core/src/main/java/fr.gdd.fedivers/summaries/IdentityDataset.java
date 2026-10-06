package fr.gdd.fedivers.summaries;

import fr.gdd.jena.visitors.ReturningOpIdentity;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.expr.Expr;
import org.apache.jena.sparql.util.ExprUtils;

import java.util.function.Function;

public class IdentityDataset extends ReturningOpIdentity implements IGraph2SourceDataset {

    Function<Var, Expr> graph2source;

    public IdentityDataset() {
        setGraph2Source("(.+)", "$1");
    }

    public IdentityDataset setGraph2Source(String from, String to) {
        graph2source = (v) -> ExprUtils.parse(String.format("URI(REPLACE(STR(%s), \"%s\", \"%s\"))", v, from, to));
        return this;
    }

    @Override
    public Function<Var, Expr> getGraph2Source() {
        return graph2source;
    }
}
