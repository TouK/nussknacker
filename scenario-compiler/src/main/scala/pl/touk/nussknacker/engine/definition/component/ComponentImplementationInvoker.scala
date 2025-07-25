package pl.touk.nussknacker.engine.definition.component

import pl.touk.nussknacker.engine.api.{Context, CustomStreamTransformer, Params, Service}
import pl.touk.nussknacker.engine.api.process.{Sink, Source}
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.nussknacker.engine.definition.component.ComponentImplementationInvoker.ComponentImplementationSpecificInvocationContext
import pl.touk.nussknacker.engine.definition.component.dynamic.FinalStateValue

import scala.concurrent.ExecutionContext

// Purpose of this class is to allow to invoke Component's implementation. It is encapsulated to the separated class to make
// stubbing and other post-processing easier. Most Components are just a factories that creates "Executors".
// The situation is different for non-eager Services where Component is an Executor, so invokeMethod is run for each request
trait ComponentImplementationInvoker extends Serializable {

  def invokeMethod(
      params: Params,
      compilationDependencies: NodeCompilationDependencies,
      invocationContext: Option[ComponentImplementationSpecificInvocationContext]
  ): Any

  final def transformResult(f: Any => Any): ComponentImplementationInvoker =
    (
        params: Params,
        compilationDependencies: NodeCompilationDependencies,
        invocationContext: Option[ComponentImplementationSpecificInvocationContext]
    ) => {
      val originalResult =
        ComponentImplementationInvoker.this.invokeMethod(params, compilationDependencies, invocationContext)
      f(originalResult)
    }

}

object ComponentImplementationInvoker {

  val dumbImplementationInvoker: ComponentImplementationInvoker = {
    // We return null, because this instance can be casted to Source/Sink/etc during component compilation.
    // when invoker is used in context when we provide only definition of a component (see ModelDefinitionBuilder)
    (_: Params, _: NodeCompilationDependencies, _: Option[ComponentImplementationSpecificInvocationContext]) => null
  }

  sealed trait ComponentImplementationSpecificInvocationContext

  case class DynamicComponentInvocationContext(finalStateValue: FinalStateValue)
      extends ComponentImplementationSpecificInvocationContext

  case class LazyServiceInvocationContext(
      executionContext: ExecutionContext,
      collector: ServiceInvocationCollector,
      variablesContext: Context
  ) extends ComponentImplementationSpecificInvocationContext

}
