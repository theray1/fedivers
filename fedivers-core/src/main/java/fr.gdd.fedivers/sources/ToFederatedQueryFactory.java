package fr.gdd.fedivers.sources;

import fr.gdd.fedivers.FediversContext;
import fr.gdd.jena.utils.OpCloningUtil;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.OpVars;
import org.apache.jena.sparql.algebra.op.*;
import org.apache.jena.sparql.core.Var;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The entrypoint to build federated queries. There are several approaches
 * that provide different properties, and performance tradeoff at runtime.
 * See: <a href="https://github.com/Chat-Wane/passage-secret/issues/29">#29</a>
 */
public class ToFederatedQueryFactory {

    /**
     * The DISTINCT is reliable and use NOT EXISTS as a backup
     * when the number of elements is too large to remain in memory.
     * @return DISTINCT SSQ x SERVICE
     */
    public static  Op MDCDServices (FediversContext context, Op query) {
        Set<Var> vars = OpVars.visibleVars(query);

        // MD CD decomposition is default in the services query:
        final Op services = new ToServices(context, true).create(query);

        // final Op remoteSummary = new ToSourcesRemote("http://remote").visit(withDistinct);

        return new OpProject(services, new ArrayList<>(vars));
    }

    /**
     * The DISTINCT is reliable and use NOT EXISTS as a backup
     * when the number of elements is too large to remain in memory.
     * @return DISTINCT SSQ x SERVICE
     */
    public static  Op distinctSSHandlesAll (FediversContext context, Op query) {
        Set<Var> vars = OpVars.visibleVars(query);

        // MD CD decomposition is default in the services query:
        final Op services = new ToServices(context, true).create(query);
        final Op withDistinct = new ToSourcesUsingDistinct(context).create(services);
        // TODO if remote summary.
        // final Op remoteSummary = new ToSourcesRemote("http://remote").visit(withDistinct);

        return new OpProject(withDistinct, new ArrayList<>(vars));
    }

    /**
     * Create the SERVICE clauses following the MD/CD decomposition.
     * To get the sources, it creates a hat of one DISTINCT triple pattern per service,
     * then FILTER EXISTS it using the query with join variables.
     * @return PRODUCER x FILTER x SERVICE
     */
    public static  Op MDCDUnionOverJoins (FediversContext context, Op query) {
        Set<Var> vars = OpVars.visibleVars(query);

        final Op services = new ToServices(context, true).create(query);
        final Op withGenerator = new ToGeneratorOfUnionOverJoins(context).create(services);

        return replaceOrCreateTopLevelProject(withGenerator, new OpProject(withGenerator, new ArrayList<>(vars)));
    }

    public static  Op MDCDJoinOverUnions (FediversContext context, Op query) {
        Set<Var> vars = OpVars.visibleVars(query);

        final Op services = new ToServices(context, true).create(query);
        final Op withGenerator = new ToGeneratorOfJoinOverUnions(context).create(services);

        // TODO : add toggle for provenance in context
        return replaceOrCreateTopLevelProject(withGenerator, new OpProject(withGenerator, new ArrayList<>(vars)));
    }

    public  static  Op MDCDJoinOverUnionsWithResultAware(FediversContext context, Op query) {
        final Op services = new ToServices(context, true).create(query);
        final Op withGenerator = new ToGeneratorOfJoinOverUnionsWithResultAware(context).create(services);
        return withGenerator;
    }

    public static Op replaceOrCreateTopLevelProject(Op op, OpProject topLevelProject) {
        final List<Op> modifiers = new ArrayList<>();

        Op current = op;

        while (current instanceof OpModifier) {
            if(current instanceof OpProject) {
                modifiers.add(OpCloningUtil.clone(topLevelProject, op));
            } else {
                modifiers.add(current);
            }
            current = ((OpModifier) current).getSubOp();
        }

        if (modifiers.isEmpty()) return OpCloningUtil.clone(topLevelProject, op); // nothing to do
        Op transformed = current;
        for  (int i = modifiers.size() - 1; i >= 0 ; --i) {
            transformed = switch (modifiers.get(i)) {
                case OpDistinct distinct -> OpCloningUtil.clone(distinct, transformed);
                case OpSlice slice -> OpCloningUtil.clone(slice, transformed);
                case OpOrder order -> OpCloningUtil.clone(order, transformed);
                case OpProject project -> OpCloningUtil.clone(project, transformed);
                case OpGroup group -> OpCloningUtil.clone(group, transformed);
                default -> throw new UnsupportedOperationException("Query modifier unknown: " + modifiers.get(i));
            };
        }
        return transformed;
    }

}
