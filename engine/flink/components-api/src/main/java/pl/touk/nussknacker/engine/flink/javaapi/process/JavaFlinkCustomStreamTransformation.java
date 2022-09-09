package pl.touk.nussknacker.engine.flink.javaapi.process;

import java.util.function.BiFunction;
import java.util.function.Function;
import org.apache.flink.streaming.api.datastream.DataStream;
import pl.touk.nussknacker.engine.api.Context;
import pl.touk.nussknacker.engine.api.ValueWithContext;
import pl.touk.nussknacker.engine.flink.api.process.FlinkCustomNodeContext;
import pl.touk.nussknacker.engine.flink.api.process.FlinkCustomStreamTransformation;

public class JavaFlinkCustomStreamTransformation {

    public static FlinkCustomStreamTransformation apply(BiFunction<DataStream<Context>, FlinkCustomNodeContext, DataStream<ValueWithContext<Object>>> fun) {
        return fun::apply;
    }

    public static FlinkCustomStreamTransformation apply(Function<DataStream<Context>, DataStream<ValueWithContext<Object>>> fun) {
        return apply((data, flinkCustomNodeContext) -> fun.apply(data));
    }

}
