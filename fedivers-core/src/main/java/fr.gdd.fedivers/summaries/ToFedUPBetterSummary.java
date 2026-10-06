package fr.gdd.fedivers.summaries;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;

import java.net.URI;

/**
 * An attempt at creating more accurate summaries for FedUP where the
 * cross-domain entities would be less compressed.
 * It has shown good preliminary results, but it needs to be processed
 * automatically, which might prove difficult in the general case.
 * TODO iterative way to build such summaries, without manually putting
 *      the authority of CD entities.
 */
@Deprecated
public class ToFedUPBetterSummary extends ToFedUPSummary {

    public ToFedUPBetterSummary(int modulo) {
        super(modulo);
    }

    @Override
    protected Node processURI(URI uri) {
        if (uri.getAuthority().contains("www4.wiwiss.fu-berlin.de")) {
            int hashcode = Math.abs(uri.toString().hashCode());
            if (modulo == 0 || modulo == 1) {
                return NodeFactory.createURI(uri.getScheme() + "://" + uri.getAuthority());
            } else {
                return NodeFactory.createURI(uri.getScheme() + "://" + uri.getAuthority() + "/" + (hashcode % modulo));
            }
        } else {
            // we assume same domain
            return NodeFactory.createURI(uri.getScheme() + "://" + uri.getAuthority());
        }
    }
}
