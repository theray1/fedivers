package fr.gdd.fedivers.transformers;

import fr.gdd.jena.visitors.ReturningOpBaseVisitor;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.op.OpQuad;
import org.apache.jena.sparql.algebra.op.OpQuadPattern;
import org.apache.jena.sparql.algebra.op.OpService;

/**
 * Important in some situation: `OpAsQuery` does not support `OpQuad`, but it
 * supports `OpQuadPattern`, so we transform each `OpQuad` into an `OpQuadPattern` of
 * 1 pattern.
 */
public class Quad2Pattern extends ReturningOpBaseVisitor {

    @Override
    public Op visit(OpQuad quad) {
        return new OpQuadPattern(quad.getQuad().getGraph(), quad.asQuadPattern().getBasicPattern());
    }

    @Override
    public Op visit(OpService service) {
        return new OpService(service.getService(), this.visit(service.getSubOp()), service.getServiceElement(), service.getSilent());
    }

}
