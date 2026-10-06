package fr.gdd.fedivers.adapters;

import fr.gdd.fedivers.FediversContext;
import org.apache.jena.atlas.io.IndentedWriter;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Node_Literal;
import org.apache.jena.graph.Node_URI;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.engine.QueryIterator;
import org.apache.jena.sparql.engine.binding.Binding;
import org.apache.jena.sparql.engine.binding.BindingBuilder;
import org.apache.jena.sparql.engine.binding.BindingFactory;
import org.apache.jena.sparql.serializer.SerializationContext;
import org.eclipse.rdf4j.federated.FedXConfig;
import org.eclipse.rdf4j.federated.FedXFactory;
import org.eclipse.rdf4j.federated.repository.FedXRepository;
import org.eclipse.rdf4j.federated.repository.FedXRepositoryConnection;
import org.eclipse.rdf4j.federated.structures.FedXTupleQuery;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.query.algebra.TupleExpr;
import org.eclipse.rdf4j.query.impl.MapBindingSet;
import org.eclipse.rdf4j.query.parser.ParsedTupleQuery;
import org.eclipse.rdf4j.query.resultio.TupleQueryResultParserRegistry;
import org.eclipse.rdf4j.query.resultio.sparqljson.SPARQLResultsJSONParserFactory;
import org.eclipse.rdf4j.query.resultio.sparqlxml.SPARQLResultsXMLParserFactory;
import org.eclipse.rdf4j.repository.sail.SailTupleQuery;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Adapter between RDF4J FedX query results and Apache Jena bindings as iterator.
 * Of course, this is not very efficient and should be avoided when there are a lot
 * of results. In particular, the static function called `createBinding` parses the results,
 * therefore being slow.
 */
public class FedXIterator2JenaIterator implements QueryIterator {

    static FedXRepository repository; /* shared globally by the application */
    FedXRepositoryConnection connection;
    TupleQueryResult iterator = null;
    FediversContext context;

    public static FedXRepository getFedX(int boundJoinBlockSize, int joinWorkerThreads, int unionWorkerThreads, int leftJoinWorkerThreads) {
        FedXRepository fedxRepo = FedXFactory.newFederation()
                .withConfig(new FedXConfig() // same as FedUP-experiment
                        .withBoundJoinBlockSize(boundJoinBlockSize) // 10+10 or 20+20 ?
                        .withJoinWorkerThreads(joinWorkerThreads)
                        .withUnionWorkerThreads(unionWorkerThreads)
                        .withLeftJoinWorkerThreads(leftJoinWorkerThreads)
                        .withEnableServiceAsBoundJoin(true)
                        .withEnforceMaxQueryTime(Integer.MAX_VALUE)
                        .withDebugQueryPlan(false))
                .withSparqlEndpoints(List.of()).create();
        // for the standalone jar, it seems mandatory to register these
        // result handlers beforehand here.
        TupleQueryResultParserRegistry.getInstance().add(new SPARQLResultsXMLParserFactory());
        TupleQueryResultParserRegistry.getInstance().add(new SPARQLResultsJSONParserFactory());

        return fedxRepo;
    }

    public FedXIterator2JenaIterator(FediversContext context, Op query) {
        this(context, query, 10, 10, 10, 10);
    }

    public FedXIterator2JenaIterator(FediversContext context, Op query, int boundJoinBlockSize, int joinWorkerThreads, int unionWorkerThreads, int leftJoinWorkerThreads) {
        this.context = context;

        TupleExpr queryAsFedX = new OpJena2OpFedX().visit(query);

        this.repository = FedXIterator2JenaIterator.getFedX(boundJoinBlockSize, joinWorkerThreads, unionWorkerThreads, leftJoinWorkerThreads);

        this.connection = repository.getConnection();
        TupleQuery tq = new FedXTupleQuery(new SailTupleQuery(new ParsedTupleQuery(queryAsFedX), this.connection));
        try {
            iterator = tq.evaluate();
        } catch (Exception e) {
            this.close();
        }
    }

