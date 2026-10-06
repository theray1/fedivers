package fr.gdd.fedivers;

import fr.gdd.fedivers.summaries.IGraph2SourceDataset;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.op.OpDistinct;
import org.apache.jena.sparql.algebra.op.OpProject;
import org.apache.jena.sparql.algebra.op.OpQuad;
import org.apache.jena.sparql.core.Quad;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.engine.ExecutionContext;
import org.apache.jena.sparql.engine.QueryEngineBase;
import org.apache.jena.sparql.engine.QueryIterator;
import org.apache.jena.sparql.engine.binding.Binding;
import org.apache.jena.sparql.engine.binding.BindingFactory;
import org.apache.jena.sparql.engine.main.QueryEngineMain;
import org.apache.jena.sparql.engine.main.QueryEngineMainQuad;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class FediversContext implements AutoCloseable {

    public final boolean shouldMaterializeSources;
    public final boolean shouldAsk;
    public final boolean shouldPushDownUnaries;

    public final IGraph2SourceDataset query2summary;
    public final IGraph2SourceDataset graph2source;

    public final ExecutionContext executionContext;
    public final Dataset summary;

    public final ExecutorService pool4asks = Executors.newFixedThreadPool(40);
    public final ConcurrentHashMap<Pair<Node, Triple>, CompletableFuture<Boolean>> asks = new ConcurrentHashMap<>();
    public final Supplier<QueryEngineBase> engineSupplier = () -> getQueryEngine();


    // default context config
    public FediversContext() {
        shouldMaterializeSources = false;
        shouldAsk = true;
        shouldPushDownUnaries = false;

        query2summary = null;
        graph2source = null;

        summary = DatasetFactory.empty();
        executionContext = ExecutionContext.create(summary.asDatasetGraph());
        // "file:///Users/boisteau-desdevises-e/Documents/datasets/fedshop20.ttl"
    }

    public FediversContext(boolean shouldMaterializeSources,
                           boolean shouldAsk,
                           boolean shouldPushDownUnaries,
                           IGraph2SourceDataset query2summary,
                           IGraph2SourceDataset graph2source,
                           ConcurrentHashMap<Pair<Node, Triple>, CompletableFuture<Boolean>> asks,
                           ExecutionContext executionContext,
                           Dataset summary) {
        this.shouldMaterializeSources = shouldMaterializeSources;
        this.shouldAsk = shouldAsk;
        this.shouldPushDownUnaries = shouldPushDownUnaries;

        this.query2summary = query2summary;
        this.graph2source = graph2source;

        this.executionContext = executionContext;
        this.summary = summary;
    }

    public static FediversContext copy(FediversContext other) {

        ConcurrentHashMap<Pair<Node, Triple>, CompletableFuture<Boolean>> newAsks = new ConcurrentHashMap<>();

        for (Map.Entry<Pair<Node, Triple>, CompletableFuture<Boolean>> entry : other.asks.entrySet()) {
            newAsks.putIfAbsent(entry.getKey(), new CompletableFuture<>());
        }

        return new FediversContext(
                other.shouldMaterializeSources,
                other.shouldAsk,
                other.shouldPushDownUnaries,
                other.query2summary,
                other.graph2source,
                newAsks,
                ExecutionContext.copy(other.executionContext),
                other.summary
        );
    }

    public QueryEngineBase getQueryEngine() {
        return new QueryEngineMainQuad((Op) null, summary.asDatasetGraph(), BindingFactory.empty(), executionContext.getContext());
    }

    public void execute(Op query, Binding binding, Consumer<Binding> consumer) {
        QueryIterator qi = engineSupplier.get().evaluate(query,
                summary.asDatasetGraph(),
                binding,
                executionContext.getContext());

        qi.forEachRemaining(consumer);
    }

    public void execute(Op query, Consumer<Binding> consumer) {
         execute(query, BindingFactory.empty(), consumer);
    }

    /**
     * @return The list of SPARQL endpoints (i.e., sources) retrieved
     *         from the local backend.
     */
    public Set<Node> getEndpoints () {
        final Var g = Var.alloc("g");
        Op g1FromSummary = new OpDistinct(new OpProject(
                new OpQuad(new Quad(g,
                        Triple.create(Var.alloc("s"), Var.alloc("p"), Var.alloc("o")))), List.of(g)));
        List<Binding> g1AsNode = new ArrayList<>();
        execute(g1FromSummary, g1AsNode::add);
        return g1AsNode.stream().map(n -> query2summary.getGraph2Source().apply(g).eval(n, executionContext).asNode()).collect(Collectors.toSet());
    }

    @Override
    public void close() throws Exception {
        pool4asks.shutdown();
    }
}
