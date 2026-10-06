package fr.gdd.fedivers.sources;

import fr.gdd.fedivers.FediversContext;
import fr.gdd.fedivers.FediversUtil;
import fr.gdd.fedivers.RandomVarProvider;
import fr.gdd.fedivers.asks.ASKing4TPsWithConstantFullAsync;
import fr.gdd.fedivers.summaries.IGraph2SourceDataset;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.jena.graph.Node;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.op.*;
import org.apache.jena.sparql.core.BasicPattern;
import org.apache.jena.sparql.core.Quad;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.engine.binding.Binding;
import org.apache.jena.sparql.expr.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The matrix of pair-wise analysis of triple-patterns and
 * their domain.
 */
public class MatrixMDCD extends Matrix2D<MatrixMDCD.DOMAIN> {

    public enum DOMAIN { CROSS, MULTI, UNDEFINED }
    final Map<Integer, OpTriple> id2tp = new TreeMap<>();
    final IdentityHashMap<OpTriple, Integer> tp2id = new IdentityHashMap<>();
    final FediversContext context;
    final RandomVarProvider vng = new RandomVarProvider("__g");
    static final boolean SILENT = false;

    public MatrixMDCD(FediversContext context) {
        super(DOMAIN.UNDEFINED);
        this.context = context;
    }

    public Op asJoinedQuadPatterns() {
        return this.getWeakComponentsOnMD().stream().map(c-> {
                    Var graphVar = vng.get();
                    Op md = new OpQuadPattern(graphVar, new BasicPattern(c.stream().map(i -> id2tp.get(i).getTriple()).toList()));
                    md = OpDistinct.create(new OpProject(md, List.of(graphVar)));
                    return md;
                })
                .reduce(null,
                        (base, op) -> Objects.isNull(base) ? op : FediversUtil.join(base, op),
                        FediversUtil::join);
    }

    public Op asJoinedServices() {
        return this.getWeakComponentsOnMD().stream().map(c-> {
                    Var graphVar = vng.get();
                    return new OpService(graphVar,
                            new OpBGP(new BasicPattern(c.stream().map(i -> id2tp.get(i).getTriple()).toList())),
                            SILENT); // silent
                })
                .reduce(null,
                        (base, op) -> Objects.isNull(base) ? op : FediversUtil.join(base, op),
                        FediversUtil::join);
    }

    public Op asSequenceOfServices() {
        final OpSequence asSequence = OpSequence.create();
        this.getWeakComponentsOnMD().stream().map(c-> {
            Var graphVar = vng.get();
            return new OpService(graphVar,
                    new OpBGP(new BasicPattern(c.stream().map(i -> id2tp.get(i).getTriple()).toList())),
                    SILENT); // silent
        }).forEach(asSequence::add);
        return (asSequence.size() == 1) ? asSequence.getElements().getFirst() : asSequence;
    }

    public MatrixMDCD add(OpBGP bgp) { bgp.getPattern().forEach(t -> this.add(new OpTriple(t))); return this; }

    /**
     * @param tp The triple pattern to check. Internally allocates it an identifier.
     * @return This, for convenience.
     */
    public MatrixMDCD add(OpTriple tp) {
        if (tp2id.containsKey(tp)) return this; // already done.
        // otherwise register and execute
        final int id = id2tp.size();
        id2tp.put(id, tp);
        tp2id.put(tp, id);
        return this;
    }

    /**
     * @return This, with the matrix actually filled with results.
     */
    public MatrixMDCD execute() {
        if (context.shouldAsk) return _executeWithASKsFilter().join();
        return _executeWithoutASKsFilter();
    }

    public MatrixMDCD _executeWithoutASKsFilter() {
        if (tp2id.size() <= 1) { return this; } // nothing to do
        // TODO select the triple patterns with joining variables
        List<OpTriple> triples = new ArrayList<>(tp2id.keySet());
        for (int i = 0; i < triples.size() - 1; ++i) {
            final OpTriple currentTp = triples.get(i);
            for (int j = i + 1; j < triples.size(); ++j) {
                final OpTriple oldTp = triples.get(j);
                if (this.get(tp2id.get(currentTp), tp2id.get(oldTp)) != DOMAIN.UNDEFINED) continue; // already processed
                final Op checkerQuery = createMDCheckerQuery(oldTp, currentTp);
                checkPairAndUpdateMatrix(checkerQuery, currentTp, oldTp);
            }
        }
        return this;
    }

