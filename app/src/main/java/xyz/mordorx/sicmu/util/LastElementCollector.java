package xyz.mordorx.sicmu.util;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

/** Usage: new LastElementCollector<>();
  */
public class LastElementCollector<T> implements Collector<T, LastElementCollector.Container<T>, Optional<T>> {
    public LastElementCollector() {

    }

    static class Container<T> {
        T last = null;
    }

    @Override
    public Supplier<Container<T>> supplier() {
        return Container::new;
    }

    @Override
    public BiConsumer<Container<T>, T> accumulator() {
        return (acc, elem) -> acc.last = elem;
    }

    @Override
    public BinaryOperator<Container<T>> combiner() {
        return (acc1, acc2) -> {
            acc1.last = acc2.last;
            return acc1;
        };
    }

    @Override
    public Function<Container<T>, Optional<T>> finisher() {
        return acc -> Optional.ofNullable(acc.last);
    }

    @Override
    public Set<Characteristics> characteristics() {
        return Collections.emptySet();
    }
}