package fr.gdd.fedivers;

import org.apache.jena.sparql.core.Var;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;

/**
 * A utility function that provide unique variable names based on
 * a prefix set at construction time. This is useful to create
 * unique identifier with high probability when the caller has
 * no global access to a unique identifier.
 */
public class RandomVarProvider implements Supplier<Var> {

    final static Random rng = new Random(42);
    final Set<Var> allocated = new HashSet<>();
    final String prefix;

    public RandomVarProvider(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public Var get() {
        Var newVar = tryAlloc();
        while (!allocated.add(newVar)) {
            newVar = tryAlloc();
        }
        return newVar;
    }

    private Var tryAlloc() {
        return Var.alloc(prefix + rng.nextInt(0, Integer.MAX_VALUE));
    }
}
