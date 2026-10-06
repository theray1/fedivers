package fr.gdd.fedivers.sources;

import fr.gdd.jena.visitors.ReturningOpBaseVisitor;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.op.OpExtend;
import org.apache.jena.sparql.algebra.op.OpJoin;
import org.apache.jena.sparql.algebra.op.OpService;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.expr.Expr;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * It is rare that the summary that produces the graphs is really
 * up-to-date with endpoints. In particular, the endpoint names can
 * change over time, so instead of reingesting everytime, we apply
 * a transformation in the names.
 * *
 * For every `SERVICE ?g { … }`, a prefix is added to modify the endpoint
 * in the form of a `BIND (regex on ?g … AS ?g_B) . SERVICE ?g_B { … }`.
 * *
 * TODO adapt this to the new way. I.e, get the variables of services,
 *      then locate the producer of the variable to wrap it.
 */
public class ToModifiedSourceNames extends ReturningOpBaseVisitor {

    final List<Var> sourcesToModify = new ArrayList<>();
    final Function<Var, Expr> graph2source;

    public ToModifiedSourceNames(Function<Var, Expr> graph2source) { this.graph2source = graph2source; }

    public Op transform(Op sources, Op services) {
        // retrieves the list of variables to modify and modify the variables
        final Op transformedServices = this.visit(services);
        final Op transformedSources = sourcesToModify.stream().reduce(
                sources,
                (base, v) -> {
                    final Var newName = Var.alloc(v.getVarName() + "_bis");
                    return OpExtend.create(base, newName, graph2source.apply(v));
                },
                (_, _) -> { throw new UnsupportedOperationException(); }
        );
        return OpJoin.create(transformedSources, transformedServices);
    }

    // public ToModifiedSourceNames() { this("$", "/"); }
    // public ToModifiedSourceNames(String from, String to) {
//        this.transformFrom = from;
//        this.transformTo = to;
//    }

    @Override
    public Op visit(OpService req) {
        if (!req.getService().isVariable()) return req; // do nothing when the uri is hard set.
        final Var sourceName = (Var) req.getService();
        sourcesToModify.add(sourceName);
        final Var newName = Var.alloc(sourceName.getVarName()+"_bis");
        return new OpService(newName, req.getSubOp(), req.getSilent());
    }
}
