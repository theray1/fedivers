package fr.gdd.fedivers.transformers;

import fr.gdd.fedivers.RandomVarProvider;
import fr.gdd.jena.utils.FlattenUnflatten;
import fr.gdd.jena.visitors.ReturningOpBaseVisitor;
import org.apache.jena.graph.Node;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.op.OpSequence;
import org.apache.jena.sparql.algebra.op.OpService;
import org.apache.jena.sparql.algebra.op.OpTable;
import org.apache.jena.sparql.algebra.op.OpUnion;
import org.apache.jena.sparql.algebra.table.TableN;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.engine.binding.BindingFactory;

import java.util.*;

/**
 * When there exists a union of multiple SERVICES but with the same content, we factorize
 * them using a VALUES. It improves readability, and may also improve performance if it
 * requires to be transformed for other federated query executor: binary UNIONs
 * prove costly to transform, since they greatly increase the query size for no reason.
 */
public class UnionOfSameServices2ValuesOfSameServices extends ReturningOpBaseVisitor {

    final RandomVarProvider rv = new RandomVarProvider("__g");

    @Override
    public Op visit(OpUnion union) {
        List<Op> ops = FlattenUnflatten.flattenUnion(union);
        // #1 retrieve the list of services
        List<OpService> services = ops.stream().filter(o -> o instanceof OpService).map(o-> (OpService) o).toList();
        List<Op> rest = new ArrayList<>(ops.stream().filter(o -> !(o instanceof OpService))
                .map(o -> visit(this.visit(o)))
                .toList());
        // #2 compute common services and register their sources
        Map<Op, List<Node>> service2sources = new HashMap<>();
        services.forEach(s -> {
            service2sources.putIfAbsent(s.getSubOp(), new ArrayList<>());
            service2sources.get(s.getSubOp()).add(s.getService());
        });
        // #3 rebuild the services as VALUES
        List<Op> rebuiltServices = new ArrayList<>();
        service2sources.forEach((subOpOfService, sources) -> {
            // if (sources.size() <= 1) {
            //    rebuiltServices.add(new OpService(sources.getFirst(), subOpOfService ,true)); // TODO get silent from service
            //} else {
                Var newVar = rv.get();
                TableN table = new TableN(List.of(newVar));
                sources.forEach(g -> table.addBinding(BindingFactory.binding(newVar, g)));
                rebuiltServices.add(
                        OpSequence.create(OpTable.create(table), new OpService(newVar, subOpOfService, true)) // TODO get silent from service
                );
            //}
        });
        // #4 unionize all rebuilt services, and the rest of the query that was not services
        rest.addAll(rebuiltServices);
        return FlattenUnflatten.unflattenUnion(rest);
    }

}
