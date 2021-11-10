package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import java.util.concurrent.TimeUnit
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ContextTransformation
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.editor._
import pl.touk.nussknacker.engine.flink.api.compat.ExplicitUidInOperatorsSupport

import scala.concurrent.duration.Duration

object sampleTransformers {

  private def toAggregator(aggregatorType: String) = aggregatorType match {
    case "Max" => aggregates.MaxAggregator
    case "Min" => aggregates.MinAggregator
    case "Set" => aggregates.SetAggregator
    case "Sum" => aggregates.SumAggregator
    case "ApproximateSetCardinality" => HyperLogLogPlusAggregator()
    case _ => throw new IllegalArgumentException(s"Unknown aggregate type: $aggregatorType")
  }

  /**
   * This aggregator can be used for both predefined aggregators (see list below) and for some specialized aggregators like #AGG.map
   * when you switch editor to "raw mode". It also has `emitWhenEventLeft` flag.
   *
   * You should define `#AGG` global variable, because it is used in editors picked for `aggregateBy` parameter.
   */
  object SlidingAggregateTransformerV2 extends CustomStreamTransformer with ExplicitUidInOperatorsSupport {

    @MethodToInvoke(returnType = classOf[AnyRef])
    def execute(@ParamName("groupBy") groupBy: LazyParameter[CharSequence],
                @ParamName("aggregator")
                @DualEditor(simpleEditor = new SimpleEditor(
                  `type` = SimpleEditorType.FIXED_VALUES_EDITOR,
                  possibleValues = Array(
                    new LabeledExpression(label = "First", expression = "#AGG.first"),
                    new LabeledExpression(label = "Last",  expression = "#AGG.last"),
                    new LabeledExpression(label = "Min",   expression = "#AGG.min"),
                    new LabeledExpression(label = "Max",   expression = "#AGG.max"),
                    new LabeledExpression(label = "Sum",   expression = "#AGG.sum"),
                    new LabeledExpression(label = "List",  expression = "#AGG.list"),
                    new LabeledExpression(label = "Set",   expression = "#AGG.set"),
                    new LabeledExpression(label = "ApproximateSetCardinality", expression = "#AGG.approxCardinality")
                  )), defaultMode = DualEditorMode.SIMPLE) aggregator: Aggregator,
                @ParamName("aggregateBy") aggregateBy: LazyParameter[AnyRef],
                @ParamName("windowLength") length: java.time.Duration,
                @ParamName("emitWhenEventLeft") @DefaultValue("false") emitWhenEventLeft: Boolean,
                @OutputVariableName variableName: String)(implicit nodeId: NodeId): ContextTransformation = {
      val windowDuration = Duration(length.toMillis, TimeUnit.MILLISECONDS)
      transformers.slidingTransformer(groupBy, aggregateBy, aggregator, windowDuration, variableName, emitWhenEventLeft, explicitUidInStatefulOperators)
    }
  }

  /**
   * Tumbling window aggregator.
   *
   * You should define `#AGG` global variable, because it is used in editors picked for `aggregateBy` parameter.
   */
  object TumblingAggregateTransformer extends CustomStreamTransformer with ExplicitUidInOperatorsSupport {

    @MethodToInvoke(returnType = classOf[AnyRef])
    def execute(@ParamName("groupBy") groupBy: LazyParameter[CharSequence],
                @ParamName("aggregator")
                @DualEditor(simpleEditor = new SimpleEditor(
                  `type` = SimpleEditorType.FIXED_VALUES_EDITOR,
                  possibleValues = Array(
                    new LabeledExpression(label = "First", expression = "#AGG.first"),
                    new LabeledExpression(label = "Last",  expression = "#AGG.last"),
                    new LabeledExpression(label = "Min",   expression = "#AGG.min"),
                    new LabeledExpression(label = "Max",   expression = "#AGG.max"),
                    new LabeledExpression(label = "Sum",   expression = "#AGG.sum"),
                    new LabeledExpression(label = "List",  expression = "#AGG.list"),
                    new LabeledExpression(label = "Set",   expression = "#AGG.set"),
                    new LabeledExpression(label = "ApproximateSetCardinality", expression = "#AGG.approxCardinality")
                  )), defaultMode = DualEditorMode.SIMPLE) aggregator: Aggregator,
                @ParamName("aggregateBy") aggregateBy: LazyParameter[AnyRef],
                @ParamName("windowLength") length: java.time.Duration,
                @ParamName("emitWhen") trigger: TumblingWindowTrigger,
                @OutputVariableName variableName: String)(implicit nodeId: NodeId): ContextTransformation = {
      val windowDuration = Duration(length.toMillis, TimeUnit.MILLISECONDS)
      transformers.tumblingTransformer(groupBy, aggregateBy, aggregator, windowDuration, variableName, trigger, explicitUidInStatefulOperators)
    }

  }

  /**
   * Session window aggregator. This component is experimental - API may change in the future
   *
   * You should define `#AGG` global variable, because it is used in editors picked for `aggregateBy` parameter.
   */
  object SessionWindowAggregateTransformer extends CustomStreamTransformer with ExplicitUidInOperatorsSupport {

    @MethodToInvoke(returnType = classOf[AnyRef])
    def execute(@ParamName("groupBy") groupBy: LazyParameter[CharSequence],
                @ParamName("aggregator")
                @DualEditor(simpleEditor = new SimpleEditor(
                  `type` = SimpleEditorType.FIXED_VALUES_EDITOR,
                  possibleValues = Array(
                    new LabeledExpression(label = "First", expression = "#AGG.first"),
                    new LabeledExpression(label = "Last",  expression = "#AGG.last"),
                    new LabeledExpression(label = "Min",   expression = "#AGG.min"),
                    new LabeledExpression(label = "Max",   expression = "#AGG.max"),
                    new LabeledExpression(label = "Sum",   expression = "#AGG.sum"),
                    new LabeledExpression(label = "List",  expression = "#AGG.list"),
                    new LabeledExpression(label = "Set",   expression = "#AGG.set"),
                    new LabeledExpression(label = "ApproximateSetCardinality", expression = "#AGG.approxCardinality")
                  )), defaultMode = DualEditorMode.SIMPLE) aggregator: Aggregator,
                @ParamName("aggregateBy") aggregateBy: LazyParameter[AnyRef],
                @ParamName("endSessionCondition") endSessionCondition: LazyParameter[java.lang.Boolean],
                @ParamName("sessionTimeout") sessionTimeout: java.time.Duration,
                @ParamName("emitWhen") trigger: SessionWindowTrigger,
                @OutputVariableName variableName: String)(implicit nodeId: NodeId): ContextTransformation = {
      val sessionTimeoutDuration = Duration(sessionTimeout.toMillis, TimeUnit.MILLISECONDS)
      transformers.sessionWindowTransformer(
        groupBy, aggregateBy, aggregator, sessionTimeoutDuration, endSessionCondition, trigger, variableName)
    }

  }

}
