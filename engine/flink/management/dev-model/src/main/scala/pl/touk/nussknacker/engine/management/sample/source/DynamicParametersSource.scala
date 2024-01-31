package pl.touk.nussknacker.engine.management.sample.source

import org.apache.flink.api.common.typeinfo.TypeInformation
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{BaseDefinedParameter, NodeDependencyValue}
import pl.touk.nussknacker.engine.api.process.SourceFactory
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, Unknown}
import pl.touk.nussknacker.engine.flink.util.source.CollectionSource
import pl.touk.nussknacker.engine.management.sample.transformer.DynamicParametersMixin

object DynamicParametersSource extends SourceFactory with DynamicParametersMixin {

  override def implementation(
      params: Map[String, Any],
      dependencies: List[NodeDependencyValue],
      finalState: Option[State]
  ): AnyRef = {
    new CollectionSource[Any](List(TypedMap(params.filterNot(_._1 == choiceParamName))), None, Unknown)(
      TypeInformation.of(classOf[Any])
    )
  }

  override protected def result(
      validationContext: ValidationContext,
      otherParams: List[(String, BaseDefinedParameter)]
  )(implicit nodeId: NodeId): FinalResults = {
    val paramsTyping = otherParams.map { case (paramName, definedParam) => paramName -> definedParam.returnType }.toMap
    FinalResults.forValidation(validationContext)(
      _.withVariable("input", Typed.record(paramsTyping), paramName = None)
    )
  }

}
