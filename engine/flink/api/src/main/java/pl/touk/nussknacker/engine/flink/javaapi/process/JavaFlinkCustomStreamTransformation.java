package pl.touk.nussknacker.engine.flink.javaapi.process;

import org.apache.flink.streaming.api.datastream.DataStream;
import pl.touk.nussknacker.engine.api.InterpretationResult;
import pl.touk.nussknacker.engine.api.ValueWithContext;
import pl.touk.nussknacker.engine.flink.api.process.FlinkCustomNodeContext;
import pl.touk.nussknacker.engine.flink.api.process.FlinkCustomStreamTransformation;

import java.util.function.BiFunction;
import java.util.function.Function;

public class JavaFlinkCustomStreamTransformation {

    public static FlinkCustomStreamTransformation apply(BiFunction<DataStream<InterpretationResult>, FlinkCustomNodeContext, DataStream<ValueWithContext<Object>>> fun) {
        return new FlinkCustomStreamTransformation() {
            @Override
            public org.apache.flink.streaming.api.scala.DataStream<ValueWithContext<Object>> transform(org.apache.flink.streaming.api.scala.DataStream<InterpretationResult> start, FlinkCustomNodeContext context) {
                return new org.apache.flink.streaming.api.scala.DataStream<>(fun.apply(start.javaStream(), context));
            }
        };
    }

    public static FlinkCustomStreamTransformation apply(Function<DataStream<InterpretationResult>, DataStream<ValueWithContext<Object>>> fun) {
        return apply(new BiFunction<DataStream<InterpretationResult>, FlinkCustomNodeContext, DataStream<ValueWithContext<Object>>>() {
            @Override
            public DataStream<ValueWithContext<Object>> apply(DataStream<InterpretationResult> data, FlinkCustomNodeContext flinkCustomNodeContext) {
                return fun.apply(data);
            }
        });
    }

}
