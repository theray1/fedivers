package fr.gdd.fedivers.sources;

import fr.gdd.fedivers.FediversContext;
import fr.gdd.fedivers.RandomVarProvider;
import fr.gdd.fedivers.transformers.Reduced2DistributedUnions;
import fr.gdd.fedivers.transformers.Services2Graphs;
import fr.gdd.jena.utils.OpCloningUtil;
import fr.gdd.jena.visitors.ReturningOpVisitor;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.OpVars;
import org.apache.jena.sparql.algebra.op.*;
import org.apache.jena.sparql.core.Var;

import java.util.Set;

/**
 * The input of this visitor is a user SPARQL query.
 * For it to be federated, we nest everything in `SERVICE ?gx`.
 * *
 * This is the first step to the building of an all-inclusive federated
 * query where the service query is shipped with the source assignment
 * query.
 * *
 * Optionally, we process a matrix[TP][TP] -> type where the type corresponds
 * to the fact that pairwise, the join is cross-domain meaning that it should
 * be executed on at least two endpoints to be correct.
 * False means that it's a SD or MD query.
 */
public class ToServices implements ReturningOpVisitor<ToServices.TransformedWithMatrix> {

    final FediversContext context;
    final Boolean shouldFactorizeMD;
    final RandomVarProvider vng = new RandomVarProvider("__g");
    public record TransformedWithMatrix (Op op, MatrixMDCD matrix) {};

    public ToServices(FediversContext context) { this(context, true); }

    public ToServices(FediversContext context, Boolean shouldFactorizeMD) {
        this.context = context;
        this.shouldFactorizeMD = shouldFactorizeMD;
    }

    public Op create(Op op) {
        // Unions are quite annoying to handle. Instead, we transform the
        // query so they are distributed.
        final Op transformed = new Reduced2DistributedUnions().visit(op);
        return this.visit(transformed).op;
    }

    /* ************************************************************************************ */
    // Handling unary operators θ that ensure this property θ(Union(Pr,Pl))≡Union(θ(Pr),θ(Pl))

    @Override
    public TransformedWithMatrix visit(OpProject project) {
        TransformedWithMatrix so = visit(project.getSubOp());
        if (shouldFactorizeMD && so.op instanceof OpService innerService && context.shouldPushDownUnaries) {
            // project is pushed inside the service.
            OpProject projectClone = OpCloningUtil.clone(project, innerService.getSubOp());

            if (innerService.getService().isVariable()) {
                projectClone.getVars().add((Var) innerService.getService());
            }

            return new TransformedWithMatrix(
                    OpCloningUtil.clone(innerService, projectClone),
                    so.matrix);
        }

        Op graphs = new Services2Graphs().visit(so.op);
        Set<Var> graphVariables = OpVars.mentionedVarsByPosition(graphs).get(0);
        graphVariables.addAll(project.getVars());

        // otherwise, Project simply wraps the graph pattern below
        return new TransformedWithMatrix(new OpProject(so.op, graphVariables.stream().toList()), so.matrix);
    }

    @Override
    public TransformedWithMatrix visit(OpFilter filter) {
        // TODO actually, if below is MD, then we can put the filter
        //      inside the req as well.
        // TODO check if existential filter, since pattern in the exists
        //      may be cross-domain…
        TransformedWithMatrix so = visit(filter.getSubOp());
        if (shouldFactorizeMD && so.op instanceof OpService innerService && context.shouldPushDownUnaries) {
            // filter is pushed inside the service.
            return new TransformedWithMatrix(
                    OpCloningUtil.clone(innerService, OpCloningUtil.clone(filter, innerService.getSubOp())),
                    so.matrix);
        }
        // otherwise, Filter simply wraps the graph pattern, the physical optimizer
        // will be in charge of deciding where to push it further.
        return new TransformedWithMatrix(OpCloningUtil.clone(filter, so.op), so.matrix);
    }

    @Override
    public TransformedWithMatrix visit(OpExtend extend) {
        TransformedWithMatrix so = visit(extend.getSubOp());
        if (shouldFactorizeMD && so.op instanceof OpService innerService && context.shouldPushDownUnaries) {
            // extend is pushed inside the service.
            return new TransformedWithMatrix(
                    OpCloningUtil.clone(innerService, OpCloningUtil.clone(extend, innerService.getSubOp())),
                    so.matrix);
        }
        // otherwise, extends simply wraps.
        return new TransformedWithMatrix(OpCloningUtil.clone(extend, so.op), so.matrix);
    }


    /* *********** other unary operators do not enjoy such easy transformation *********** */

    @Override
    public TransformedWithMatrix visit(OpDistinct distinct) {
        // Distinct of MD is not the union of Distinct, so we cannot put it inside.
        // TODO when composed with other operators, it can be further improved.
        //      Related: https://github.com/Chat-Wane/passage-secret/issues/34#issuecomment-4798070185
        TransformedWithMatrix so = visit(distinct.getSubOp());
        return new TransformedWithMatrix(new OpDistinct(so.op), so.matrix);
    }

    @Override
    public TransformedWithMatrix visit(OpSlice slice) {
        // Slice does not make a lot of sense in federation,
        // we keep it there, to be performed by the federation engine.
        TransformedWithMatrix so = visit(slice.getSubOp());
        return new TransformedWithMatrix(OpCloningUtil.clone(slice, so.op), so.matrix);
    }

    @Override
    public TransformedWithMatrix visit(OpGroup group) {
        // GroupBy MD is not the union of GroupBys. However, there could be optimizations
        // such as count becomes sum of counts, etc.
        TransformedWithMatrix so = visit(group.getSubOp());
        return new TransformedWithMatrix(OpCloningUtil.clone(group, so.op), so.matrix);
    }

