package fr.gdd.fedivers.sources;

import fr.gdd.fedivers.FediversContext;
import fr.gdd.fedivers.transformers.SkipGlobalQueryModifiers;
import fr.gdd.jena.visitors.ReturningArgsOpVisitor;
import fr.gdd.jena.visitors.ReturningOpBaseVisitor;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.op.*;
import org.apache.jena.sparql.core.Var;

import java.util.List;

/**
 * Like join-over-union logical plan but adds a FILTER EXISTS
 * to check if the sources provided are joining.
 * For instance, it generates federated SPARQL queries like:
 * SELECT * WHERE {
 *     SELECT DISTINCT ?g1 WHERE { … } # we don't dive into how DISTINCT is transformed yet
 *     SERVICE ?g1 { … }
 *     SELECT DISTINCT ?g2 WHERE { … }
 *     FILTER EXISTS ( SELECT ?g1 ?g2 WHERE { … } ) # becomes result aware
 *     SERVICE ?g2 { … }
 * }
 * The materializing of DISTINCT graphs only replace them by VALUES, the
 * FILTER EXISTS is kept.
 */
@Deprecated // TODO nothing is done yet.
public class ToGeneratorOfJoinOverUnionsWithResultAware implements ReturningArgsOpVisitor<Op, Op> {

    final FediversContext context;

    public ToGeneratorOfJoinOverUnionsWithResultAware(FediversContext context) {
        this.context = context;
    }

    public Op create(Op op) {
        final SkipGlobalQueryModifiers skipper = new SkipGlobalQueryModifiers();
        final Op skippedServices = skipper.visit(op);
        final Op transformed = this.visit(skippedServices, OpTable.unit());
        return skipper.reApply(transformed);
    }

    @Override
    public Op visit(OpService req, Op contextOfOperator) { // TODO possibly final recursive instead
        Var g = (Var) req.getService();
        final Op forGraph2SourceDataset = new ToGeneratorOfJoinOverUnions.RemoveLeftJoins().visit(context.query2summary.visit(req.getSubOp()));
        Op hat = new ToDistinctByProduceTPQPThenFilterExists(context).visit(
                new OpDistinct(new OpProject(
                        new OpGraph(g, forGraph2SourceDataset),
                        List.of(g))));
        if (context.shouldMaterializeSources) {
            return OpJoin.create(ToMaterializedSources.materialized(context, hat, req), req);
        }
        // TODO should add something with the operator's context so it becomes
        return new ToModifiedSourceNames(context.query2summary.getGraph2Source()).transform(hat, req);
    }

    public static class RemoveLeftJoins extends ReturningOpBaseVisitor {
        @Override public Op visit(OpConditional cond) { return visit(cond.getLeft()); }
        @Override public Op visit(OpLeftJoin lj) { return visit(lj.getLeft()); }
    }

}
