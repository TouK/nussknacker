package pl.touk.nussknacker.engine.management.sample.source

import org.apache.flink.api.scala._
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{BaseDefinedParameter, NodeDependencyValue}
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.api.typed.typing.{TypedObjectTypingResult, Unknown}
import pl.touk.nussknacker.engine.flink.api.process.FlinkSourceFactory
import pl.touk.nussknacker.engine.flink.util.source.CollectionSource
import pl.touk.nussknacker.engine.management.sample.transformer.DynamicParametersMixin

object DynamicParametersSource extends FlinkSourceFactory[AnyRef] with DynamicParametersMixin {

  override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalState: Option[State]): AnyRef = {
    new CollectionSource[Any](StreamExecutionEnvironment.getExecutionEnvironment.getConfig,
      List(TypedMap(params.filterNot(_._1 == choiceParamName))), None, Unknown)
  }

  override protected def result(validationContext: ValidationContext,
                                otherParams: List[(String, BaseDefinedParameter)])(implicit nodeId: NodeId): FinalResults = {

    validationContext.withVariable("input", TypedObjectTypingResult(
      otherParams.toMap.mapValues(_.returnType)
    ), paramName = None).fold(a => FinalResults(validationContext, a.toList), FinalResults(_))
  }
}