    @Override
    public TransformedWithMatrix visit(OpOrder order) {
        // Cannot be put inside since it requires all bindings to order them.
        TransformedWithMatrix so = visit(order.getSubOp());
        return new TransformedWithMatrix(OpCloningUtil.clone(order, so.op), so.matrix);
    }

    /* ***************************************************************************** */

    @Override
    public TransformedWithMatrix visit(OpTriple triple) {
        return new TransformedWithMatrix(new OpService(vng.get(), triple, true),
                new MatrixMDCD(context).add(triple));
    }

    @Override
    public TransformedWithMatrix visit(OpBGP bgp) {
        // otherwise, we build the groups that are identified as multi-domain.
        final MatrixMDCD matrix = new MatrixMDCD(context).add(bgp);
        if (shouldFactorizeMD) { matrix.execute(); }
        return new TransformedWithMatrix(matrix.asSequenceOfServices(), matrix);
    }

    @Override
    public TransformedWithMatrix visit(OpJoin join) {
        final TransformedWithMatrix left = visit(join.getLeft());
        final TransformedWithMatrix right = visit(join.getRight());
        // TODO should examine each component of left and right to see if they
        //      can be merged…
        //      Then the mechanism should be used in LeftJoins as well
        if (shouldFactorizeMD && right.op instanceof OpService rightService) {
            // TODO, on the left, only one is required to be MD with the right.
            if (left.op instanceof OpService leftService) {
                MatrixMDCD merged = left.matrix.merge(right.matrix);
                merged.execute();
                if (merged.getWeakComponentsOnMD().size() == 1) {
                    return new TransformedWithMatrix(new OpService(leftService.getService(),
                            OpJoin.create(leftService.getSubOp(), rightService.getSubOp()),
                            leftService.getSilent()),
                            merged
                    );
                }
            }
        }

        return new TransformedWithMatrix(OpJoin.create(left.op, right.op), left.matrix.merge(right.matrix));
    }

    @Override
    public TransformedWithMatrix visit(OpUnion union) {
        // Union is a special case where Union(MD(P1), MD(P2)) can be written:
        // - (1) Union(MD(P1), MD(P2)) ; or
        // - (2) MD(Union(P1,P2)).
        // We intially chose (1) as it seemed to remove Req, but they are actually
        // kept since the corresponding VALUES sources is the sum of both sides.
        // We went from:
        // ```
        // SELECT ?s ?o ?x ?y WHERE {
        //    { SELECT DISTINCT ?g1 {GRAPH ?g1 { ?s <p1> ?o }} { SERVICE ?g1 { ?s <p1> ?o } }
        //    UNION { SELECT DISTINCT ?g2 {GRAPH ?g2 { ?x <p2> ?y }} { SERVICE ?g2 { ?x <p2> ?y } } }
        // ```
        // to:
        // ```
        // SELECT ?s ?o ?x ?y WHERE {
        //    { SELECT DISTINCT ?g1 { GRAPH ?g1 { {?s <p1> ?o} UNION {?x <p2> ?y} }
        //      SERVICE ?g1 { { ?s <p1> ?o } UNION { ?x <p2> ?y } } } }
        // ```
        // Even when ?g1 ?g2 CD, writing it as MD makes it match ?g1 and ?g2 individually
        //
        // NOW: unless both sides are always evaluated on the same source (which means that it's SD),
        // we keep them separated (2) to keep the CD nature at the federation engine level.
        final TransformedWithMatrix left = this.visit(union.getLeft());
        final TransformedWithMatrix right = this.visit(union.getRight());

        if  (shouldFactorizeMD && left.op instanceof OpService leftService && right.op instanceof OpService rightService) {
            // TODO check if SD, i.e. MD and |services| < 2
            //      the issue is that it proves costly in general to check that…
            if (false) { // TODO if proven possible and not time wasting.
                return new TransformedWithMatrix(
                        new OpService(leftService.getService(),
                                OpUnion.create(leftService.getSubOp(), rightService.getSubOp()),
                                leftService.getSilent()),
                        left.matrix.merge(right.matrix)); // remember: the matrix is a matrix of joins
            }
        }
        // otherwise, the union remains a union
        return new TransformedWithMatrix(OpUnion.create(left.op, right.op), left.matrix.merge(right.matrix));
    }

    @Override
    public TransformedWithMatrix visit(OpLeftJoin lj) {
        // TODO an analysis has to be made between the left and the right
        //      The suspected outcome would be that only when Right is fully MD
        //      then if there exists a mandatory MD that MD-ifies with the right,
        //      we can push the OPT in there.
        final TransformedWithMatrix left = this.visit(lj.getLeft());
        final TransformedWithMatrix right = this.visit(lj.getRight());
        if (shouldFactorizeMD && right.op instanceof OpService rightService) {
            // TODO, on the left, only one is required to be MD with the right.
            if (left.op instanceof OpService leftService) {
                MatrixMDCD merged = left.matrix.merge(right.matrix);
                merged.execute();
                if (merged.getWeakComponentsOnMD().size() == 1) {
                    return new TransformedWithMatrix(new OpService(leftService.getService(),
                            OpCloningUtil.clone(lj, leftService.getSubOp(), rightService.getSubOp()),
                            leftService.getSilent()),
                            merged
                            );
                }
            }
            // This is a single qp meaning that the sub-query is MD.
            // TODO Perform an additional check to see if there exists a
            //      MD in the mandatory part to join with, so it can be pushed up.
        }
        // otherwise, it's kept as CD:
        return new TransformedWithMatrix(OpCloningUtil.clone(lj, left.op, right.op), left.matrix.merge(right.matrix));
    }
    
}
