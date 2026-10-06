package fr.gdd.fedivers.optimizers;

import fr.gdd.jena.utils.FlattenUnflatten;
import fr.gdd.jena.visitors.ReturningOpVisitor;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.op.OpJoin;
import org.apache.jena.sparql.algebra.op.OpSequence;
import org.apache.jena.sparql.algebra.op.OpService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Deprecated // TODO test etc…
            // TODO all operators, maybe switch to a HefQUIN-like optimizer
public class ToExclusiveGroups implements ReturningOpVisitor<Op> {

    @Override
    public Op visit(OpJoin join) {
        List<Op> flattened = FlattenUnflatten.flattenJoin(join);
        List<OpService> services = flattened.stream().filter(o -> o instanceof OpService).map(o -> (OpService) o).toList();
        List<OpService> ordered = services.stream().sorted(Comparator.comparing(a -> a.getService().toString())).toList();

        // TODO possibly should check if there are joining variables…
        List<OpService> grouped = new ArrayList<>();
        OpService service = null;
        for (OpService opService : ordered) {
            if (Objects.isNull(service)) {
                service = opService;
            } else {
                if (service.getService().equals(opService.getService())) {
                    service = new OpService(service.getService(),
                            OpJoin.create(service.getSubOp(), opService.getSubOp()),
                            service.getSilent());
                } else {
                    grouped.add(service);
                    service = opService;
                }
            }
        }
        if (Objects.nonNull(service)) grouped.add(service);

        OpSequence sequence = OpSequence.create();
        grouped.forEach(sequence::add);
        // add the rest that where not services…
        flattened.stream().filter(o -> !(o instanceof OpService)).map(this::visit).forEach(sequence::add);

        return sequence;
    }

}
