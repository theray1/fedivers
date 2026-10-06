package fr.gdd.fedivers.sources;

import fr.gdd.fedivers.FediversContext;
import fr.gdd.fedivers.FediversUtil;
import fr.gdd.fedivers.transformers.SkipGlobalQueryModifiers;
import fr.gdd.jena.utils.OpCloningUtil;
import fr.gdd.jena.visitors.ReturningArgsOpBaseVisitor;
import fr.gdd.jena.visitors.ReturningOpBaseVisitor;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.op.*;
import org.apache.jena.sparql.core.Var;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A builder of source selection query using the DISTINCT operator.
 */
public class ToSourcesUsingDistinct extends ReturningArgsOpBaseVisitor<Op> {

    final FediversContext context;

    public ToSourcesUsingDistinct(FediversContext context) { this.context = context; }

    public Op create(Op op) {
        final SkipGlobalQueryModifiers skipper = new SkipGlobalQueryModifiers();
        final Op skippedServices = skipper.visit(op);
        final Op transformed = this._create(skippedServices);
        return skipper.reApply(transformed);
    }

    public Op _create(Op op) {
        if (op instanceof OpLeftJoin lj) { return this.visit(lj, OpTable.unit()); } // no distinct wrapping left join
        final Op distinctGraphs = new Distinctor(context).create(op); // done before to retrieve variables
        // DISTINCT ?g1 ?g2 … { pattern on summary or graph } X SERVICE ?g1 {…} X SERVICE ?g2 {…}…
        return OpJoin.create(distinctGraphs, this.visit(op, OpTable.unit()));
    }

    public Op _create(Op op, Op parent) {
        if (op instanceof OpLeftJoin lj) { return this.visit(lj, parent); } // no distinct wrapping left join
        final Op distinctGraphs = new Distinctor(context).create(FediversUtil.join(parent, op)); // done before to retrieve variables
        // DISTINCT ?g1 ?g2 … { pattern on summary or graph } X SERVICE ?g1 {…} X SERVICE ?g2 {…}…
        return OpJoin.create(distinctGraphs, this.visit(op, parent));
    }

    @Override
    public Op visit(OpLeftJoin lj, Op parent) {
        return OpCloningUtil.clone(lj,
                this._create(lj.getLeft(), parent),
                this._create(lj.getRight(), FediversUtil.join(parent, lj.getLeft())));
    }

    @Override public Op visit(OpService req, Op parent) { return req; /* explicitly not visiting SERVICE */ }

    /* **************************************************************************** */

    private static class Distinctor extends ReturningOpBaseVisitor {
        final FediversContext context;
        final Set<Var> projectedGraphs = new HashSet<>();

        public Distinctor(FediversContext context) { this.context = context; }

        public Op create(Op op) {
            return OpDistinct.create(new OpProject(this.visit(op), projectedGraphs.stream().toList()));
        }

        @Override
        public Op visit(OpService req) {
            if (!req.getService().isVariable()) return OpTable.unit(); // nothing since already known source
            // create a graph pattern ?g executed on the summary or summary
            final Var graphVar = (Var) req.getService();
            this.projectedGraphs.add(graphVar);
            return new OpGraph(graphVar, context.query2summary.visit(req.getSubOp()));
        }

        @Override
        public Op visit(OpLeftJoin lj) {
            // we only visit left side, since the right part is not mandatory,
            // an additional processing is required that depends on the left.
            return visit(lj.getLeft());
        }

        @Override public Op visit(OpProject project) { return this.visit(project.getSubOp()); }
        @Override public Op visit(OpDistinct distinct) { return this.visit(distinct.getSubOp()); }
        @Override public Op visit(OpSlice slice) { return this.visit(slice.getSubOp()); }
        @Override public Op visit(OpOrder orderBy) { return this.visit(orderBy.getSubOp()); }

        @Override
        public Op visit(OpFilter filter) {
            return Objects.isNull(context.query2summary) ? super.visit(filter) :
                    this.visit(context.query2summary.visit(filter));
        }
    }


}
