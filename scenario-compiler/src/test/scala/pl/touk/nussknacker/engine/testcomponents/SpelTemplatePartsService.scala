package pl.touk.nussknacker.engine.testcomponents

import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.TemplateRenderedPart.{RenderedLiteral, RenderedSubExpression}
import pl.touk.nussknacker.engine.api.component.NodesDeploymentData.NodeDeploymentData
import pl.touk.nussknacker.engine.api.context.{OutputVar, ValidationContext}
import pl.touk.nussknacker.engine.api.context.transformation.{
  DefinedLazyParameter,
  NodeDependencyValue,
  SingleInputDynamicComponent
}
import pl.touk.nussknacker.engine.api.definition.{
  NodeDependency,
  OutputVariableNameDependency,
  Parameter,
  SpelTemplateParameterEditor
}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.ComponentUseContext
import pl.touk.nussknacker.engine.api.test.InvocationCollectors
import pl.touk.nussknacker.engine.api.typed.typing

import scala.concurrent.{ExecutionContext, Future}

object SpelTemplatePartsService extends EagerService with SingleInputDynamicComponent[ServiceInvoker] {

  private val spelTemplateParameterName = ParameterName("template")

  private val spelTemplateParameter = Parameter
    .optional[String](spelTemplateParameterName)
    .copy(
      isLazyParameter = true,
      editor = Some(SpelTemplateParameterEditor)
    )

  override type State = Any

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): SpelTemplatePartsService.ContextTransformationDefinition = {
    case TransformationStep(Nil, _) => NextParameters(List(spelTemplateParameter))
    case TransformationStep((`spelTemplateParameterName`, DefinedLazyParameter(_)) :: Nil, _) =>
      FinalResults.forValidation(context, List.empty)(validation =
        ctx =>
          ctx.withVariable(
            OutputVariableNameDependency.extract(dependencies),
            typing.Typed[String],
            Some(ParameterName(OutputVar.VariableFieldName))
          )
      )
  }

  override def implementation(
      params: Params,
      dependencies: List[NodeDependencyValue],
      finalState: Option[Any]
  ): ServiceInvoker = new ServiceInvoker {

    override def invoke(context: Context)(
        implicit ec: ExecutionContext,
        collector: InvocationCollectors.ServiceInvocationCollector,
        componentUseContext: ComponentUseContext,
    ): Future[Any] = {
      val templateResult =
        params.extractOrEvaluateLazyParamUnsafe[TemplateEvaluationResult](spelTemplateParameterName, context)
      val result = templateResult.renderedParts.map {
        case RenderedLiteral(value)       => s"[$value]-literal"
        case RenderedSubExpression(value) => s"[$value]-subexpression"
      }.mkString
      Future.successful(result)
    }

  }

  override def nodeDependencies: List[NodeDependency] = List(OutputVariableNameDependency)
}
