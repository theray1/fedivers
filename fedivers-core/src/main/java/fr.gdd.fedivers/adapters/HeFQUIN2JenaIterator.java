package fr.gdd.fedivers.adapters;

import fr.gdd.fedivers.FediversContext;
import org.apache.jena.atlas.io.IndentedWriter;
import org.apache.jena.graph.Node;
import org.apache.jena.query.Query;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.OpAsQuery;
import org.apache.jena.sparql.engine.QueryIterator;
import org.apache.jena.sparql.engine.binding.Binding;
import org.apache.jena.sparql.serializer.SerializationContext;
import se.liu.ida.hefquin.engine.HeFQUINEngine;
import se.liu.ida.hefquin.engine.HeFQUINEngineBuilder;
import se.liu.ida.hefquin.engine.HeFQUINEngineConfigReader;
import se.liu.ida.hefquin.engine.queryplan.utils.ExecutablePlanPrinter;
import se.liu.ida.hefquin.engine.queryplan.utils.LogicalPlanPrinter;
import se.liu.ida.hefquin.engine.queryplan.utils.PhysicalPlanPrinter;
import se.liu.ida.hefquin.engine.queryproc.QueryProcessor;
import se.liu.ida.hefquin.federation.access.FederationAccessManager;
import se.liu.ida.hefquin.federation.catalog.FederationCatalog;
import se.liu.ida.hefquin.federation.catalog.impl.FederationCatalogImpl;
import se.liu.ida.hefquin.federation.members.impl.SPARQLEndpointImpl;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Adapter for the HeFQUIN federation engine.
 */
public class HeFQUIN2JenaIterator implements QueryIterator {

    static HeFQUINEngine engine;
    final FederationCatalogImpl federation = new FederationCatalogImpl();
    ResultSet iterator;

    public HeFQUIN2JenaIterator(FediversContext context, Op query) {
        if (Objects.isNull(engine)) {
            for (Node e: context.getEndpoints()) {
                final String uri = e.getURI();
                federation.addMember(uri, new SPARQLEndpointImpl(uri, null));
            }
            // The custom builder exists because we want to modify the thread pool size
            engine = new HeFQUINEngineBuilder4Passage("MyDefaultConf.ttl", federation).build(); // ugly
        }

        Query asQuery = OpAsQuery.asQuery(query);
        try {
            iterator = engine.executeSelectQuery(asQuery).getResultSet();
        } catch (Exception e) {
            this.close();
        }
    }

    @Override public boolean hasNext() { return iterator.hasNext(); }
    @Override public Binding next() { return iterator.nextBinding(); }
    @Override public Binding nextBinding() { return this.next(); }
    @Override public void close() { if (Objects.nonNull(iterator))  iterator.close(); }
    @Override public void cancel() { this.close(); }
    @Override public void output(IndentedWriter out, SerializationContext sCxt) { }
    @Override public String toString(PrefixMapping pmap) { return ""; }
    @Override public void output(IndentedWriter out) { }


    /**
     * A slightly cleaner way to instantiate HeFQUIN.
     * TODO try to instantiate LogicalOpMultiwayUnion and LogicalOpMultiwayJoin
     *      before going in.
     */
    public static class HeFQUINEngineBuilder4Passage extends HeFQUINEngineBuilder {

        final FederationCatalog catalog;
        final Model config;

        public HeFQUINEngineBuilder4Passage(String configPath, FederationCatalog catalog) {
            this(RDFDataMgr.loadModel(configPath), catalog);
        }

        public HeFQUINEngineBuilder4Passage(Model config, FederationCatalog catalog) {
            this.catalog = catalog;
            this.config = config;
        }

        @Override
        public HeFQUINEngine build() {
            final HeFQUINEngineConfigReader.Context ctx = new HeFQUINEngineConfigReader.Context() {
                public ExecutorService getExecutorServiceForFederationAccess() { return Executors.newFixedThreadPool(10); }
                public ExecutorService getExecutorServiceForPlanTasks() { return Executors.newFixedThreadPool(10); }
                public FederationCatalog getFederationCatalog() { return catalog; }
                public boolean isExperimentRun() { return false; }
                public boolean skipExecution() { return false; }
                public LogicalPlanPrinter getSourceAssignmentPrinter() { return null; }
                public LogicalPlanPrinter getLogicalPlanPrinter() { return null; }
                public PhysicalPlanPrinter getPhysicalPlanPrinter() { return null; }
                public ExecutablePlanPrinter getExecutablePlanPrinter() { return null; }
            };

            final HeFQUINEngineConfigReader confReader = new HeFQUINEngineConfigReader();
            final FederationAccessManager fedAccessMgr = confReader.readFederationAccessManager(config, ctx);
            final QueryProcessor qProc = confReader.readQueryProcessor(config, ctx, fedAccessMgr);

            // integrate the engine into the Jena/ARQ machinery
            integrateEngineIntoJena(qProc);
            return new HeFQUINEngine4Passage(fedAccessMgr, qProc);
        }
    }

    public static class HeFQUINEngine4Passage extends HeFQUINEngine { // because the constructor is protected

        protected HeFQUINEngine4Passage(FederationAccessManager fedAccessMgr, QueryProcessor qProc) {
            super(fedAccessMgr, qProc);
        }
    }

}
