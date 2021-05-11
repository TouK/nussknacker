package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import java.util.concurrent.TimeUnit
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ContextTransformation
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.editor._
import pl.touk.nussknacker.engine.flink.api.compat.ExplicitUidInOperatorsSupport

import scala.concurrent.duration.Duration

object sampleTransformers {

  @deprecated("Should be used SimpleSlidingAggregateTransformerV2 with human friendly 'windowLength' parameter", "0.1.2")
  object SimpleSlidingAggregateTransformer extends CustomStreamTransformer with ExplicitUidInOperatorsSupport {

    @MethodToInvoke(returnType = classOf[AnyRef])
    def execute(@ParamName("keyBy") keyBy: LazyParameter[CharSequence],
                @SimpleEditor(
                  `type` = SimpleEditorType.FIXED_VALUES_EDITOR,
                  possibleValues = Array(
                    new LabeledExpression(expression = "'Max'", label = "Max"),
                    new LabeledExpression(expression = "'Min'", label = "Min"),
                    new LabeledExpression(expression = "'Sum'", label = "Sum"),
                    new LabeledExpression(expression = "'ApproximateSetCardinality'", label = "ApproximateSetCardinality"),
                    new LabeledExpression(expression = "'Set'", label = "Set")
                  )
                )
                @ParamName("aggregator") aggregatorType: String,
                @ParamName("aggregateBy") aggregateBy: LazyParameter[AnyRef],
                @ParamName("windowLengthInSeconds") length: Long,
                @OutputVariableName variableName: String)(implicit nodeId: NodeId): ContextTransformation = {
      val windowDuration = Duration(length, TimeUnit.SECONDS)
      transformers.slidingTransformer(keyBy, aggregateBy, toAggregator(aggregatorType), windowDuration, variableName, emitWhenEventLeft = false, explicitUidInStatefulOperators)
    }
  }

  @deprecated("Should be used SlidingAggregateTransformerV2 with support for more flexible aggregator picking strategy", "0.2.0")
  object SimpleSlidingAggregateTransformerV2 extends CustomStreamTransformer with ExplicitUidInOperatorsSupport {

    @MethodToInvoke(returnType = classOf[AnyRef])
    def execute(@ParamName("keyBy") keyBy: LazyParameter[CharSequence],
                @SimpleEditor(
                  `type` = SimpleEditorType.FIXED_VALUES_EDITOR,
                  possibleValues = Array(
                    new LabeledExpression(expression = "'Max'", label = "Max"),
                    new LabeledExpression(expression = "'Min'", label = "Min"),
                    new LabeledExpression(expression = "'Sum'", label = "Sum"),
                    new LabeledExpression(expression = "'ApproximateSetCardinality'", label = "ApproximateSetCardinality"),
                    new LabeledExpression(expression = "'Set'", label = "Set")
                  )
                )
                @ParamName("aggregator") aggregatorType: String,
                @ParamName("aggregateBy") aggregateBy: LazyParameter[AnyRef],
                @ParamName("windowLength") length: java.time.Duration,
                @OutputVariableName variableName: String)(implicit nodeId: NodeId): ContextTransformation = {
      val windowDuration = Duration(length.toMillis, TimeUnit.MILLISECONDS)
      transformers.slidingTransformer(keyBy, aggregateBy, toAggregator(aggregatorType), windowDuration, variableName, emitWhenEventLeft = false, explicitUidInStatefulOperators)
    }
  }

  @deprecated("Should be used TumblingAggregateTransformer with support for more flexible aggregator picking strategy", "0.2.0")
  object SimpleTumblingAggregateTransformer extends CustomStreamTransformer with ExplicitUidInOperatorsSupport {

    @MethodToInvoke(returnType = classOf[AnyRef])
    def execute(@ParamName("keyBy") keyBy: LazyParameter[CharSequence],
                @SimpleEditor(
                  `type` = SimpleEditorType.FIXED_VALUES_EDITOR,
                  possibleValues = Array(
                    new LabeledExpression(expression = "'Max'", label = "Max"),
                    new LabeledExpression(expression = "'Min'", label = "Min"),
                    new LabeledExpression(expression = "'Sum'", label = "Sum"),
                    new LabeledExpression(expression = "'ApproximateSetCardinality'", label = "ApproximateSetCardinality"),
                    new LabeledExpression(expression = "'Set'", label = "Set")
                  )
                )
                @ParamName("aggregator") aggregatorType: String,
                @ParamName("aggregateBy") aggregateBy: LazyParameter[AnyRef],
                @ParamName("windowLength") length: java.time.Duration,
                @OutputVariableName variableName: String)(implicit nodeId: NodeId): ContextTransformation = {
      val windowDuration = Duration(length.toMillis, TimeUnit.MILLISECONDS)
      transformers.tumblingTransformer(keyBy, aggregateBy, toAggregator(aggregatorType), windowDuration, variableName, TumblingWindowTrigger.OnEnd, explicitUidInStatefulOperators)
    }

  }

