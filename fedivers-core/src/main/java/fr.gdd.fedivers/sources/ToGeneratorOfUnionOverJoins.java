package fr.gdd.fedivers.sources;

import fr.gdd.fedivers.FediversContext;
import fr.gdd.fedivers.FediversUtil;
import fr.gdd.fedivers.summaries.IdentityDataset;
import fr.gdd.fedivers.transformers.SkipGlobalQueryModifiers;
import fr.gdd.jena.utils.FlattenUnflatten;
import fr.gdd.jena.utils.OpCloningUtil;
import fr.gdd.jena.visitors.ReturningArgsOpBaseVisitor;
import fr.gdd.jena.visitors.ReturningOpBaseVisitor;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.op.*;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.expr.E_Exists;

import java.util.*;

/**
 * Annotate the services with a generator and filter of graphs.
 * This generator is designed to pick a triple pattern of each service
 * to iterator over.
 * It generates SPARQL queries like:
 * SELECT * WHERE {
 *     DISTINCT ?g1 WHERE { … }
 *     DISTINCT ?g2 WHERE { … }
 *     FILTER EXISTS ( SELECT ?g1 ?g2 WHERE { … } )
 *     SERVICE ?g1 { … }
 *     SERVICE ?g2 { … } }
 *  When asking for materialized, it will modify the group of DISTINCT + FILTER as VALUES:
 *  SELECT * WHERE {
 *     VALUES (?g1 ?g2) { (g1 g2) (g1 g3) … (g2 g1) }
 *     SERVICE ?g1 { … }
 *     SERVICE ?g2 { … }
 *  }
 */
public class ToGeneratorOfUnionOverJoins extends ReturningArgsOpBaseVisitor<Op> {

    final FediversContext context;

    public ToGeneratorOfUnionOverJoins(FediversContext context) { this.context = context; }

    public Op create(Op op) {
        final SkipGlobalQueryModifiers skipper = new SkipGlobalQueryModifiers();
        final Op skippedServices = skipper.visit(op);
        final Op transformed = this._create(skippedServices, OpTable.unit());
        return skipper.reApply(transformed);
    }

    /**
     * @param op The operator currently being rewritten.
     * @param parentServices Sometimes, the current graph pattern require a more global
     *                 context of graphs (i.e. mostly the inner part of left joins).
     */
    public Op _create(Op op, Op parentServices) {
        if (op instanceof OpLeftJoin lj) { // left join is special
            return this.visit(lj, parentServices); // no distinct wrapping left join
        }

        List<Op> unionChildren = FlattenUnflatten.flattenUnion(op);
        List<Op> hattedChildren = new ArrayList<>();
        for(Op child : unionChildren) {
            final Op producer = new Producer(context).visit(child);
            final Op services = this.visit(child, parentServices);
            if (context.shouldMaterializeSources) {
                // when the sources get materialized, we must reproduce the mandatory part
                // of the parent, so it joins when actually executed.
                final Op producerWithParent = FediversUtil.join(new Producer(context).visit(parentServices), producer);
                final Op distinctGraphs = new Filterer(context).create(FediversUtil.join(parentServices, child), producerWithParent);
                Op materializedHat = ToMaterializedSources.materialized(context, distinctGraphs, FediversUtil.join(parentServices, services));
                hattedChildren.add(FediversUtil.join(materializedHat, services));
            } else {
                // otherwise, no need to repeat:
                // DISTINCT ?g1 ?g2 … { pattern on summary or graph } X SERVICE ?g1 {…} X SERVICE ?g2 {…}…
                final Op distinctGraphs = new Filterer(context).create(FediversUtil.join(parentServices, child), producer); // done before to retrieve variables
                if (Objects.nonNull(context.query2summary.getGraph2Source())) {
                    hattedChildren.add(new ToModifiedSourceNames(context.query2summary.getGraph2Source()).transform(distinctGraphs, services));
                } else {
                    hattedChildren.add(FediversUtil.join(distinctGraphs, services));
                }
            }
        }

        return FlattenUnflatten.unflattenUnion(hattedChildren);
    }

    @Override
    public Op visit(OpJoin join, Op parent) {
        final Op left = this.visit(join.getLeft(), parent);
        final Op right = this.visit(join.getRight(), FediversUtil.join(parent, join.getLeft()));
        return FediversUtil.join(left, right);
    }

    @Override
    public Op visit(OpLeftJoin lj, Op parent) {
        Op mandatory = parent.equalTo(OpTable.unit(), null) ? this._create(lj.getLeft(), OpTable.unit()) : this._create(lj.getLeft(), parent);
        Op optional = this._create(lj.getRight(), FediversUtil.join(parent, lj.getLeft()));

        if(Objects.isNull(optional)) return mandatory;
        return OpCloningUtil.clone(lj, mandatory, optional);
    }