    @Override
    public boolean hasNext() {
        if (Objects.isNull(iterator) || !connection.isOpen()) { return false; }
        try {
            if (iterator.hasNext()) {
                return true;
            } else {
                close();
                return false;
            }
        } catch (Exception e) {
            close();
            return false;
        }
    }

    @Override
    public Binding nextBinding() {
        if (Objects.isNull(iterator) || !connection.isOpen()) {
            throw new RuntimeException("Connection closed while attempting to get results.");
        }
        try {
            return createBinding(iterator.next());
        } catch (Exception e) {
            close();
            throw new RuntimeException(e.getMessage());
        }
    }

    @Override public Binding next() { return this.nextBinding(); }
    @Override public void cancel() { connection.close(); repository.shutDown(); }
    @Override public void close() { connection.close(); repository.shutDown(); }
    @Override public void output(IndentedWriter out, SerializationContext sCxt) {}
    @Override public String toString(PrefixMapping pmap) { return null; }
    @Override public void output(IndentedWriter out) {}

    /**
     * @param origin The RDF4J binding to convert.
     * @return An Apache Jena `Binding` that comes from an RDF4J binding.
     */
    public static Binding createBinding(BindingSet origin) {
        BindingBuilder builder = BindingFactory.builder();
        for (String name : origin.getBindingNames()) {
            org.eclipse.rdf4j.query.Binding value = origin.getBinding(name);
            // TODO unuglify and generalize, especially the literal part
            Node valueAsNode = null;
            if (value.getValue().isBNode()) {
                valueAsNode = NodeFactory.createBlankNode(value.getValue().stringValue());
            } else if (value.getValue().isIRI()) {
                valueAsNode = NodeFactory.createURI(value.getValue().stringValue());
            } else if (value.getValue().isLiteral()) {
                Optional<String> lang = ((Literal) value.getValue()).getLanguage();

                if (value.getValue().toString().contains(XSDDatatype.XSDinteger.getURI())) {
                    valueAsNode = NodeFactory.createLiteralDT(value.getValue().stringValue(), XSDDatatype.XSDinteger);
                } else if (value.getValue().toString().contains(XSDDatatype.XSDint.getURI())) {
                    valueAsNode = NodeFactory.createLiteralDT(value.getValue().stringValue(), XSDDatatype.XSDint);
                }else if (value.getValue().toString().contains(XSDDatatype.XSDdouble.getURI())) {
                    valueAsNode = NodeFactory.createLiteralDT(value.getValue().stringValue(), XSDDatatype.XSDdouble);
                } else if (value.getValue().toString().contains(XSDDatatype.XSDdateTime.getURI())) {
                    valueAsNode = NodeFactory.createLiteralDT(value.getValue().stringValue(), XSDDatatype.XSDdateTime);
                } else if (value.getValue().toString().contains(XSDDatatype.XSDdate.getURI())) {
                    valueAsNode = NodeFactory.createLiteralDT(value.getValue().stringValue(), XSDDatatype.XSDdate);
                } else if (lang.isPresent()) {
                    valueAsNode = NodeFactory.createLiteralLang(value.getValue().stringValue(), lang.get());
                } else {
                    valueAsNode = NodeFactory.createLiteralString(value.getValue().stringValue());
                }
            } else if (value.getValue().isResource() || value.getValue().isTriple()) {
                throw new UnsupportedOperationException("RDF4J to Jena Bindings with a resource or a triple.");
            }
            builder.add(Var.alloc(name), valueAsNode);
        }
        return builder.build();
    }

    /**
     * @param origin The RDF4J binding to convert.
     * @return An Apache Jena `Binding` that comes from an RDF4J binding.
     */
    public static BindingSet createBindingConverse(Binding origin) {
        final MapBindingSet converted = new MapBindingSet();
        final ValueFactory vf = SimpleValueFactory.getInstance();
        origin.forEach((v, n) -> {
            switch (n) {
                case Node_URI uri -> converted.addBinding(v.getVarName(), vf.createIRI(uri.getURI()));
                case Node_Literal lit -> converted.addBinding(v.getVarName(), vf.createLiteral(lit.getLiteralLexicalForm()));
                default -> throw new UnsupportedOperationException();
            }
        });
        return converted;
    }

}
