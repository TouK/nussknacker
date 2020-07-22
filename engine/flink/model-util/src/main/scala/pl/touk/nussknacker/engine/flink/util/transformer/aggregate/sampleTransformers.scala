package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import java.util.concurrent.TimeUnit

import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ContextTransformation
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.editor.{LabeledExpression, SimpleEditor, SimpleEditorType}
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
      transformers.slidingTransformer(keyBy, aggregateBy, toAggregator(aggregatorType), windowDuration, variableName, explicitUidInStatefulOperators)
    }
  }

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
      transformers.slidingTransformer(keyBy, aggregateBy, toAggregator(aggregatorType), windowDuration, variableName, explicitUidInStatefulOperators)
    }
  }

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
      transformers.tumblingTransformer(keyBy, aggregateBy, toAggregator(aggregatorType), windowDuration, variableName, explicitUidInStatefulOperators)
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

  object SlidingAggregateTransformer extends CustomStreamTransformer with ExplicitUidInOperatorsSupport {

    @MethodToInvoke(returnType = classOf[AnyRef])
    def execute(@ParamName("keyBy") keyBy: LazyParameter[CharSequence],
                @ParamName("aggregator") aggregator: Aggregator,
                @ParamName("aggregateBy") aggregateBy: LazyParameter[AnyRef],
                @ParamName("windowLengthInSeconds") length: Long,
                @OutputVariableName variableName: String)(implicit nodeId: NodeId): ContextTransformation = {
      val windowDuration = Duration(length, TimeUnit.SECONDS)
      transformers.slidingTransformer(keyBy, aggregateBy, aggregator, windowDuration, variableName, explicitUidInStatefulOperators)
    }
  }
}
