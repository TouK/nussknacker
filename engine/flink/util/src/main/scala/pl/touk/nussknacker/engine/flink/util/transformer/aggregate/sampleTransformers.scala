package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import java.util.concurrent.TimeUnit

import pl.touk.nussknacker.engine.api.context.ContextTransformation
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.editor.{LabeledExpression, SimpleEditor, SimpleEditorType}

import scala.concurrent.duration.Duration

object sampleTransformers {

  object SimpleSlidingAggregateTransformer extends CustomStreamTransformer {

    @MethodToInvoke(returnType = classOf[AnyRef])
    def execute(@ParamName("keyBy") keyBy: LazyParameter[String],
                @SimpleEditor(
                  `type` = SimpleEditorType.FIXED_VALUES_EDITOR,
                  possibleValues = Array(
                    new LabeledExpression(expression = "'Max'", label = "Max"),
                    new LabeledExpression(expression = "'Min'", label = "Min"),
                    new LabeledExpression(expression = "'ApproximateSetCardinality'", label = "ApproximateSetCardinality"),
                    new LabeledExpression(expression = "'Set'", label = "Set")
                  )
                )
                @ParamName("aggregator") aggregatorType: String,
                @ParamName("aggregateBy") aggregateBy: LazyParameter[AnyRef],
                @ParamName("windowLengthInSeconds") length: Long,
                @OutputVariableName variableName: String)(implicit nodeId: NodeId): ContextTransformation = {
      val windowDuration = Duration(length, TimeUnit.SECONDS)
      transformers.slidingTransformer(keyBy, aggregateBy, toAggregator(aggregatorType), windowDuration, variableName)
    }

    private def toAggregator(aggregatorType: String) = aggregatorType match {
      case "Max" => aggregates.MaxAggregator
      case "Min" => aggregates.MinAggregator
      case "Set" => aggregates.SetAggregator
      case "Sum" => aggregates.SumAggregator
      case "ApproximateSetCardinality" => HyperLogLogPlusAggregator()
      case _ => throw new IllegalArgumentException(s"Unknown aggregate type: $aggregatorType")
    }
  }

  object SlidingAggregateTransformer extends CustomStreamTransformer {

    @MethodToInvoke(returnType = classOf[AnyRef])
    def execute(@ParamName("keyBy") keyBy: LazyParameter[String],
                @ParamName("aggregator") aggregator: Aggregator,
                @ParamName("aggregateBy") aggregateBy: LazyParameter[AnyRef],
                @ParamName("windowLengthInSeconds") length: Long,
                @OutputVariableName variableName: String)(implicit nodeId: NodeId): ContextTransformation = {
      val windowDuration = Duration(length, TimeUnit.SECONDS)
      transformers.slidingTransformer(keyBy, aggregateBy, aggregator, windowDuration, variableName)
    }
  }
}
