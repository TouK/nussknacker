package pl.touk.nussknacker.engine.management.sample.transformer

import pl.touk.nussknacker.engine.api.context.transformation.{DefinedEagerParameter, BaseDefinedParameter, FailedToDefineParameter, NodeDependencyValue, SingleInputGenericNodeTransformation}
import pl.touk.nussknacker.engine.api.definition.{FixedExpressionValue, FixedValuesParameterEditor, NodeDependency, Parameter, TypedNodeDependency}
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, NodeId}
import pl.touk.nussknacker.engine.api.context.ValidationContext

trait DynamicParametersMixin extends SingleInputGenericNodeTransformation[AnyRef] {

  override type State = Nothing

  protected val choiceParamName = "communicationType"

  private val choiceParam = Parameter[String](choiceParamName).copy(
    editor = Some(FixedValuesParameterEditor(List(
      FixedExpressionValue("'SMS'", "sms"),
      FixedExpressionValue("'MAIL'", "mail")
    )))
  )

  private val paramsMap = Map(
    "SMS" -> List(Parameter[String]("Number"), Parameter[String]("Text")),
    "MAIL" -> List(Parameter[String]("Address"), Parameter[String]("Subject"), Parameter[String]("Text"))
  )

  override def contextTransformation(context: ValidationContext,
                                     dependencies: List[NodeDependencyValue])(implicit nodeId: NodeId): this.NodeTransformationDefinition = {
    case TransformationStep(Nil, _) => NextParameters(initialParameters)
    case TransformationStep((`choiceParamName`, DefinedEagerParameter(value: String, _)) :: Nil, None) =>
      paramsMap.get(value).map(NextParameters(_)).getOrElse(NextParameters(Nil, List(CustomNodeError(s"Unknown type: ${value}", Some(choiceParamName)))))
    case TransformationStep((`choiceParamName`, FailedToDefineParameter) :: Nil, None) =>
      result(context, Nil)
    case TransformationStep((`choiceParamName`, _) :: otherParams, None)  =>
      result(context, otherParams)
  }

  protected def result(validationContext: ValidationContext, otherParams: List[(String, BaseDefinedParameter)])(implicit nodeId: NodeId): FinalResults = {
    FinalResults(validationContext)
  }

  override def initialParameters: List[Parameter] = List(choiceParam)

  override def nodeDependencies: List[NodeDependency] = List(TypedNodeDependency(classOf[MetaData]))

}
