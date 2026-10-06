package fr.gdd.fedivers.sources;

import fr.gdd.fedivers.optimizers.IsWithNestedService;
import fr.gdd.jena.visitors.ReturningOpCustomBaseVisitor;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.op.*;

/**
 * Things that were built local (e.g. querying a local summary)
 * are actually remote. So we must transform the generators of
 * such data to remote calls. The most convenient way would be that they
 * are wrapped in sub-queries. So this would avoid having to process the
 * exclusive groups.
 * *
 * /!\ When the summary is remote, the resulting graphs are not mapping
 *     at a time, since they need to be transferred in one-go. This could
 *     be alleviated via streaming or continuation queries.
 */
public class ToSourcesRemote extends ReturningOpCustomBaseVisitor {

    final Node remote;
    final Boolean silent;

    public ToSourcesRemote (String remote) { this(remote, true); }
    public ToSourcesRemote (String remote, Boolean silent) {
        this.setFunction(this::serviceWrapper);
        this.remote = NodeFactory.createURI(remote);
        this.silent = silent;
    }

    public Op serviceWrapper(Op op) {
        // try to get the largest piece of operator that does not have
        // a SERVICE inside.
        if (!(new IsWithNestedService().visit(op))) {
            return new OpService(remote, op, silent);
        }
        if (op instanceof OpService req) {
            return req; // stops here
        }
        return switch (op) { // otherwise, copy and explore for sub-service queries:
            case Op0 op0 -> this.visit(op0);
            case Op1 op1 -> op1.copy(this.visit(op1.getSubOp()));
            case Op2 op2 -> op2.copy(this.visit(op2.getLeft()), this.visit(op2.getRight()));
            case OpN opN -> opN.copy(opN.getElements().stream().map(this::visit).toList());
            default -> throw new UnsupportedOperationException("Could not wrap in SERVICE the operator: " + op);
        };
    }


}
