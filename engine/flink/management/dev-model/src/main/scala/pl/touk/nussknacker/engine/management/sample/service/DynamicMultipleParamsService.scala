package pl.touk.nussknacker.engine.management.sample.service

import pl.touk.nussknacker.engine.api.{Context, EagerService, NodeId, Params, ServiceInvoker}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{
  DefinedEagerParameter,
  NodeDependencyValue,
  SingleInputDynamicComponent
}
import pl.touk.nussknacker.engine.api.definition.{
  FixedExpressionValue,
  FixedValuesParameterEditor,
  NodeDependency,
  Parameter
}
import pl.touk.nussknacker.engine.api.definition.FixedExpressionValue.nullFixedValue
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.api.test.InvocationCollectors
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.graph.expression.Expression

import scala.concurrent.{ExecutionContext, Future}

object DynamicMultipleParamsService extends EagerService with SingleInputDynamicComponent[ServiceInvoker] {

  override type State = Unit

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): DynamicMultipleParamsService.ContextTransformationDefinition = {
    case TransformationStep(Nil, _) =>
      val fooParam = Parameter(ParameterName("foo"), Typed[String]).copy(editor =
        Some(
          FixedValuesParameterEditor(
            List(
              nullFixedValue,
              FixedExpressionValue("'fooValueFromConfig'", "From Config"),
              FixedExpressionValue("'other'", "Other")
            )
          )
        )
      )
      NextParameters(List(fooParam))
    case TransformationStep((ParameterName("foo"), DefinedEagerParameter(_, _)) :: Nil, _) =>
      NextParameters(List(Parameter(ParameterName("bar"), Typed[String])))
    case TransformationStep(
          (ParameterName("foo"), DefinedEagerParameter(fooValue, _)) :: (
            ParameterName("bar"),
            DefinedEagerParameter(barValue: String, _)
          ) :: Nil,
          _
        ) =>
      NextParameters(
        List(
          Parameter(ParameterName("baz"), Typed[String])
            .copy(defaultValue = Some(Expression.spel(s"'$fooValue' + '-' + '$barValue'")))
        )
      )
    case TransformationStep(
          (ParameterName("foo"), DefinedEagerParameter(_, _)) ::
          (ParameterName("bar"), DefinedEagerParameter(_, _)) ::
          (ParameterName("baz"), DefinedEagerParameter(_, _)) :: Nil,
          _
        ) =>
      FinalResults(context)
  }

  override def implementation(
      params: Params,
      dependencies: List[NodeDependencyValue],
      finalState: Option[State]
  ): ServiceInvoker = {
    new ServiceInvoker {
      override def invoke(context: Context)(
          implicit ec: ExecutionContext,
          collector: InvocationCollectors.ServiceInvocationCollector,
          componentUseCase: ComponentUseCase
      ): Future[Any] = ???
    }
  }

  override def nodeDependencies: List[NodeDependency] = List.empty

}