    @Override public Op visit(OpService req, Op parent) { return req; /* explicitly not visiting SERVICE */ }

    /* **************************************************************************** */

    /**
     * Creates the producer of graphs by picking on triple pattern per service.
     */
    public static class Producer extends ReturningOpBaseVisitor {
        final FediversContext context;
        public Producer(FediversContext context) { this.context = context; }

        @Override
        public Op visit(OpService req) {
            final Var graphVar = (Var) req.getService();
            final Op pickedPattern = new LeftPicker().visit(req.getSubOp());
            return new OpDistinct(new OpProject(
                    new OpGraph(graphVar,
                            Objects.isNull(context.query2summary) ?
                                    pickedPattern :
                                    context.query2summary.visit(pickedPattern)),
                    List.of(graphVar)));
        }

        @Override
        public Op visit(OpProject project) {
            return this.visit(project.getSubOp());
        }

        @Override
        public Op visit(OpFilter filter) {
            // Regardless of the summary strategy, the filters should never appear in the producer
            // because it may not contain triples with variables present in the filters.
            // They may appear in the Filterer.
            return this.visit(filter.getSubOp());
        }

    }

    /**
     * Within a service, picks the leftest triple pattern, with the following
     * rationale: if the join orderer did a good job, it put the most selective
     * triple pattern first.
     */
    public static class LeftPicker extends ReturningOpBaseVisitor {

        @Override
        public Op visit(OpSequence sequence) {
            return sequence.getElements().stream().map(this::visit).filter(s -> !s.equalTo(OpTable.empty(), null))
                    .findFirst().orElse(OpTable.empty());
        }

        @Override
        public Op visit(OpBGP bgp) { return new OpTriple(bgp.getPattern().getList().getFirst()); }
        @Override public Op visit(OpJoin join) { return this.visit((Op2) join); }
        @Override public Op visit(OpMinus minus) { return this.visit((Op2) minus); }
        @Override public Op visit(OpConditional cond) { return this.visit((Op2) cond);  }
        @Override public Op visit(OpLeftJoin lj) {  return this.visit((Op2) lj); }
        @Override public Op visit(OpExtend extend) { return visit(extend.getSubOp()); }
        @Override public Op visit(OpDistinct distinct) { return this.visit(distinct.getSubOp());}
        @Override public Op visit(OpSlice slice) { return this.visit(slice.getSubOp()); }
        @Override public Op visit(OpProject project) { return visit(project.getSubOp()); }
        @Override public Op visit(OpUnion union) { return visit((Op2) union); }
        @Override public Op visit(OpTable table) { return OpTable.empty(); }
        @Override public Op visit(OpOrder orderBy) { return visit(orderBy.getSubOp()); }
        @Override public Op visit(OpFilter filter) { return visit(filter.getSubOp()); }

        public Op visit(Op2 op) {
            final Op left = visit(op.getLeft());
            final Op right = visit(op.getRight());
            if (left.equalTo(OpTable.empty(), null)) return right;
            if (right.equalTo(OpTable.empty(), null)) return left;
            return left;
        }
    }

    /**
     * Creates a FILTER EXISTS between the sources and the services, that
     * checks if the graphs actually joins, based on the query.
     */
    public static class Filterer extends ReturningOpBaseVisitor {
        final FediversContext context;
        final Set<Var> projectedGraphs = new HashSet<>();

        public Filterer(FediversContext context) { this.context = context; }

        public Op create(Op op, Op producer) {
            return OpFilter.filter(
                    new E_Exists(new OpProject(this.visit(op) /* retrieve the projected graphs as well */,
                            projectedGraphs.stream().toList())),
                    producer);
        }

        @Override
        public Op visit(OpService req) {
            if (!req.getService().isVariable()) return OpTable.unit(); // nothing since already known source
            // create a graph pattern ?g executed on the summary or summary
            final Var graphVar = (Var) req.getService();
            this.projectedGraphs.add(graphVar);
            return new OpGraph(graphVar, new ToGeneratorOfJoinOverUnions.RemoveLeftJoins().visit(
                    context.query2summary.visit(req.getSubOp())));
        }

        @Override
        public Op visit(OpLeftJoin lj) {
            // we only visit left side, since the right part is not mandatory,
            // an additional processing is required that depends on the left.
            return visit(lj.getLeft());
        }

        @Override
        public Op visit(OpFilter filter) {
            return context.query2summary instanceof IdentityDataset ?
                    super.visit(filter) :
                    this.visit(context.query2summary.visit(filter));
        }

    }


}
