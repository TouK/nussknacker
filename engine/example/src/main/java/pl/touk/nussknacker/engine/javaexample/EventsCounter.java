package pl.touk.nussknacker.engine.javaexample;

import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.util.Collector;
import pl.touk.nussknacker.engine.api.CustomStreamTransformer;
import pl.touk.nussknacker.engine.api.LazyParameter;
import pl.touk.nussknacker.engine.api.MethodToInvoke;
import pl.touk.nussknacker.engine.api.ParamName;
import pl.touk.nussknacker.engine.api.ValueWithContext;
import pl.touk.nussknacker.engine.api.util.MultiMap;
import pl.touk.nussknacker.engine.flink.api.process.FlinkCustomStreamTransformation;
import pl.touk.nussknacker.engine.flink.api.state.TimestampedEvictableStateFunction;
import pl.touk.nussknacker.engine.flink.javaapi.process.JavaFlinkCustomStreamTransformation;
import scala.concurrent.duration.Duration;

import static scala.collection.JavaConverters.asJavaCollectionConverter;

public class EventsCounter extends CustomStreamTransformer {

    @MethodToInvoke(returnType = EventCount.class)
    public FlinkCustomStreamTransformation execute(@ParamName("key") LazyParameter<String> key, @ParamName("length") String length) {
        return JavaFlinkCustomStreamTransformation.apply((start, ctx) -> {
            long lengthInMillis = Duration.apply(length).toMillis();
                    return start
                        .map(ctx.lazyParameterHelper().lazyMapFunction(key))
                        //it seems that explicit anonymous class is mandatory here, otherwise there is some weird LazyInterpreter generic type exception
                        .keyBy(new KeySelector<ValueWithContext<String>, String>() {
                            @Override
                            public String getKey(ValueWithContext<String> ir) throws Exception {
                                return ir.value();
                            }
                        })
                        .process(new CounterFunction(lengthInMillis));
        });
    }

    public static class EventCount {
        long count;

        public EventCount(long count) {
            this.count = count;
        }

        public long count() {
            return count;
        }
    }

    public static class CounterFunction extends TimestampedEvictableStateFunction<ValueWithContext<String>, ValueWithContext<Object>, Integer> {
        long lengthInMillis;

        public CounterFunction(long lengthInMillis) {
            this.lengthInMillis = lengthInMillis;
        }

        @Override
        @SuppressWarnings("unchecked")
        public ValueStateDescriptor<MultiMap<Object, Integer>> stateDescriptor() {
            return new ValueStateDescriptor("state", MultiMap.class);
        }

        @Override
        public void processElement(ValueWithContext<String> ir, Context ctx, Collector<ValueWithContext<Object>> out) throws Exception {
            long timestamp = ctx.timestamp();

            moveEvictionTime(lengthInMillis, ctx);

            MultiMap<Object, Integer> eventCount = stateValue().add(timestamp, 1);
            state().update(eventCount);
            //TODO java version of MultiMap?
            int eventsCount = asJavaCollectionConverter(eventCount.map().values()).asJavaCollection().stream()
                    .map(l -> asJavaCollectionConverter(l).asJavaCollection().stream().mapToInt(Integer::intValue).sum())
                    .mapToInt(Integer::intValue).sum();
            out.collect(
                new ValueWithContext<>(new EventCount(eventsCount), ir.context())
            );
        }

    }

}