  private def toAggregator(aggregatorType: String) = aggregatorType match {
    case "Max" => aggregates.MaxAggregator
    case "Min" => aggregates.MinAggregator
    case "Set" => aggregates.SetAggregator
    case "Sum" => aggregates.SumAggregator
    case "ApproximateSetCardinality" => HyperLogLogPlusAggregator()
    case _ => throw new IllegalArgumentException(s"Unknown aggregate type: $aggregatorType")
  }

  @deprecated("Should be used SlidingAggregateTransformerV2 with human friendly 'windowLength' parameter and better editor for aggregator", "0.2.0")
  object SlidingAggregateTransformer extends CustomStreamTransformer with ExplicitUidInOperatorsSupport {

    @MethodToInvoke(returnType = classOf[AnyRef])
    def execute(@ParamName("keyBy") keyBy: LazyParameter[CharSequence],
                @ParamName("aggregator") aggregator: Aggregator,
                @ParamName("aggregateBy") aggregateBy: LazyParameter[AnyRef],
                @ParamName("windowLengthInSeconds") length: Long,
                @OutputVariableName variableName: String)(implicit nodeId: NodeId): ContextTransformation = {
      val windowDuration = Duration(length, TimeUnit.SECONDS)
      transformers.slidingTransformer(keyBy, aggregateBy, aggregator, windowDuration, variableName, emitWhenEventLeft = false, explicitUidInStatefulOperators)
    }
  }

  /**
   * This aggregator can be used for both predefined aggregators (see list below) and for some specialized aggregators like #AGG.map
   * when you switch editor to "raw mode". It also has `emitWhenEventLeft` flag.
   */
  object SlidingAggregateTransformerV2 extends CustomStreamTransformer with ExplicitUidInOperatorsSupport {

    @MethodToInvoke(returnType = classOf[AnyRef])
    def execute(@ParamName("keyBy") keyBy: LazyParameter[CharSequence],
                @ParamName("aggregator")
                @DualEditor(simpleEditor = new SimpleEditor(
                  `type` = SimpleEditorType.FIXED_VALUES_EDITOR,
                  possibleValues = Array(
                    new LabeledExpression(label = "First", expression = "T(pl.touk.nussknacker.engine.flink.util.transformer.aggregate.AggregateHelper).FIRST"),
                    new LabeledExpression(label = "Last",  expression = "T(pl.touk.nussknacker.engine.flink.util.transformer.aggregate.AggregateHelper).LAST"),
                    new LabeledExpression(label = "Min",   expression = "T(pl.touk.nussknacker.engine.flink.util.transformer.aggregate.AggregateHelper).MIN"),
                    new LabeledExpression(label = "Max",   expression = "T(pl.touk.nussknacker.engine.flink.util.transformer.aggregate.AggregateHelper).MAX"),
                    new LabeledExpression(label = "Sum",   expression = "T(pl.touk.nussknacker.engine.flink.util.transformer.aggregate.AggregateHelper).SUM"),
                    new LabeledExpression(label = "List",  expression = "T(pl.touk.nussknacker.engine.flink.util.transformer.aggregate.AggregateHelper).LIST"),
                    new LabeledExpression(label = "Set",   expression = "T(pl.touk.nussknacker.engine.flink.util.transformer.aggregate.AggregateHelper).SET"),
                    new LabeledExpression(label = "ApproximateSetCardinality", expression = "T(pl.touk.nussknacker.engine.flink.util.transformer.aggregate.AggregateHelper).APPROX_CARDINALITY")
                  )), defaultMode = DualEditorMode.SIMPLE) aggregator: Aggregator,
                @ParamName("aggregateBy") aggregateBy: LazyParameter[AnyRef],
                @ParamName("windowLength") length: java.time.Duration,
                @ParamName("emitWhenEventLeft") emitWhenEventLeft: Boolean,
                @OutputVariableName variableName: String)(implicit nodeId: NodeId): ContextTransformation = {
      val windowDuration = Duration(length.toMillis, TimeUnit.MILLISECONDS)
      transformers.slidingTransformer(keyBy, aggregateBy, aggregator, windowDuration, variableName, emitWhenEventLeft, explicitUidInStatefulOperators)
    }
  }

  object TumblingAggregateTransformer extends CustomStreamTransformer with ExplicitUidInOperatorsSupport {

