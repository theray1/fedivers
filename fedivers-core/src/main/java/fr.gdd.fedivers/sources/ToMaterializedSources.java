package fr.gdd.fedivers.sources;

import fr.gdd.fedivers.FediversContext;
import fr.gdd.fedivers.asks.ASKing4TPsWithConstantFullAsync;
import fr.gdd.fedivers.transformers.SimplifyEmpty;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.OpAsQuery;
import org.apache.jena.sparql.algebra.Table;
import org.apache.jena.sparql.algebra.TableFactory;
import org.apache.jena.sparql.algebra.op.OpTable;
import org.apache.jena.sparql.core.Substitute;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.engine.QueryIterator;
import org.apache.jena.sparql.engine.binding.Binding;
import org.apache.jena.sparql.engine.binding.BindingBuilder;
import org.apache.jena.sparql.engine.binding.BindingFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Utility to execute the source assignment part of the query and transform
 * it to a VALUES.
 */
public class ToMaterializedSources {

    public static  OpTable materialized (FediversContext context, Op hat, Op services) {
        // We materialize the graphs, and we take the opportunity to change the endpoint
        // name as well, if needed.
        final List<Binding> results = new ArrayList<>();

        // TODO : engine execution
        context.execute(hat, results::add);

        if (results.isEmpty()) return OpTable.empty();

        List<Binding> sources = results.stream().map(b -> {
            BindingBuilder modifiedBuilder = BindingFactory.builder();
            b.varsMentioned().forEach(v ->
                    modifiedBuilder.add(v, context.query2summary.getGraph2Source().apply(v).eval(b, context.executionContext).asNode()
            ));
            return modifiedBuilder.build();
        }).toList();

        if (context.shouldAsk) {
            // Additional filter here. Without materialized, this would be done by the dynamic optimizer
            // positioned above the service(s).
            Map<Binding, CompletableFuture<Op>> handles = sources.stream()
                    .map(source -> {
                        Op substituted = Substitute.substitute(services, source);
                        return Map.entry(source, new ASKing4TPsWithConstantFullAsync(context).visit(substituted));
                    })
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            CompletableFuture.allOf(handles.values().toArray(CompletableFuture[]::new)).join();
            final Table tTransformed = TableFactory.create();
            handles.entrySet().stream().map(e -> Map.entry(e.getKey(), e.getValue().join()))
                    .map(e -> Map.entry(e.getKey(), new SimplifyEmpty().visit(e.getValue())))
                    .filter(e -> !e.getValue().equalTo(OpTable.empty(), null))
                    .map(Map.Entry::getKey)
                    .sorted(compareByVariableOrder(new ArrayList<>(sources.getFirst().varsMentioned()))) // sort to get a deterministic result
                    .forEach(tTransformed::addBinding);
            return OpTable.create(tTransformed);
        }

        final Table t = TableFactory.create();
        sources.stream()
                .sorted(compareByVariableOrder(new ArrayList<>(sources.getFirst().varsMentioned())))
                .forEach(t::addBinding); // sorted as well, for deterministic output.
        return OpTable.create(t); // hat gets materialized
    }

    static  Comparator<Binding> compareByVariableOrder(List<Var> vars) {
        return (a, b) -> {
            for (Var v : vars) {
                if (!a.contains(v) && !b.contains(v)) {
                    return Integer.compare(a.hashCode(), b.hashCode());
                } else if (!a.contains(v)) {
                    return -1;
                } else if (!b.contains(v)) {
                    return 1;
                } else { // both set compare the value
                    int compared = a.get(v).toString().compareTo(b.get(v).toString());
                    if (compared != 0) return compared;
                }
            }
            return 0;
        };
    }


}