    /**
     * @return This, with the matrix actually filled with results.
     */
    public CompletableFuture<MatrixMDCD> _executeWithASKsFilter() {
        if (tp2id.size() <= 1) { return CompletableFuture.completedFuture(this); } // nothing to do
        // TODO select the triple patterns with joining variables
        List<OpTriple> triples = new ArrayList<>(tp2id.keySet());
        List<CompletableFuture<Void>> handles = new ArrayList<>();
        for (int i = 0; i < triples.size() - 1; ++i) {
            final OpTriple currentTp = triples.get(i);
            for (int j = i + 1; j < triples.size(); ++j) {
                final OpTriple oldTp = triples.get(j);
                if (this.get(tp2id.get(currentTp), tp2id.get(oldTp)) != DOMAIN.UNDEFINED) continue; // already processed
                // creating the query with FILTER on graphs require making ASK
                final CompletableFuture<Op> checkerQuery = createMDCheckQueryWithASKFiltersOnSources(oldTp, currentTp);
                final CompletableFuture<Void> onSummary = checkerQuery.thenAccept(o -> checkPairAndUpdateMatrix(o, currentTp, oldTp) /* execute on summary */ );
                handles.add(onSummary);
            }
        }
        return CompletableFuture.allOf(handles.toArray(CompletableFuture[]::new)).thenApply(_ -> this);
    }

    private void checkPairAndUpdateMatrix(Op checkerQuery, OpTriple currentTp, OpTriple oldTp) {
        final var fullExecutionContext = FediversContext.copy(context);

        final AtomicReference<Binding> found = new AtomicReference<>();

        fullExecutionContext.execute(checkerQuery, found::set);

        // found = true <=> CD
        boolean isCD = Objects.nonNull(found.get()); // There exists a g1 != g2
        this.set(tp2id.get(currentTp), tp2id.get(oldTp), isCD ? DOMAIN.CROSS : DOMAIN.MULTI);
        this.set(tp2id.get(oldTp), tp2id.get(currentTp), isCD ? DOMAIN.CROSS : DOMAIN.MULTI); // symmetric
    }

    /**
     * @param other The matrix to merge with.
     * @return This, modified to integrate the other matrix data.
     */
    public MatrixMDCD merge(MatrixMDCD other) {
        // allocate new identifiers to the patterns;
        other.id2tp.forEach((_, value) -> this.add(value));
        // then fill the matrix with values from the other matrix
        for (Map.Entry<Integer, OpTriple> kv : other.id2tp.entrySet()) {
            for (Map.Entry<Integer, OpTriple> kv2 : other.id2tp.entrySet()) {
                int newId1 = this.tp2id.get(kv.getValue());
                int newId2 = this.tp2id.get(kv2.getValue());
                this.set(newId1, newId2, other.get(kv.getKey(), kv2.getKey()));
                this.set(newId2, newId1, other.get(kv2.getKey(), kv.getKey()));
            }
        }
        return this;
    }

    /**
     * The maintained matrix is an adjacency matrix. We can compute
     * the set of connected multi-domain triple patterns with a weak
     * components processing.
     * @return A list of set of identifier representing the sets of
     *         MD triple patterns. Hence, disjoint sets are considered
     *         as possibly cross-domain.
     */
    List<Set<Integer>> getWeakComponentsOnMD () {
        if (this.rows() <= 1) {
            // nothing has been executed so by default, we assume CD.
            return id2tp.keySet().stream().map(Set::of).toList();
        }
        List<Set<Integer>> achievedComponents = new ArrayList<>();
        Set<Integer> currentComponent = new TreeSet<>();
        Set<Integer> visited = new TreeSet<>();
        Deque<Integer> toVisit = new ArrayDeque<>(List.of(0));
        // TODO modify the order of checks using a variable-based heuristic.
        while (!toVisit.isEmpty()) {
            int currentId = toVisit.poll();
            currentComponent.add(currentId);
            visited.add(currentId);
            for (int j=0; j < this.cols(currentId); ++j) {
                if (currentId != j && this.get(currentId, j) == DOMAIN.MULTI) {
                    currentComponent.add(j);
                    if (!visited.contains(j)) {
                        toVisit.add(j);
                    }
                }
            }
            if (toVisit.isEmpty()) {
                achievedComponents.add(currentComponent);
                currentComponent = new TreeSet<>();
                int id = 0; // compute a new starting point
                while (id < this.rows() && toVisit.isEmpty()) {
                    if (!visited.contains(id))
                        toVisit.add(id);
                    ++id;
                }
            }
        }
        return achievedComponents;
    }

    /**
     * @param tp1 The triple pattern 1.
     * @param tp2 The triple pattern 2.
     * @return The query that determines if the graphs are always equal meaning
     *         that the query is a single-domain (SD) or a multi-domain (MD).
     *         If there exists a pair of different graphs, then the query is
     *         cross-domain (CD).
     */
    Op createMDCheckerQuery(OpTriple tp1, OpTriple tp2) {
        return createMDCheckerQuery(tp1, tp2, ExprList.create());
    }

