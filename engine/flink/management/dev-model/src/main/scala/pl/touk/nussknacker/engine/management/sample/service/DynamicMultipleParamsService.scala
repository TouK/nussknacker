package pl.touk.nussknacker.engine.management.sample.service

import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.{Context, ContextId, EagerService, NodeId, Params, ServiceInvoker}
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
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.api.test.InvocationCollectors
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.api.definition.FixedExpressionValue.nullFixedValue
import pl.touk.nussknacker.engine.graph.expression.Expression

import scala.concurrent.{ExecutionContext, Future}

object DynamicMultipleParamsService extends EagerService with SingleInputDynamicComponent[ServiceInvoker] {

  override type State = Unit

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): DynamicMultipleParamsService.ContextTransformationDefinition = {
    case TransformationStep(Nil, _) =>
      val fooParam = Parameter("foo", Typed[String]).copy(editor =
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
    case TransformationStep(("foo", DefinedEagerParameter(_, _)) :: Nil, _) =>
      NextParameters(List(Parameter("bar", Typed[String])))
    case TransformationStep(
          ("foo", DefinedEagerParameter(fooValue, _)) :: ("bar", DefinedEagerParameter(barValue: String, _)) :: Nil,
          _
        ) =>
      NextParameters(
        List(
          Parameter("baz", Typed[String]).copy(defaultValue = Some(Expression.spel(s"'$fooValue' + '-' + '$barValue'")))
        )
      )
    case TransformationStep(
          ("foo", DefinedEagerParameter(fooValue, _)) :: ("bar", DefinedEagerParameter(_, _)) :: (
            "baz",
            DefinedEagerParameter(_, _)
          ) :: Nil,
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
      override def run(context: Context)(
          implicit ec: ExecutionContext,
          collector: InvocationCollectors.ServiceInvocationCollector,
          componentUseCase: ComponentUseCase
      ): Future[Any] = ???
    }
  }

  override def nodeDependencies: List[NodeDependency] = List.empty

}
