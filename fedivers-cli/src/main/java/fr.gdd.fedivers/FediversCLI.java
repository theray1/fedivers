package fr.gdd.fedivers;

import fr.gdd.fedivers.adapters.FedXIterator2JenaIterator;
import fr.gdd.fedivers.adapters.HeFQUIN2JenaIterator;
import fr.gdd.fedivers.sources.ToFederatedQueryFactory;
import fr.gdd.fedivers.sources.UOJ2JOU;
import fr.gdd.fedivers.summaries.IGraph2SourceDataset;
import fr.gdd.fedivers.summaries.IdentityDataset;
import fr.gdd.fedivers.summaries.ToFedUPSummary;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.riot.rowset.RowSetWriter;
import org.apache.jena.riot.rowset.rw.RowSetWriterJSON;
import org.apache.jena.sparql.algebra.*;
import org.apache.jena.sparql.algebra.optimize.TransformReorder;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.engine.QueryIterator;
import org.apache.jena.sparql.engine.binding.BindingRoot;
import org.apache.jena.sparql.engine.main.QueryEngineMain;
import org.apache.jena.sparql.exec.RowSet;
import org.apache.jena.sparql.exec.RowSetStream;
import org.apache.jena.sparql.util.Context;
import picocli.CommandLine;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;

import static fr.gdd.fedivers.CLIUtils.getStrategy;
import static org.apache.jena.riot.resultset.ResultSetLang.RS_JSON;

@CommandLine.Command(
        name = "source-assign",
        version = "0.2.0",
        description = "Utility for creating federated query with decomposition and source assignment.",
        usageHelpAutoWidth = true, // adapt to the screen size instead of new line on 80 chars
        sortOptions = false,
        sortSynopsis = false,
        showDefaultValues = true
)
public class FediversCLI {

    // private final static Logger log = LoggerFactory.getLogger(SourceAssignCLI.class); // (error when multiple loggers)

    @CommandLine.ArgGroup(multiplicity = "1")
    ExclusiveQuery exclusiveQuery;
    static class ExclusiveQuery {
        @CommandLine.Option(
                order = 2,
                names = {"-q", "--query"},
                paramLabel = "SPARQL",
                description = "The SPARQL query to execute.")
        String queryAsString;

        @CommandLine.Option(
                order = 2,
                names = {"-f", "--file"},
                paramLabel = "path/to/query",
                description = "The file containing the SPARQL query to execute.")
        String queryFile;
    }

    @CommandLine.Option(
            order = 2,
            names = {"-o", "--output"},
            paramLabel = "path/to/a/sparql/file",
            description = "The output file to write the query to. When not specified, printed to the standard output."
    )
    String outputFile;

    @CommandLine.ArgGroup(multiplicity = "1", order = 2)
    ExclusiveType exclusiveType;
    static class ExclusiveType {
        @CommandLine.Option(
                names = "--type",
                paramLabel = "jou|ouj|uoj2jou",
                defaultValue = "jou",
                description = """
                    Should it transform to a join-over-union (jou) or a union-over-join (ouj) federated query."""
        )
        String type = "jou";

        @CommandLine.Option(
                names = {"--no-sources"},
                description = """
                    Creates a decomposition of SERVICES without associating sources to them.""")
        boolean no_sources = false;
    }

    @CommandLine.Option(
            order = 3,
            names = {"-s", "--summary"},
            paramLabel = "path/to/blazegraph",
            description = """
                    Path to the summary dataset. The path is to a Blazegraph .properties file.""")
    String summaryPath;

    @CommandLine.Option(
            order = 4,
            names = {"-t", "--strategy"},
            paramLabel = "truncate|fedup|id", // TODO id
            defaultValue = "truncate (3)",
            description = """
                    The summarizing strategy to use for transforming the input query.""")
    String strategy = "trunc";

    @CommandLine.Option(
            order = 5,
            names = {"--endpoint-prefix"},
            paramLabel = "http://localhost:9999/blazegraph/sparql?default-graph-uri=",
            description = """
                    A prefix to append the graph to in order to obtain the endpoint URI.""")
    String endpointPrefix = "";

    @CommandLine.Option(
            order = 5,
            names = {"--asks"},
            description = """
                    Determines if asks should be used to more accurately determine provenance of triples with constants.""")
    boolean asks = false;

