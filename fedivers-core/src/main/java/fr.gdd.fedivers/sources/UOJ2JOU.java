package fr.gdd.fedivers.sources;

import fr.gdd.jena.visitors.ReturningOpBaseVisitor;
import org.apache.jena.graph.Node;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.Table;
import org.apache.jena.sparql.algebra.TableFactory;
import org.apache.jena.sparql.algebra.op.OpJoin;
import org.apache.jena.sparql.algebra.op.OpService;
import org.apache.jena.sparql.algebra.op.OpTable;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.engine.binding.Binding;
import org.apache.jena.sparql.engine.binding.BindingFactory;

import java.util.*;
import java.util.stream.Collectors;

public class UOJ2JOU extends ReturningOpBaseVisitor {

    Map<Var, Set<Node>> service2sources = new HashMap<>();

    // TODO : differentiate between VALUES clauses from input query, and VALUES clauses artificially added for sources

    @Override
    public Op visit(OpTable table) {
        List<Var> tableVars = table.getTable().getVars();
        for(Var var: tableVars) {
            service2sources.computeIfAbsent(var, k -> new HashSet<>());

            Iterator<Binding> bindingIterator = table.getTable().rows();

            while (bindingIterator.hasNext()) {
                Binding binding = bindingIterator.next();
                service2sources.get(var).add(binding.get(var));
            }
        }

        return OpTable.unit();
    }

    @Override
    public Op visit(OpService req) {
        if(service2sources.containsKey(req.getService())) {
            Var var = ((Var) req.getService());

            Table table = TableFactory.builder()
                    .addRows(service2sources.get(var).stream()
                            .map(node -> BindingFactory.binding(var, node))
                            .collect(Collectors.toUnmodifiableList()))
                    .addVar(var)
                    .build();

            return OpJoin.create(OpTable.create(table), req);
        }

        return super.visit(req);
    }
}
