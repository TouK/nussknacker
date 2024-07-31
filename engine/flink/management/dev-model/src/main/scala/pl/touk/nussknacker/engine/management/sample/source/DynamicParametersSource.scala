package pl.touk.nussknacker.engine.management.sample.source

import pl.touk.nussknacker.engine.api.component.UnboundedStreamComponent
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{BaseDefinedParameter, NodeDependencyValue}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.SourceFactory
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, Unknown}
import pl.touk.nussknacker.engine.api.{NodeId, Params}
import pl.touk.nussknacker.engine.flink.util.source.CollectionSource
import pl.touk.nussknacker.engine.management.sample.transformer.DynamicParametersMixin

object DynamicParametersSource extends SourceFactory with DynamicParametersMixin with UnboundedStreamComponent {

  override def implementation(
      params: Params,
      dependencies: List[NodeDependencyValue],
      finalState: Option[State]
  ): AnyRef = {
    val paramsTyping = params.nameToValueMap.filterNot(_._1 == choiceParamName).map { case (paramName, value) =>
      paramName.value -> value
    }
    new CollectionSource[Any](List(TypedMap(paramsTyping)), None, Unknown)
  }

  override protected def result(
      validationContext: ValidationContext,
      otherParams: List[(ParameterName, BaseDefinedParameter)]
  )(implicit nodeId: NodeId): FinalResults = {
    val paramsTyping = otherParams.map { case (paramName, definedParam) =>
      paramName.value -> definedParam.returnType
    }.toMap
    FinalResults.forValidation(validationContext)(
      _.withVariable("input", Typed.record(paramsTyping), paramName = None)
    )
  }

}
