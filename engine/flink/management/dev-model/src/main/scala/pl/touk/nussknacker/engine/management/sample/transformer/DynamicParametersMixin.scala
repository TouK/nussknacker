package pl.touk.nussknacker.engine.management.sample.transformer

import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{
  BaseDefinedParameter,
  DefinedEagerParameter,
  FailedToDefineParameter,
  NodeDependencyValue,
  SingleInputDynamicComponent
}
import pl.touk.nussknacker.engine.api.definition.{
  FixedExpressionValue,
  FixedValuesParameterEditor,
  NodeDependency,
  Parameter,
  TypedNodeDependency
}
import pl.touk.nussknacker.engine.api.parameter.ParameterName

trait DynamicParametersMixin extends SingleInputDynamicComponent[AnyRef] {

  override type State = Nothing

  protected val choiceParamName: ParameterName = ParameterName("communicationType")

  private val choiceParam = Parameter[String](choiceParamName).copy(
    editor = Some(
      FixedValuesParameterEditor(
        List(
          FixedExpressionValue("'SMS'", "sms"),
          FixedExpressionValue("'MAIL'", "mail")
        )
      )
    )
  )

  private val paramsMap = Map(
    "SMS" -> List(Parameter[String](ParameterName("Number")), Parameter[String](ParameterName("Text"))),
    "MAIL" -> List(
      Parameter[String](ParameterName("Address")),
      Parameter[String](ParameterName("Subject")),
      Parameter[String](ParameterName("Text"))
    )
  )

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): this.ContextTransformationDefinition = {
    case TransformationStep(Nil, _) => NextParameters(List(choiceParam))
    case TransformationStep((`choiceParamName`, DefinedEagerParameter(value: String, _)) :: Nil, None) =>
      paramsMap
        .get(value)
        .map(NextParameters(_))
        .getOrElse(NextParameters(Nil, List(CustomNodeError(s"Unknown type: $value", Some(choiceParamName)))))
    case TransformationStep((`choiceParamName`, FailedToDefineParameter(_)) :: Nil, None) =>
      result(context, Nil)
    case TransformationStep((`choiceParamName`, _) :: otherParams, None) =>
      result(context, otherParams)
  }

  protected def result(validationContext: ValidationContext, otherParams: List[(ParameterName, BaseDefinedParameter)])(
      implicit nodeId: NodeId
  ): FinalResults = {
    FinalResults(validationContext)
  }

  override def nodeDependencies: List[NodeDependency] = List(TypedNodeDependency[MetaData])

}
