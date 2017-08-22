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

    public FlinkCustomStreamTransformation execute(@ParamName("clientId") LazyInterpreter<String> clientId) {
        return JavaFlinkCustomStreamTransformation.apply(start -> {
            return
                    //it seems that explicit anonymous class is mandatory here, otherwise there is some weird LazyInterpreter generic type exception
                    start.keyBy(new KeySelector<InterpretationResult, String>() {
                        @Override
                        public String getKey(InterpretationResult ir) throws Exception {
                            return clientId.syncInterpretationFunction().apply(ir);
                        }
                    }).map(amountAggregateFunction());
        });
    }

    private RichMapFunction<InterpretationResult, ValueWithContext<Object>> amountAggregateFunction() {
        return new RichMapFunction<InterpretationResult, ValueWithContext<Object>>() {
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
            public ValueWithContext<Object> map(InterpretationResult ir) throws Exception {
                Transaction transaction = ir.finalContext().apply("input");
                int aggregatedAmount = transaction.amount + ((state.value() == null) ? 0 : state.value().amount);
                state.update(new AggregatedAmount(transaction.clientId, aggregatedAmount));
                return (new ValueWithContext(state.value(), ir.finalContext()));
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
