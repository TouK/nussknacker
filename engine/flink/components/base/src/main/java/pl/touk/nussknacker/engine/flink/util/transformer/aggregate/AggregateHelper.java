package pl.touk.nussknacker.engine.flink.util.transformer.aggregate;

import java.util.Arrays;
import java.util.Map;
import pl.touk.nussknacker.engine.api.Hidden;
import pl.touk.nussknacker.engine.api.ParamName;
import pl.touk.nussknacker.engine.api.definition.DualParameterEditor;
import pl.touk.nussknacker.engine.api.definition.FixedExpressionValue;
import pl.touk.nussknacker.engine.api.definition.FixedValuesParameterEditor;
import pl.touk.nussknacker.engine.api.definition.SimpleParameterEditor;
import pl.touk.nussknacker.engine.api.editor.DualEditorMode;
import scala.collection.JavaConverters;

/**
 * This class is in Java, because constants are used in expressions in editors - see
 * `pl.touk.nussknacker.engine.flink.util.transformer.aggregate.SlidingAggregateTransformerV2`. and scala objects are
 * not good for that. Be aware that. If you add some new aggregator please add it also there to make sure that it will
 * be available in selectbox.
 *
 * You should define `#AGG` global variable, because it is used in editors.
 */
public class AggregateHelper {

    public static final SimpleParameterEditor SIMPLE_EDITOR = new FixedValuesParameterEditor(JavaConverters.collectionAsScalaIterableConverter(Arrays.asList(
            new FixedExpressionValue("#AGG.first", "First"),
            new FixedExpressionValue("#AGG.last", "Last"),
            new FixedExpressionValue("#AGG.min", "Min"),
            new FixedExpressionValue("#AGG.max", "Max"),
            new FixedExpressionValue("#AGG.sum", "Sum"),
            new FixedExpressionValue("#AGG.list", "List"),
            new FixedExpressionValue("#AGG.set", "Set"),
            new FixedExpressionValue("#AGG.approxCardinality", "ApproximateSetCardinality"))).asScala().toList());

    @Hidden
    public static final DualParameterEditor DUAL_EDITOR = new DualParameterEditor(SIMPLE_EDITOR, DualEditorMode.SIMPLE);

    private static final Aggregator SUM = aggregates.SumAggregator$.MODULE$;
    private static final Aggregator MAX = aggregates.MaxAggregator$.MODULE$;
    private static final Aggregator MIN = aggregates.MinAggregator$.MODULE$;
    private static final Aggregator LIST = aggregates.ListAggregator$.MODULE$;
    private static final Aggregator SET = aggregates.SetAggregator$.MODULE$;
    private static final Aggregator FIRST = aggregates.FirstAggregator$.MODULE$;
    private static final Aggregator LAST = aggregates.LastAggregator$.MODULE$;
    private static final Aggregator APPROX_CARDINALITY = HyperLogLogPlusAggregator$.MODULE$.apply(5, 10);

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

    public Aggregator map(@ParamName("parts") Map<String, Aggregator> parts) {
        return new aggregates.MapAggregator(parts);
    }

}