    /**
     * @param tp1 The triple pattern 1.
     * @param tp2 The triple pattern 2.
     * @return The query that determines if the graphs are always equal meaning
     *         that the query is a single-domain (SD) or a multi-domain (MD).
     *         If there exists a pair of different graphs, then the query is
     *         cross-domain (CD).
     */
    Op createMDCheckerQuery(OpTriple tp1, OpTriple tp2, ExprList ASKFilters) {
        final Var g1 = Var.alloc("g1");
        final Var g2 = Var.alloc("g2");
        final Op qp1 = new OpQuad(new Quad(g1, tp1.getTriple()));
        final Op qp2 = new OpQuad(new Quad(g2, tp2.getTriple()));
        final Op joined = OpJoin.create(qp1, qp2);
        final Op forDB = context.query2summary.visit(joined);
        // We put the filter ?g1 != ?g2 to be executed first since the filter
        // on graph identity might be costly and of unbounded size.
        final Op filtered = OpFilter.filter(new E_NotEquals(new ExprVar(g1), new ExprVar(g2)), forDB); // ?g1 != ?g2
        final Op ASKFiltered = OpFilter.filterBy(ASKFilters, filtered); // ?g1 != <g1> … && … ?g2 != <g2>
        return new OpSlice(ASKFiltered, 0, 1); // LIMIT 1
    }

    public record G2S (Node g, Node s) { /* graph to source */ }

    CompletableFuture<Op> createMDCheckQueryWithASKFiltersOnSources(OpTriple tp1, OpTriple tp2) {
        final Var g = Var.alloc("g");
        Op g1FromSummary = context.query2summary.visit(new OpDistinct(new OpProject(new OpGraph(g, tp1), List.of(g))));
        Op g2FromSummary = context.query2summary.visit(new OpDistinct(new OpProject(new OpGraph(g, tp2), List.of(g))));
        List<Binding> g1AsNode = new ArrayList<>();
        List<Binding> g2AsNode = new ArrayList<>();
        context.execute(g1FromSummary, g1AsNode::add);
        context.execute(g2FromSummary, g2AsNode::add);

        IGraph2SourceDataset toSummary = context.query2summary;
        List<CompletableFuture<Pair<G2S, Boolean>>> handlesG1 = new ArrayList<>();
        for (G2S g1: g1AsNode.stream().map(n -> new G2S(n.get(g), toSummary.getGraph2Source().apply(g).eval(n, context.executionContext).asNode())).toList()) {
            CompletableFuture<Pair<G2S, Boolean>> handleG1 = new ASKing4TPsWithConstantFullAsync.AskingAService(context, g1.s).visit(tp1).thenApply(b -> Pair.of(g1, b));
            handlesG1.add(handleG1);
        }

        List<CompletableFuture<Pair<G2S, Boolean>>> handlesG2 = new ArrayList<>();
        for (G2S g2: g2AsNode.stream().map(n -> new G2S(n.get(g), toSummary.getGraph2Source().apply(g).eval(n, context.executionContext).asNode())).toList()) {
            CompletableFuture<Pair<G2S, Boolean>> handleG2 = new ASKing4TPsWithConstantFullAsync.AskingAService(context, g2.s).visit(tp2).thenApply(b -> Pair.of(g2, b));
            handlesG2.add(handleG2);
        }

        CompletableFuture<List<Expr>> g1expr = CompletableFuture.allOf(handlesG1.toArray(CompletableFuture[]::new))
                .thenApply( _ -> handlesG1.stream().map(CompletableFuture::join)
                        .map(pair -> !pair.getValue() ? pair.getKey().g : null )
                        .filter(Objects::nonNull)
                        .map(g1 -> (Expr) new E_NotEquals(new ExprVar(Var.alloc("g1")), ExprLib.nodeToExpr(g1)))
                        .toList());

        CompletableFuture<List<Expr>> g2expr = CompletableFuture.allOf(handlesG2.toArray(CompletableFuture[]::new))
                .thenApply( _ -> handlesG2.stream().map(CompletableFuture::join)
                        .map(pair -> !pair.getValue() ? pair.getKey().g : null )
                        .filter(Objects::nonNull)
                        .map(g2 -> (Expr) new E_NotEquals(new ExprVar(Var.alloc("g2")), ExprLib.nodeToExpr(g2)))
                        .toList());

        return CompletableFuture.allOf(g1expr, g2expr).thenApply(_ -> {
            List<Expr> filters = new ArrayList<>(g1expr.join());
            filters.addAll(g2expr.join());
            if (filters.isEmpty()) filters.add(NodeValue.TRUE);
            return createMDCheckerQuery(tp1, tp2, new ExprList(filters));
        });
    }

}
