package fr.gdd.fedivers.sources;

import fr.gdd.fedivers.FediversContext;
import fr.gdd.fedivers.transformers.SkipGlobalQueryModifiers;
import fr.gdd.jena.visitors.ReturningOpBaseVisitor;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.op.*;
import org.apache.jena.sparql.core.Var;

import java.util.List;

/**
 * Associate each service with its source finder. When materialized, it takes
 * the form a VALUES, otherwise, takes the form of DISTINCT FILTER EXISTS:
 *   SERVICE ?g1 WHERE { ?s <p> ?o . ?o <p2> ?x }
 * becomes:
 *   SELECT DISTINCT ?g1 WHERE { ?s <p> o }
 *   FILTER EXISTS { SELECT ?g1 WHERE { ?s <p> ?o . ?o <p2> ?x }}
 *   SERVICE ?g1 WHERE { ?s <p> ?o . ?o <p2> ?x }
 */
public class ToGeneratorOfJoinOverUnions extends ReturningOpBaseVisitor {

    final FediversContext context;

    public ToGeneratorOfJoinOverUnions(FediversContext context) {
        this.context = context;
    }

    public Op create(Op op) {
        final SkipGlobalQueryModifiers skipper = new SkipGlobalQueryModifiers();
        final Op skippedServices = skipper.visit(op);
        final Op transformed = this.visit(skippedServices);
        return skipper.reApply(transformed);
    }

    @Override
    public Op visit(OpService req) {
        Var g = (Var) req.getService();
        final Op forGraph2SourceDataset = new RemoveLeftJoins().visit(context.query2summary.visit(req.getSubOp()));
        Op hat = new ToDistinctByProduceTPQPThenFilterExists(context).visit(
                new OpDistinct(new OpProject(
                        new OpGraph(g, forGraph2SourceDataset),
                        List.of(g))));
        if (context.shouldMaterializeSources) {
            return OpJoin.create(ToMaterializedSources.materialized(context, hat, req), req);
        }
        return new ToModifiedSourceNames(context.query2summary.getGraph2Source()).transform(hat, req);
    }

    public static class RemoveLeftJoins extends ReturningOpBaseVisitor {
        @Override public Op visit(OpConditional cond) { return visit(cond.getLeft()); }
        @Override public Op visit(OpLeftJoin lj) { return visit(lj.getLeft()); }
    }

}
