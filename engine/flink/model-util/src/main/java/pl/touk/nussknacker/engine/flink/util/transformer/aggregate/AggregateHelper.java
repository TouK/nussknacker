package pl.touk.nussknacker.engine.flink.util.transformer.aggregate;

import java.util.Arrays;
import java.util.Map;
import pl.touk.nussknacker.engine.api.ParamName;
import pl.touk.nussknacker.engine.api.definition.DualParameterEditor;
import pl.touk.nussknacker.engine.api.definition.FixedExpressionValue;
import pl.touk.nussknacker.engine.api.definition.FixedValuesParameterEditor;
import pl.touk.nussknacker.engine.api.definition.SimpleParameterEditor;
import pl.touk.nussknacker.engine.api.editor.DualEditorMode;
import scala.collection.JavaConverters;

// This class is in Java, because constants are used in expressions in editors - see
// `pl.touk.nussknacker.engine.flink.util.transformer.aggregate.SlidingAggregateTransformerV2`. and scala objects are
// not good for that. Be aware that. If you add some new aggregator please add it also there to make sure that it will
// be available in selectbox.
public class AggregateHelper {

    public static final SimpleParameterEditor SIMPLE_EDITOR = new FixedValuesParameterEditor(JavaConverters.collectionAsScalaIterableConverter(Arrays.asList(
            new FixedExpressionValue("T(" + AggregateHelper.class.getName() + ").FIRST", "First"),
            new FixedExpressionValue("T(" + AggregateHelper.class.getName() + ").LAST", "Last"),
            new FixedExpressionValue("T(" + AggregateHelper.class.getName() + ").MIN", "Min"),
            new FixedExpressionValue("T(" + AggregateHelper.class.getName() + ").MAX", "Max"),
            new FixedExpressionValue("T(" + AggregateHelper.class.getName() + ").SUM", "Sum"),
            new FixedExpressionValue("T(" + AggregateHelper.class.getName() + ").LIST", "List"),
            new FixedExpressionValue("T(" + AggregateHelper.class.getName() + ").SET", "Set"),
            new FixedExpressionValue("T(" + AggregateHelper.class.getName() + ").APPROX_CARDINALITY", "ApproximateSetCardinality"))).asScala().toList());

    public static final DualParameterEditor DUAL_EDITOR = new DualParameterEditor(SIMPLE_EDITOR, DualEditorMode.SIMPLE);

    public static final Aggregator SUM = aggregates.SumAggregator$.MODULE$;
    public static final Aggregator MAX = aggregates.MaxAggregator$.MODULE$;
    public static final Aggregator MIN = aggregates.MinAggregator$.MODULE$;
    public static final Aggregator LIST = aggregates.ListAggregator$.MODULE$;
    public static final Aggregator SET = aggregates.SetAggregator$.MODULE$;
    public static final Aggregator FIRST = aggregates.FirstAggregator$.MODULE$;
    public static final Aggregator LAST = aggregates.LastAggregator$.MODULE$;
    public static final Aggregator APPROX_CARDINALITY = HyperLogLogPlusAggregator$.MODULE$.apply(5, 10);

    // Instance methods below are for purpose of using in SpEL so your IDE can report that they are not used.
    // Please keep this list consistent with list above to make sure that all aggregators are available in both ways.
    public Aggregator sum = SUM;

    public Aggregator max = MAX;

    public Aggregator min = MIN;

    public Aggregator list = LIST;

    public Aggregator set = SET;

    public Aggregator first = FIRST;

    public Aggregator last = LAST;

    public Aggregator approxCardinality = APPROX_CARDINALITY;

    public Aggregator map(@ParamName("parts" ) Map<String, Aggregator> parts) {
        return new aggregates.MapAggregator(parts);
    }

}
