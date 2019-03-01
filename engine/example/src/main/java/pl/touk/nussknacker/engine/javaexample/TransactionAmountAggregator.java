package pl.touk.nussknacker.engine.javaexample;

import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.configuration.Configuration;
import pl.touk.nussknacker.engine.api.*;
import pl.touk.nussknacker.engine.flink.api.process.FlinkCustomStreamTransformation;
import pl.touk.nussknacker.engine.flink.javaapi.process.JavaFlinkCustomStreamTransformation;

public class TransactionAmountAggregator extends CustomStreamTransformer {

    @MethodToInvoke
    public FlinkCustomStreamTransformation execute(@ParamName("clientId") LazyParameter<String> clientId) {
        return JavaFlinkCustomStreamTransformation.apply((start, ctx) -> {
            return
                    //it seems that explicit anonymous class is mandatory here, otherwise there is some weird LazyInterpreter generic type exception
                    start
                        .map(ctx.nodeServices().lazyMapFunction(clientId))
                        .keyBy(new KeySelector<ValueWithContext<String>, String>() {
                            @Override
                            public String getKey(ValueWithContext<String> ir) throws Exception {
                                return ir.value();
                            }
                        })
                        .map(amountAggregateFunction());
        });
    }

    private RichMapFunction<ValueWithContext<String>, ValueWithContext<Object>> amountAggregateFunction() {
        return new RichMapFunction<ValueWithContext<String>, ValueWithContext<Object>>() {
            ValueState<AggregatedAmount> state = null;

            @Override
            public void open(Configuration parameters) throws Exception {
                super.open(parameters);
                TypeInformation<AggregatedAmount> typeInfo = TypeInformation.of(AggregatedAmount.class);
                ValueStateDescriptor<AggregatedAmount> descriptor = new ValueStateDescriptor<>("state",
                        typeInfo.createSerializer(getRuntimeContext().getExecutionConfig()));
                state = getRuntimeContext().getState(descriptor);
            }

            @Override
            public ValueWithContext<Object> map(ValueWithContext<String> ir) throws Exception {
                Transaction transaction = ir.context().apply("input");
                int aggregatedAmount = transaction.amount + ((state.value() == null) ? 0 : state.value().amount);
                state.update(new AggregatedAmount(transaction.clientId, aggregatedAmount));
                return (new ValueWithContext<>(state.value(), ir.context()));
            }
        };
    }

    public static class AggregatedAmount {
        public String clientId;
        public int amount;

        public AggregatedAmount(String clientId, int amount) {
            this.clientId = clientId;
            this.amount = amount;
        }
    }
}
