package fr.gdd.fedivers.transformers;

import fr.gdd.jena.utils.FlattenUnflatten;
import fr.gdd.jena.visitors.ReturningOpBaseVisitor;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.op.OpJoin;

import java.util.List;

public class Joins2Sequences extends ReturningOpBaseVisitor {

    @Override
    public Op visit(OpJoin join) {
        List<Op> flattened = FlattenUnflatten.flattenJoin(join);
        flattened = flattened.stream().map(this::visit).toList();
        return FlattenUnflatten.flattenJoin2Sequence(flattened);
    }
}