    @CommandLine.Option(
            order = 7,
            names = "--order",
            paramLabel = "variables-counting|none",
            defaultValue = "none",
            description = "Modify the join-ordering."
    )
    String join_ordering = "none";

    @CommandLine.Option(
            order = 8,
            names = "--engine",
            paramLabel = "fedx|hefquin|jena|none",
            defaultValue = "none",
            description = "Run the generated query with a federation engine."
    )
    String engine = "none";

    @picocli.CommandLine.Option(
            order = 9,
            names = {"--block-size", },
            description = "The size of the mapping set sent in every bound join request.")
    public int blockSize = 10;

    @picocli.CommandLine.Option(
            order = 10,
            names = {"--union-workers", },
            description = "Number of workers for union operators.")
    public int unionWorkers = 10;

    @picocli.CommandLine.Option(
            order = 11,
            names = {"--join-workers", },
            description = "Number of workers for join operators.")
    public int joinWorkers = 10;

    @picocli.CommandLine.Option(
            order = 12,
            names = {"--left-join-workers", "--lj-workers"},
            description = "Number of workers for left join operators.")
    public int leftJoinWorkers = 10;

    @picocli.CommandLine.Option(
            order = 16,
            names = {"--json"},
            paramLabel = "json",
            description = """
                    Print results in JSON.""")
    public Boolean json = false;

    @picocli.CommandLine.Option(
            order = 17,
            names = {"--stats"},
            paramLabel = "stats",
            description = """
                    Print statistics to the specified file (JSON)"""
    )
    public String stats;

    @picocli.CommandLine.Option(
            order = 18,
            names = {"--no-mdcd-unary-push-down"},
            paramLabel = "mdcdunarypushdown",
            description = """
                    Prevents "easy" unary operators push downs during MDCD.""")
    public Boolean no_unary_push_down = false;

    @CommandLine.Option(
            order = Integer.MAX_VALUE, // last
            names = {"-h", "--help"},
            usageHelp = true,
            description = "Display this help message.")
    boolean usageHelpRequested;

