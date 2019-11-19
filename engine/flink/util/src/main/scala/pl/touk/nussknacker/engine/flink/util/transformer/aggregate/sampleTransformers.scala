package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import pl.touk.nussknacker.engine.api.context.ContextTransformation
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api._

import scala.concurrent.duration.Duration

object sampleTransformers {

  object SlidingAggregateTransformer extends CustomStreamTransformer {

    @MethodToInvoke(returnType = classOf[AnyRef])
    def execute(@ParamName("keyBy") keyBy: LazyParameter[String],
                @ParamName("length") length: String,
                @ParamName("aggregator") aggregator: Aggregator,
                @ParamName("aggregateBy") aggregateBy: LazyParameter[AnyRef],
                @OutputVariableName variableName: String)(implicit nodeId: NodeId): ContextTransformation =
        transformers.slidingTransformer(keyBy, aggregateBy, aggregator, Duration(length), variableName)
  }


  object AggregateTumblingTransformer extends CustomStreamTransformer {

    @MethodToInvoke(returnType = classOf[AnyRef])
    def execute(@ParamName("keyBy") keyBy: LazyParameter[String],
                @ParamName("length") length: String,
                @ParamName("aggregator") aggregator: Aggregator,
                @ParamName("aggregateBy") aggregateBy: LazyParameter[AnyRef],
                @OutputVariableName variableName: String)(implicit nodeId: NodeId): ContextTransformation =
      transformers.tumblingTransformer(keyBy, aggregateBy, aggregator, Duration(length), variableName)
  }

}
