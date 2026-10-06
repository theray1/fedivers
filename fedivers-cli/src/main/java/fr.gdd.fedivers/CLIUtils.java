package fr.gdd.fedivers;

import fr.gdd.fedivers.summaries.IGraph2SourceDataset;
import fr.gdd.fedivers.summaries.IdentityDataset;
import fr.gdd.fedivers.summaries.ToFedUPSummary;
import fr.gdd.fedivers.summaries.TruncateOnSuffix;

public class CLIUtils {
    public static IGraph2SourceDataset getStrategy(String strategy) {
        return switch (strategy) {
            case "trunc" -> new TruncateOnSuffix();
            case "fedup" -> new ToFedUPSummary();
            default -> new IdentityDataset();
        };
    }
}