    @MethodToInvoke(returnType = classOf[AnyRef])
    def execute(@ParamName("keyBy") keyBy: LazyParameter[CharSequence],
                @ParamName("aggregator")
                @DualEditor(simpleEditor = new SimpleEditor(
                  `type` = SimpleEditorType.FIXED_VALUES_EDITOR,
                  possibleValues = Array(
                    new LabeledExpression(label = "First", expression = "T(pl.touk.nussknacker.engine.flink.util.transformer.aggregate.AggregateHelper).FIRST"),
                    new LabeledExpression(label = "Last",  expression = "T(pl.touk.nussknacker.engine.flink.util.transformer.aggregate.AggregateHelper).LAST"),
                    new LabeledExpression(label = "Min",   expression = "T(pl.touk.nussknacker.engine.flink.util.transformer.aggregate.AggregateHelper).MIN"),
                    new LabeledExpression(label = "Max",   expression = "T(pl.touk.nussknacker.engine.flink.util.transformer.aggregate.AggregateHelper).MAX"),
                    new LabeledExpression(label = "Sum",   expression = "T(pl.touk.nussknacker.engine.flink.util.transformer.aggregate.AggregateHelper).SUM"),
                    new LabeledExpression(label = "List",  expression = "T(pl.touk.nussknacker.engine.flink.util.transformer.aggregate.AggregateHelper).LIST"),
                    new LabeledExpression(label = "Set",   expression = "T(pl.touk.nussknacker.engine.flink.util.transformer.aggregate.AggregateHelper).SET"),
                    new LabeledExpression(label = "ApproximateSetCardinality", expression = "T(pl.touk.nussknacker.engine.flink.util.transformer.aggregate.AggregateHelper).APPROX_CARDINALITY")
                  )), defaultMode = DualEditorMode.SIMPLE) aggregator: Aggregator,
                @ParamName("aggregateBy") aggregateBy: LazyParameter[AnyRef],
                @ParamName("windowLength") length: java.time.Duration,
                @ParamName("emitWhen") trigger: TumblingWindowTrigger,
                @OutputVariableName variableName: String)(implicit nodeId: NodeId): ContextTransformation = {
      val windowDuration = Duration(length.toMillis, TimeUnit.MILLISECONDS)
      transformers.tumblingTransformer(keyBy, aggregateBy, aggregator, windowDuration, variableName, trigger, explicitUidInStatefulOperators)
    }

  }


  //Experimental component, API may change in the future
  object SessionWindowAggregateTransformer extends CustomStreamTransformer with ExplicitUidInOperatorsSupport {

    @MethodToInvoke(returnType = classOf[AnyRef])
    def execute(@ParamName("keyBy") keyBy: LazyParameter[CharSequence],
                @ParamName("aggregator")
                @DualEditor(simpleEditor = new SimpleEditor(
                  `type` = SimpleEditorType.FIXED_VALUES_EDITOR,
                  possibleValues = Array(
                    new LabeledExpression(label = "First", expression = "T(pl.touk.nussknacker.engine.flink.util.transformer.aggregate.AggregateHelper).FIRST"),
                    new LabeledExpression(label = "Last",  expression = "T(pl.touk.nussknacker.engine.flink.util.transformer.aggregate.AggregateHelper).LAST"),
                    new LabeledExpression(label = "Min",   expression = "T(pl.touk.nussknacker.engine.flink.util.transformer.aggregate.AggregateHelper).MIN"),
                    new LabeledExpression(label = "Max",   expression = "T(pl.touk.nussknacker.engine.flink.util.transformer.aggregate.AggregateHelper).MAX"),
                    new LabeledExpression(label = "Sum",   expression = "T(pl.touk.nussknacker.engine.flink.util.transformer.aggregate.AggregateHelper).SUM"),
                    new LabeledExpression(label = "List",  expression = "T(pl.touk.nussknacker.engine.flink.util.transformer.aggregate.AggregateHelper).LIST"),
                    new LabeledExpression(label = "Set",   expression = "T(pl.touk.nussknacker.engine.flink.util.transformer.aggregate.AggregateHelper).SET"),
                    new LabeledExpression(label = "ApproximateSetCardinality", expression = "T(pl.touk.nussknacker.engine.flink.util.transformer.aggregate.AggregateHelper).APPROX_CARDINALITY")
                  )), defaultMode = DualEditorMode.SIMPLE) aggregator: Aggregator,
                @ParamName("aggregateBy") aggregateBy: LazyParameter[AnyRef],
                @ParamName("endSessionCondition") endSessionCondition: LazyParameter[java.lang.Boolean],
                @ParamName("sessionTimeout") sessionTimeout: java.time.Duration,
                @ParamName("emitWhen") trigger: SessionWindowTrigger,
                @OutputVariableName variableName: String)(implicit nodeId: NodeId): ContextTransformation = {
      val sessionTimeoutDuration = Duration(sessionTimeout.toMillis, TimeUnit.MILLISECONDS)
      transformers.sessionWindowTransformer(
        keyBy, aggregateBy, aggregator, sessionTimeoutDuration, endSessionCondition, trigger, variableName)
    }

  }

}
