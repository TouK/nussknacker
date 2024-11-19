package pl.touk.nussknacker.engine.testcomponents

import pl.touk.nussknacker.engine.api.LazyParameter.TemplateLazyParameter
import pl.touk.nussknacker.engine.api.LazyParameter.TemplateLazyParameter.EvaluableExpressionPart._
import pl.touk.nussknacker.engine.api.{Context, EagerService, NodeId, Params, ServiceInvoker}
import pl.touk.nussknacker.engine.api.context.{OutputVar, ValidationContext}
import pl.touk.nussknacker.engine.api.context.transformation.{
  DefinedLazyParameter,
  NodeDependencyValue,
  SingleInputDynamicComponent
}
import pl.touk.nussknacker.engine.api.definition.{
  NodeDependency,
  OutputVariableNameDependency,
  ParameterDeclaration,
  SpelTemplateParameterEditor
}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.api.test.InvocationCollectors
import pl.touk.nussknacker.engine.api.typed.typing

import scala.concurrent.{ExecutionContext, Future}

object SpelTemplateAstOperationService extends EagerService with SingleInputDynamicComponent[ServiceInvoker] {

  private val spelTemplateParameter = ParameterDeclaration
    .lazyOptional[String](ParameterName("template"))
    .withCreator(modify =
      _.copy(
        editor = Some(SpelTemplateParameterEditor)
      )
    )

  override type State = Any

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): SpelTemplateAstOperationService.ContextTransformationDefinition = {
    case TransformationStep(Nil, _) => NextParameters(List(spelTemplateParameter.createParameter()))
    case TransformationStep((ParameterName("template"), DefinedLazyParameter(_)) :: Nil, _) =>
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
        componentUseCase: ComponentUseCase
    ): Future[Any] = {
      val lazyParam = spelTemplateParameter
        .extractValueUnsafe(params)
        .asInstanceOf[TemplateLazyParameter[String]]
      val result = lazyParam.parts.map {
        case Literal(value)        => s"[$value]-literal"
        case template: Placeholder => s"[${template.evaluate(context)}]-templated"
      }.mkString
      Future.successful(result)
    }

  }

  override def nodeDependencies: List[NodeDependency] = List(OutputVariableNameDependency)
}
