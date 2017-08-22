package pl.touk.nussknacker.engine.javaexample;

import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.streaming.runtime.streamrecord.StreamRecord;
import pl.touk.nussknacker.engine.api.*;
import pl.touk.nussknacker.engine.api.util.MultiMap;
import pl.touk.nussknacker.engine.flink.api.process.FlinkCustomStreamTransformation;
import pl.touk.nussknacker.engine.flink.api.state.TimestampedEvictableState;
import pl.touk.nussknacker.engine.flink.javaapi.process.JavaFlinkCustomStreamTransformation;
import pl.touk.nussknacker.engine.flink.util.TypeInfoHelper$;
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
                    .transform("eventsCounter", TypeInfoHelper$.MODULE$.valueWithContextTypeInfo(), new CounterFunction(lengthInMillis))
                    ;
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

    public static class CounterFunction extends TimestampedEvictableState<Integer> {
        long lengthInMillis;

        public CounterFunction(long lengthInMillis) {
            this.lengthInMillis = lengthInMillis;
        }

        @Override
        public ValueStateDescriptor<MultiMap<Object, Integer>> stateDescriptor() {
            return new ValueStateDescriptor("state", MultiMap.class);
        }

        @Override
        public void processElement(StreamRecord<InterpretationResult> element) throws Exception {
            setEvictionTimeForCurrentKey(element.getTimestamp() + lengthInMillis);
            getState().update(filterState(element.getTimestamp(), lengthInMillis));

            InterpretationResult ir = element.getValue();
            MultiMap<Object, Integer> eventCount = stateValue().add(element.getTimestamp(), 1);
            state().update(eventCount);
            //TODO java version of MultiMap?
            int eventsCount = asJavaCollection(eventCount.map().values()).stream()
                    .map(l -> asJavaCollection(l).stream().mapToInt(Integer::intValue).sum())
                    .mapToInt(Integer::intValue).sum();
            output.collect(new StreamRecord<ValueWithContext<Object>>(
                    new ValueWithContext<>(new EventCount(eventsCount), ir.finalContext()), element.getTimestamp()
            ));
        }
    }

}



