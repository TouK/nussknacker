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
import pl.touk.nussknacker.engine.process.scenariotesting.StubbedComponentImplementationInvoker.returnType
import shapeless.syntax.typeable.typeableOps

abstract class StubbedComponentImplementationInvoker(
    protected val original: ComponentImplementationInvoker,
    originalDefinitionReturnType: Option[TypingResult]
) extends ComponentImplementationInvoker {

  def this(componentDefinition: ComponentDefinitionWithImplementation) = {
    this(
      componentDefinition.implementationInvoker,
      returnType(componentDefinition)
    )
  }

  override def invokeMethod(
      params: Params,
      compilationDependencies: NodeCompilationDependencies,
      invocationContext: Option[ComponentImplementationSpecificInvocationContext]
  ): Any = {
    def transform(impl: Any): Any = {
      // Correct TypingResult is important for method based components, because even for testing and verification
      // purpose, ImplementationInvoker is used also to determine output types. Dynamic components don't use it during
      // scenario validation so we pass Unknown for them
      val typingResult =
        impl
          .cast[ReturningType]
          .map(rt => rt.returnType)
          .orElse(originalDefinitionReturnType)
          .getOrElse(Unknown)

      transformOriginalInvocationResult(impl, typingResult, compilationDependencies)
    }

    val originalCompilationResult = invokeOriginalInvoker(params, compilationDependencies, invocationContext)
    originalCompilationResult match {
      case contextTransformation: ContextTransformation =>
        contextTransformation.copy(implementation = transform(contextTransformation.implementation))
      case componentExecutor =>
        transform(componentExecutor)
    }
  }

  protected def invokeOriginalInvoker(
      params: Params,
      compilationDependencies: NodeCompilationDependencies,
      invocationContext: Option[ComponentImplementationSpecificInvocationContext]
  ): Any =
    original.invokeMethod(params, compilationDependencies, invocationContext)

  def transformOriginalInvocationResult(
      impl: Any,
      typingResult: TypingResult,
      compilationDependencies: NodeCompilationDependencies
  ): Any

}

object StubbedComponentImplementationInvoker {

  private def returnType(componentDefinition: ComponentDefinitionWithImplementation): Option[TypingResult] = {
    componentDefinition match {
      case methodBasedDefinition: MethodBasedComponentDefinitionWithImplementation => methodBasedDefinition.returnType
      case _: DynamicComponentDefinitionWithImplementation                         => None
    }
  }

}
