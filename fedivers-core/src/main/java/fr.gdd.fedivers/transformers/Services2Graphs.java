package fr.gdd.fedivers.transformers;

import fr.gdd.jena.visitors.ReturningOpBaseVisitor;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.op.OpGraph;
import org.apache.jena.sparql.algebra.op.OpService;

/**
 * Each SERVICE is transformed into GRAPH to execute the query on the
 * targeted local graph database.
 */
public class Services2Graphs extends ReturningOpBaseVisitor {
    @Override  public Op visit(OpService req) { return new OpGraph(req.getService(), req.getSubOp()); }
}
