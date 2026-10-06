package fr.gdd.fedivers.asks;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.*;
import org.apache.jena.sparql.algebra.OpAsQuery;
import org.apache.jena.sparql.algebra.op.OpGraph;
import org.apache.jena.sparql.algebra.op.OpTriple;
import org.apache.jena.sparql.exec.http.QueryExecutionHTTPBuilder;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Task of a thread that consists in performing an ASK query and register the result
 * in a shared map.
 * TODO make retry configurable
 * TODO make timeout configurable
 */
public class ASKRunnable {

    final ConcurrentHashMap<Pair<Node, Triple>, CompletableFuture<Boolean>> asks; /* shared */
    final Triple triple; // TODO not triples only.
    final Node endpoint;
    QueryExecutionBuilder builder;
    final Dataset dataset;
    final ExecutorService dedicatedPoolForAsks;

    public static Integer RETRY = 5;
    public static boolean ON_UNREACHABLE = false; // unreachable so it's false, it's assume to not contain the data

    public ASKRunnable(ExecutorService pool, ConcurrentHashMap<Pair<Node, Triple>, CompletableFuture<Boolean>> asks, Node endpoint, Triple triple) {
        this(pool, asks, endpoint, triple, null); // asks performed on remote endpoints
    }

    public ASKRunnable(ExecutorService pool, ConcurrentHashMap<Pair<Node, Triple>, CompletableFuture<Boolean>> asks, Node endpoint, Triple triple, Dataset dataset) {
        this.asks = asks;
        this.triple = triple;
        this.endpoint = endpoint;
        this.dedicatedPoolForAsks = pool;
        if (Objects.isNull(dataset)) {
            this.builder = QueryExecutionHTTPBuilder.create();
            ((QueryExecutionHTTPBuilder) this.builder).endpoint(endpoint.getURI());
        } else {
            this.builder = QueryExecutionDatasetBuilder.create();
            ((QueryExecutionDatasetBuilder) this.builder).dataset(dataset);
        }
        this.dataset = dataset;
    }

    public CompletableFuture<Boolean> run() {
        var id = Pair.of(endpoint, triple);
        return asks.computeIfAbsent(id, _ -> CompletableFuture.supplyAsync(() ->
                switch (builder) {
                    case QueryExecutionHTTPBuilder b -> {
                        int retry = RETRY;
                        Query query = OpAsQuery.asQuery(new OpTriple(triple));
                        // TODO if ASK not supported, perform a LIMIT 1 query.
                        // Query query = OpAsQuery.asQuery(new OpSlice(new OpTriple(triple), 0, 1));
                        query.setQueryAskType();
                        while (retry > 0) {
                            try {
                                // var results = b.query(query).timeout(5, TimeUnit.SECONDS).select();
                                // yield results.hasNext();
                                yield b.query(query).timeout(5000, TimeUnit.MILLISECONDS).ask();
                            } catch (QueryException e) {
                                retry -= 1;
                            }
                        }
                        yield ON_UNREACHABLE; // too many fails
                    }
                    case QueryExecutionDatasetBuilder b -> { // local
                        Query query = OpAsQuery.asQuery(new OpGraph(endpoint, new OpTriple(triple)));
                        query.setQueryAskType();
                        dataset.begin(ReadWrite.READ);
                        boolean r = b.query(query).ask(); // summary must be in read txn
                        dataset.commit();
                        dataset.end();
                        yield r;
                    }
                    default -> throw new UnsupportedOperationException();
                }, dedicatedPoolForAsks).completeOnTimeout(ON_UNREACHABLE, 1200, TimeUnit.SECONDS));
    }


}
