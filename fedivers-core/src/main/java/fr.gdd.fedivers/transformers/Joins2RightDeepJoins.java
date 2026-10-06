package fr.gdd.fedivers.transformers;

import fr.gdd.jena.utils.FlattenUnflatten;
import fr.gdd.jena.visitors.ReturningOpBaseVisitor;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.op.OpJoin;

import java.util.List;

public class Joins2RightDeepJoins extends ReturningOpBaseVisitor {

    @Override
    public Op visit(OpJoin join) {
        List<Op> joins = FlattenUnflatten.flattenJoin(join);
        joins = joins.stream().map(this::visit).toList();
        return FlattenUnflatten.unflattenJoin(joins, false);
    }
}