    public static void main(String[] args) throws Exception {
        long totalStart = System.currentTimeMillis();

        FediversCLI options = new FediversCLI();
        try {
            new CommandLine(options).parseArgs(args);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            CommandLine.usage(options, System.out);
            System.exit(CommandLine.ExitCode.USAGE);
        }
        if (options.usageHelpRequested) {
            CommandLine.usage(options, System.out);
            System.exit(CommandLine.ExitCode.OK);
        }

        if (Objects.nonNull(options.exclusiveQuery.queryFile)) {
            Path queryPath = Path.of(options.exclusiveQuery.queryFile);
            try {
                options.exclusiveQuery.queryAsString = Files.readString(queryPath);
            } catch (IOException e) {
                System.out.println("Error: could not read " + queryPath + ".");
                System.exit(CommandLine.ExitCode.SOFTWARE);
            }
        }

        Set<Var> vars = OpVars.visibleVars(Algebra.compile(QueryFactory.create(options.exclusiveQuery.queryAsString)));

        if (Objects.isNull(options.summaryPath)) { throw new IllegalArgumentException("summaryPath must be specified."); }

        final FediversContextBuilder builder = new FediversContextBuilder();
        builder.setSummary(options.summaryPath);
        // TODO : clean this up
        IGraph2SourceDataset strategy = getStrategy(options.strategy);
        if(!options.endpointPrefix.isEmpty()) {
            if (strategy instanceof ToFedUPSummary tfs) tfs.setGraph2Source("^(.+?)?$", options.endpointPrefix + "$1");
            if (strategy instanceof IdentityDataset id) id.setGraph2Source("^(.+?)?$", options.endpointPrefix + "$1"); // TODO why though?
        }

        builder.setGraph2source(strategy);
        builder.setQuery2summary(strategy);
        if (options.asks) { builder.setShouldAsk(true); } else { builder.setShouldAsk(false); }
        if (options.no_unary_push_down) { builder.setShouldPushDownUnaries(false); } else { builder.setShouldPushDownUnaries(true); }
        builder.setShouldMaterializeSources(true); // for now, always materialize
        Op query = Algebra.compile(QueryFactory.create(options.exclusiveQuery.queryAsString));
        try (final FediversContext context = builder.build()) {  // thread pool closed after finalize.
            switch (options.join_ordering.toLowerCase()) {
                case "variables-counting" -> query = Transformer.transform(new TransformReorder(), query);
                case "none" -> {/* nothing */}
                default -> throw new UnsupportedOperationException("The join-ordering strategy is not recognized.");
            }

            // ACTUAL EXECUTION:
            long startBeforeExecution = System.currentTimeMillis(); // moved the measure to the actual execution
            Op transformed = null;
            if (options.exclusiveType.no_sources) {
                transformed = ToFederatedQueryFactory.MDCDServices(context, query);
            } else {
                switch (options.exclusiveType.type.toLowerCase()) {
                    case "jou" -> transformed = ToFederatedQueryFactory.MDCDJoinOverUnions(context, query);
                    case "uoj" -> transformed = ToFederatedQueryFactory.MDCDUnionOverJoins(context, query);
                    case "uoj2jou" -> transformed = new UOJ2JOU().visit(query);
                }
            }

            System.err.printf("Assigned sources to query %s in %s ms.%n", Objects.nonNull(options.exclusiveQuery.queryFile) ?
                    options.exclusiveQuery.queryFile : "", System.currentTimeMillis() - startBeforeExecution);

            if (!options.engine.equalsIgnoreCase("none")) {
                // will be executed so we display the query in stderr before
                String outputQueryAsString = OpAsQuery.asQuery(transformed).toString(); // if null, then throws here
                System.err.println(outputQueryAsString);
            }

            QueryIterator results = switch (options.engine.toLowerCase()) {
                case "fedx" -> options.executeWithFedX(context, transformed, options.blockSize, options.joinWorkers, options.unionWorkers, options.leftJoinWorkers);
                case "jena" -> options.executeWithJena(transformed);
                case "hefquin" -> options.executeWithHeFQUIN(context, transformed);
                case "none" -> null;
                default -> throw new UnsupportedOperationException("The engine " + options.engine + " is not recognized.");
            };

            if (options.engine.equalsIgnoreCase("none")) {
                String outputQueryAsString = OpAsQuery.asQuery(transformed).toString(); // if null, then throws here

                if (Objects.nonNull(options.outputFile)) {
                    BufferedWriter writer = new BufferedWriter(new FileWriter(options.outputFile));
                    writer.write(outputQueryAsString);
                    writer.close();
                } else {
                    System.out.println(outputQueryAsString);
                }
            } else if (Objects.nonNull(results)) {
                OutputStream out = Objects.nonNull(options.outputFile) ?
                        new FileOutputStream(options.outputFile) :
                        System.out;

                if(options.json) {
                    RowSet rs = RowSetStream.create(new ArrayList<>(vars), results);
                    RowSetWriter rsw = RowSetWriterJSON.factory.create(RS_JSON);
                    rsw.write(out, rs, Context.emptyContext());
                } else {
                    if (Objects.nonNull(options.outputFile)) {
                        BufferedWriter writer = new BufferedWriter(new FileWriter(options.outputFile));
                        results.forEachRemaining(r -> {
                            try { writer.write(r.toString()); } catch (IOException e) { throw new RuntimeException(e); }
                        });
                        writer.close();
                    } else {
                        results.forEachRemaining(r -> System.out.println(r.toString()));
                    }
                }

                results.close();
            }
        }
    }

    public QueryIterator executeWithFedX(FediversContext context, Op query, int boundJoinBlockSize, int joinWorkerThreads, int unionWorkerThreads, int leftJoinWorkerThreads) {
        return new FedXIterator2JenaIterator(context, query, boundJoinBlockSize, joinWorkerThreads, unionWorkerThreads, leftJoinWorkerThreads);
    }

    public QueryIterator executeWithJena(Op queryAsJena) {
        QueryEngineMain engine = new QueryEngineMain(queryAsJena, DatasetFactory.empty().asDatasetGraph(), BindingRoot.create(), new Context());
        return engine.eval(queryAsJena, DatasetFactory.empty().asDatasetGraph(), BindingRoot.create(), new Context());
    }

    public QueryIterator executeWithHeFQUIN(FediversContext context, Op query) {
        return new HeFQUIN2JenaIterator(context, query);
    }

}
