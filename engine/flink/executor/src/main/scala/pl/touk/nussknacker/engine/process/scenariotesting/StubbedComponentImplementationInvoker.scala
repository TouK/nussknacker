package pl.touk.nussknacker.engine.process.scenariotesting

import pl.touk.nussknacker.engine.api.Params
import pl.touk.nussknacker.engine.api.context.ContextTransformation
import pl.touk.nussknacker.engine.api.typed.ReturningType
import pl.touk.nussknacker.engine.api.typed.typing.{TypingResult, Unknown}
import pl.touk.nussknacker.engine.definition.component.{
  ComponentDefinitionWithImplementation,
  ComponentImplementationInvoker,
  NodeCompilationDependencies
}
import pl.touk.nussknacker.engine.definition.component.ComponentImplementationInvoker.ComponentImplementationSpecificInvocationContext
import pl.touk.nussknacker.engine.definition.component.dynamic.DynamicComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.definition.component.methodbased.MethodBasedComponentDefinitionWithImplementation
import shapeless.syntax.typeable.typeableOps

abstract class StubbedComponentImplementationInvoker(
    protected val componentDefinition: ComponentDefinitionWithImplementation
) extends ComponentImplementationInvoker {

  private lazy val originalDefinitionReturnType: Option[TypingResult] = {
    componentDefinition match {
      case methodBasedDefinition: MethodBasedComponentDefinitionWithImplementation => methodBasedDefinition.returnType
      case _: DynamicComponentDefinitionWithImplementation                         => None
    }
  }

  override def invokeMethod(
      params: Params,
      compilationDependencies: NodeCompilationDependencies,
      invocationContext: Option[ComponentImplementationSpecificInvocationContext]
  ): Any = {
    // Correct TypingResult is important for method-based components, because even for testing and verification
    // purpose, ImplementationInvoker is used also to determine output types. Dynamic components don't use it during
    // scenario validation so we can pass Unknown for them
    def withReturnType[T](invocationResult: Any)(f: TypingResult => T): Any = {
      f(
        invocationResult
          .cast[ReturningType]
          .map(rt => rt.returnType)
          .orElse(originalDefinitionReturnType)
          .getOrElse(Unknown)
      )
    }

    val originalInvocationResult = invokeOriginalInvoker(params, compilationDependencies, invocationContext)
    originalInvocationResult match {
      case contextTransformation: ContextTransformation =>
        contextTransformation.copy(implementation =
          withReturnType(contextTransformation.implementation)(
            transformOriginalInvocationResult(
              contextTransformation.implementation,
              originalInvocationResultWasWrappedInContextTransformation = true,
              _,
              compilationDependencies,
              invocationContext
            )
          )
        )
      case componentExecutor =>
        withReturnType(componentExecutor)(
          transformOriginalInvocationResult(
            componentExecutor,
            originalInvocationResultWasWrappedInContextTransformation = false,
            _,
            compilationDependencies,
            invocationContext
          )
        )
    }
  }

  protected def invokeOriginalInvoker(
      params: Params,
      compilationDependencies: NodeCompilationDependencies,
      invocationContext: Option[ComponentImplementationSpecificInvocationContext]
  ): Any =
    componentDefinition.implementationInvoker.invokeMethod(params, compilationDependencies, invocationContext)

  def transformOriginalInvocationResult(
      originalInvocationResult: Any,
      originalInvocationResultWasWrappedInContextTransformation: Boolean,
      typingResult: TypingResult,
      compilationDependencies: NodeCompilationDependencies,
      invocationContext: Option[ComponentImplementationSpecificInvocationContext]
  ): Any

}
