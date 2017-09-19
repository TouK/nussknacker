package pl.touk.nussknacker.engine.javaexample;

import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.util.Collector;
import pl.touk.nussknacker.engine.api.CustomStreamTransformer;
import pl.touk.nussknacker.engine.api.InterpretationResult;
import pl.touk.nussknacker.engine.api.LazyInterpreter;
import pl.touk.nussknacker.engine.api.MethodToInvoke;
import pl.touk.nussknacker.engine.api.ParamName;
import pl.touk.nussknacker.engine.api.ValueWithContext;
import pl.touk.nussknacker.engine.api.util.MultiMap;
import pl.touk.nussknacker.engine.flink.api.process.FlinkCustomStreamTransformation;
import pl.touk.nussknacker.engine.flink.api.state.TimestampedEvictableStateFunction;
import pl.touk.nussknacker.engine.flink.javaapi.process.JavaFlinkCustomStreamTransformation;
import scala.concurrent.duration.Duration;

import static scala.collection.JavaConversions.asJavaCollection;

public class EventsCounter extends CustomStreamTransformer {

    @MethodToInvoke(returnType = EventCount.class)
    public FlinkCustomStreamTransformation execute(@ParamName("key") LazyInterpreter<String> key, @ParamName("length") String length) {
        return JavaFlinkCustomStreamTransformation.apply(start -> {
            long lengthInMillis = Duration.apply(length).toMillis();
            return start
                    //it seems that explicit anonymous class is mandatory here, otherwise there is some weird LazyInterpreter generic type exception
                    .keyBy(new KeySelector<InterpretationResult, String>() {
                        @Override
                        public String getKey(InterpretationResult ir) throws Exception {
                            return key.syncInterpretationFunction().apply(ir);
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

    public static class CounterFunction extends TimestampedEvictableStateFunction<InterpretationResult, ValueWithContext<Object>, Integer> {
        long lengthInMillis;

        public CounterFunction(long lengthInMillis) {
            this.lengthInMillis = lengthInMillis;
        }

        @Override
        public ValueStateDescriptor<MultiMap<Object, Integer>> stateDescriptor() {
            return new ValueStateDescriptor("state", MultiMap.class);
        }

        @Override
        public void processElement(InterpretationResult ir, Context ctx, Collector<ValueWithContext<Object>> out) throws Exception {
            long timestamp = ctx.timestamp();

            moveEvictionTime(lengthInMillis, ctx);

            MultiMap<Object, Integer> eventCount = stateValue().add(timestamp, 1);
            state().update(eventCount);
            //TODO java version of MultiMap?
            int eventsCount = asJavaCollection(eventCount.map().values()).stream()
                    .map(l -> asJavaCollection(l).stream().mapToInt(Integer::intValue).sum())
                    .mapToInt(Integer::intValue).sum();
            out.collect(
                new ValueWithContext<>(new EventCount(eventsCount), ir.finalContext())
            );
        }

    }

}



