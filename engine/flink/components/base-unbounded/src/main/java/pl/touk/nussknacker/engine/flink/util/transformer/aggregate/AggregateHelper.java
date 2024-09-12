package pl.touk.nussknacker.engine.flink.util.transformer.aggregate;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Map;
import pl.touk.nussknacker.engine.api.Hidden;
import pl.touk.nussknacker.engine.api.ParamName;
import pl.touk.nussknacker.engine.api.definition.*;
import pl.touk.nussknacker.engine.api.editor.DualEditorMode;
import pl.touk.nussknacker.engine.api.editor.FixedValuesEditorMode;
import scala.collection.JavaConverters;

/**
 * This class is in Java, because constants are used in expressions in editors - see
 * `pl.touk.nussknacker.engine.flink.util.transformer.aggregate.SlidingAggregateTransformerV2`. and scala objects are
 * not good for that. Be aware that. If you add some new aggregator please add it also there to make sure that it will
 * be available in selectbox.
 *
 * You should define `#AGG` global variable, because it is used in editors.
 */
public class AggregateHelper implements Serializable {

    public static final SimpleParameterEditor SIMPLE_EDITOR = new FixedValuesParameterEditor(
            JavaConverters.collectionAsScalaIterableConverter(Arrays.asList(
                    FixedExpressionValue$.MODULE$.apply("#AGG.first", "First"),
                    FixedExpressionValue$.MODULE$.apply("#AGG.last", "Last"),
                    FixedExpressionValue$.MODULE$.apply("#AGG.countWhen", "CountWhen"),
                    FixedExpressionValue$.MODULE$.apply("#AGG.average", "Average"),
                    FixedExpressionValue$.MODULE$.apply("#AGG.stddevPop", "StddevPop"),
                    FixedExpressionValue$.MODULE$.apply("#AGG.stddevSamp", "StddevSamp"),
                    FixedExpressionValue$.MODULE$.apply("#AGG.varPop", "VarPop"),
                    FixedExpressionValue$.MODULE$.apply("#AGG.varSamp", "VarSamp"),
                    FixedExpressionValue$.MODULE$.apply("#AGG.median", "Median"),
                    FixedExpressionValue$.MODULE$.apply("#AGG.min", "Min"),
                    FixedExpressionValue$.MODULE$.apply("#AGG.max", "Max"),
                    FixedExpressionValue$.MODULE$.apply("#AGG.sum", "Sum"),
                    FixedExpressionValue$.MODULE$.apply("#AGG.list", "List"),
                    FixedExpressionValue$.MODULE$.apply("#AGG.set", "Set"),
                    FixedExpressionValue$.MODULE$.apply("#AGG.approxCardinality", "ApproximateSetCardinality")
            )).asScala().toList(),
            FixedValuesEditorMode.LIST
    );

    @Hidden
    public static final DualParameterEditor DUAL_EDITOR = new DualParameterEditor(SIMPLE_EDITOR, DualEditorMode.SIMPLE);

    private static final Aggregator SUM = aggregates.SumAggregator$.MODULE$;
    private static final Aggregator MAX = aggregates.MaxAggregator$.MODULE$;
    private static final Aggregator MIN = aggregates.MinAggregator$.MODULE$;
    private static final Aggregator LIST = aggregates.ListAggregator$.MODULE$;
    private static final Aggregator SET = aggregates.SetAggregator$.MODULE$;
    private static final Aggregator FIRST = aggregates.FirstAggregator$.MODULE$;
    private static final Aggregator LAST = aggregates.LastAggregator$.MODULE$;
    private static final Aggregator COUNT_WHEN = aggregates.CountWhenAggregator$.MODULE$;
    private static final Aggregator AVERAGE = aggregates.AverageAggregator$.MODULE$;
    private static final Aggregator STDDEV_POP = aggregates.PopulationStandardDeviationAggregator$.MODULE$;
    private static final Aggregator STDDEV_SAMP = aggregates.SampleStandardDeviationAggregator$.MODULE$;
    private static final Aggregator VAR_POP = aggregates.PopulationVarianceAggregator$.MODULE$;
    private static final Aggregator VAR_SAMP = aggregates.SampleVarianceAggregator$.MODULE$;
    private static final Aggregator MEDIAN = aggregates.MedianAggregator$.MODULE$;
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

    public Aggregator countWhen = COUNT_WHEN;
    public Aggregator average = AVERAGE;

    public Aggregator stddevPop = STDDEV_POP;
    public Aggregator stddevSamp = STDDEV_SAMP;
    public Aggregator varPop = VAR_POP;
    public Aggregator varSamp = VAR_SAMP;

    public Aggregator median = MEDIAN;

    public Aggregator approxCardinality = APPROX_CARDINALITY;

    public Aggregator map(@ParamName("parts") Map<String, Aggregator> parts) {
        return new aggregates.MapAggregator(parts);
    }
}
