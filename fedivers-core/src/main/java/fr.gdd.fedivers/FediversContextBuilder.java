package fr.gdd.fedivers;

import fr.gdd.fedivers.summaries.IGraph2SourceDataset;
import org.apache.jena.query.Dataset;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.RDFParserBuilder;
import org.apache.jena.sparql.engine.ExecutionContext;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class FediversContextBuilder {

    public boolean shouldMaterializeSources = true;
    public boolean shouldAsk = true;
    public boolean shouldPushDownUnaries = false;

    // Visitor used to transform nodes from queries
    public IGraph2SourceDataset query2summary;

    // Class from which getGraph2Source is called to modify output federated queries
    // in order to account for summary vs real world discrepancies
    public IGraph2SourceDataset graph2source;

    public ExecutionContext executionContext;
    public Dataset summary;

    public FediversContext build() {

        if(Objects.isNull(summary)) {
            throw new RuntimeException("summary is null");
        }

        if(Objects.isNull(query2summary)) {
            throw new RuntimeException("query2summary is null");
        }

        if(Objects.isNull(graph2source)) {
            throw new RuntimeException("graph2source is null");
        }

        ExecutionContext executionContext = ExecutionContext.create(summary.asDatasetGraph());

        return new FediversContext(
                shouldMaterializeSources,
                shouldAsk,
                shouldPushDownUnaries,
                query2summary,
                graph2source,
                new ConcurrentHashMap<>(),
                executionContext,
                summary);
    }

    public FediversContextBuilder setShouldMaterializeSources(boolean shouldMaterializeSources) {
        this.shouldMaterializeSources = shouldMaterializeSources;
        return this;
    }

    public FediversContextBuilder setShouldAsk(boolean shouldAsk) {
        this.shouldAsk = shouldAsk;
        return this;
    }

    public FediversContextBuilder setShouldPushDownUnaries(boolean shouldPushDownUnaries) {
        this.shouldPushDownUnaries = shouldPushDownUnaries;
        return this;
    }

    public FediversContextBuilder setQuery2summary(IGraph2SourceDataset query2summary) {
        this.query2summary = query2summary;
        return this;
    }

    public FediversContextBuilder setGraph2source(IGraph2SourceDataset graph2source) {
        this.graph2source = graph2source;
        return this;
    }

    public FediversContextBuilder setSummary(String summaryPath) {
        RDFParserBuilder builder = RDFParser.create();
        builder.source(summaryPath);
        this.summary = builder.build().toDataset();
//        this.summary = DatasetFactory.create(List.of(summaryPath));
//        this.summary = DatasetFactory.assemble(summaryPath);
//        this.summary = DatasetFactory.create();

        System.out.println("summary path: " + summaryPath);
        this.summary.listNames().forEachRemaining(System.out::println);

        return this;
    }
}
