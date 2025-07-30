package pl.touk.nussknacker.engine.definition.component

import pl.touk.nussknacker.engine.api.{Context, Params}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.nussknacker.engine.api.util.{NotNothing, ReflectUtils}
import pl.touk.nussknacker.engine.definition.component.ComponentImplementationInvoker.ComponentImplementationSpecificInvocationContext
import pl.touk.nussknacker.engine.definition.component.dynamic.FinalStateValue

import java.lang.reflect.{InvocationHandler, Method, Proxy => JavaProxy}
import scala.concurrent.ExecutionContext
import scala.reflect.{classTag, ClassTag}

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

  def dummyImplementationInvoker[ComponentExecutor: NotNothing: ClassTag]: ComponentImplementationInvoker = {
    (_: Params, _: NodeCompilationDependencies, _: Option[ComponentImplementationSpecificInvocationContext]) =>
      // We create a dummy instance, because NodeCompiler even during scenario validation checks what class was returned
      ReflectUtils.createADummyInstanceOf[ComponentExecutor]
  }

  trait DummyImplementation

  sealed trait ComponentImplementationSpecificInvocationContext

  case class DynamicComponentInvocationContext(
      finalStateValue: FinalStateValue,
      outputValidationContext: ValidationContext
  ) extends ComponentImplementationSpecificInvocationContext

  case class LazyServiceInvocationContext(
      executionContext: ExecutionContext,
      collector: ServiceInvocationCollector,
      variablesContext: Context
  ) extends ComponentImplementationSpecificInvocationContext

}
