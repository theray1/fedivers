package fr.gdd.fedivers.summaries;

import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.*;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.sparql.algebra.OpAsQuery;
import org.apache.jena.sparql.algebra.op.OpDistinct;
import org.apache.jena.sparql.algebra.op.OpGraph;
import org.apache.jena.sparql.algebra.op.OpProject;
import org.apache.jena.sparql.algebra.op.OpTriple;
import org.apache.jena.sparql.core.Quad;
import org.apache.jena.sparql.core.Var;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SummarizerUtils {

    public static void summarizer(URI source) {
        Set<String> graphs = getGraphsFromService(source);
        System.out.println("Number of graphs to summarize: " + graphs.size());
        List<String> graphsAdded = new ArrayList<>();
        graphs.forEach(graph -> {
            graphsAdded.add(graph);
            System.out.printf("%s: Started summarizing %s…%n", graphsAdded.size(), graph);
            List<Quad> quads2add = new ArrayList<>();
            List<Triple> triples = getSPOFromServiceGraph(source, graph);
            triples.forEach(triple -> {
                quads2add.add(Quad.create(NodeFactory.createURI(graph), triple));
                });
                System.out.printf("%s: Summarizing %s triples…%n", graphsAdded.size(), quads2add.size());

                // int nbSummarized = updateSummary(options.output, graph, triples, summary);

                // System.out.printf("%s: Summary contains %s triples for this graph.%n", graphsAdded.size(), nbSummarized);
        });
    }

    public static String generateNQ(Set<Quad> quads) {
        final ToFedUPBetterSummary summarizer = new ToFedUPBetterSummary(100_000);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        RDFDataMgr.writeQuads(out, quads.stream().map(summarizer::toSummaryQuad).iterator());
        return out.toString();
    }

    /**
     * @return The Jena Query that retrieves all the distinct graphs of a summary.
     *         The graphs are designed to end up in Variable ?g.
     */
    public static Query getQueryGraphs() { return getQueryGraphs(Var.alloc("g")); }

    public static Query getQueryGraphs(Var g) {
        // SELECT DISTINCT ?g WHERE { GRAPH ?g { ?s ?p ?o } }
        final Var s = Var.alloc("s"), p = Var.alloc("p"), o = Var.alloc("o");
        return OpAsQuery.asQuery(new OpDistinct(new OpProject(
                new OpGraph(g, new OpTriple(Triple.create(s, p, o))),
                List.of(g))));
    }

    public static Query getQueryGraphsFromService(URI uri) {
        return QueryFactory.create(String.format("""
                           SELECT DISTINCT ?g WHERE { SERVICE <%s> {
                             SELECT DISTINCT ?g WHERE {GRAPH ?g {?s ?p ?o}}
                           } }
                           """, uri));
    }

    /**
     * @param uri The service to summarize.
     * @return The set of graphs served by the service.
     */
    public static Set<String> getGraphsFromService(URI uri) {
        Query getGraphsQuery = getQueryGraphsFromService(uri);

        try (QueryExecution qexec = QueryExecutionFactory.create(getGraphsQuery, DatasetFactory.empty())) {
            ResultSet results = qexec.execSelect();
            Set<String> onlyGraphs = new HashSet<>();
            while (results.hasNext()) {
                QuerySolution result = results.nextSolution();
                onlyGraphs.add(result.get("?g").toString());
            }
            return onlyGraphs;
        } catch (Exception e) {
            System.err.println("Could not get graphs from " + uri);
        }
        return null;
    }


    /**
     * @param uri The service uri.
     * @param graph The graph from the service uri.
     * @return The triples from the targeted graph at targeted uri.
     */
    public static List<Triple> getSPOFromServiceGraph (URI uri, String graph) {
        Query getGraphsQuery = QueryFactory.create(String.format("""
                           SELECT DISTINCT ?s ?p ?o WHERE { SERVICE <%s> {
                             GRAPH <%s> { ?s ?p ?o }
                           } }
                           """, uri, graph));

        try (QueryExecution qexec = QueryExecutionFactory.create(getGraphsQuery, DatasetFactory.empty())) {
            ResultSet results = qexec.execSelect();
            List<Triple> spos = new ArrayList<>();
            while (results.hasNext()) {
                QuerySolution result = results.nextSolution();
                spos.add(Triple.create(result.get("?s").asNode(), result.get("?p").asNode(), result.get("?o").asNode()));
            }
            return spos;
        } catch (Exception e) {
            System.err.println("Could not get SPO for graph " + graph + " from " + uri);
        }
        return null;
    }

}
