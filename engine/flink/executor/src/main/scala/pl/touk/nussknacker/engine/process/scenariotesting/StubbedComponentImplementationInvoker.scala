package pl.touk.nussknacker.engine.process.scenariotesting

import cats.data.Validated.Valid
import pl.touk.nussknacker.engine.api.Params
import pl.touk.nussknacker.engine.api.context.{ContextTransformation, ScenarioCompilationErrors, ValidationContext}
import pl.touk.nussknacker.engine.api.typed.ReturningType
import pl.touk.nussknacker.engine.api.typed.typing.{TypingResult, Unknown}
import pl.touk.nussknacker.engine.compile.nodecompilation.StaticComponentOutputValidationContextDeterminer
import pl.touk.nussknacker.engine.definition.component.{
  ComponentDefinitionWithImplementation,
  ComponentImplementationInvoker,
  NodeCompilationDependencies
}
import pl.touk.nussknacker.engine.definition.component.ComponentImplementationInvoker.{
  ComponentImplementationSpecificInvocationContext,
  DynamicComponentInvocationContext
}
import pl.touk.nussknacker.engine.definition.component.dynamic.DynamicComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.definition.component.methodbased.MethodBasedComponentDefinitionWithImplementation
import shapeless.syntax.typeable.typeableOps

abstract class StubbedComponentImplementationInvoker(
    protected val componentDefinition: ComponentDefinitionWithImplementation,
    outputValidationContextDeterminer: StaticComponentOutputValidationContextDeterminer
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
        val outputValidationContext =
          determineOutputValidationContext(
            contextTransformation.implementation,
            compilationDependencies,
            invocationContext
          )
        contextTransformation.copy(implementation =
          withReturnType(contextTransformation.implementation)(
            transformOriginalInvocationResult(
              contextTransformation.implementation,
              outputValidationContext,
              _,
              compilationDependencies
            )
          )
        )
      case componentExecutor =>
        val outputValidationContext =
          determineOutputValidationContext(componentExecutor, compilationDependencies, invocationContext)
        withReturnType(componentExecutor)(
          transformOriginalInvocationResult(
            componentExecutor,
            outputValidationContext,
            _,
            compilationDependencies
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

  private def determineOutputValidationContext(
      originalSource: Any,
      compilationDependencies: NodeCompilationDependencies,
      invocationContext: Option[ComponentImplementationSpecificInvocationContext]
  ) = {
    (componentDefinition, invocationContext) match {
      case (_, Some(DynamicComponentInvocationContext(_, outputValidationContext))) => outputValidationContext
      case (staticComponent: MethodBasedComponentDefinitionWithImplementation, _) =>
        outputValidationContextDeterminer
          .contextAfterNode(
            nodeData = compilationDependencies.nodeData,
            customNodeIsEndingNode = None,
            staticComponent = staticComponent,
            validComponentExecutor = Valid(originalSource),
            inputContext = compilationDependencies.inputValidationContext
          )(compilationDependencies.jobData)
          .valueOr { errNel =>
            throw new IllegalStateException(
              "Compilation errors during output validation context determining",
              ScenarioCompilationErrors(errNel.toList)
            )
          }
      case _ =>
        throw new IllegalStateException(
          s"Illegal combination of component [$componentDefinition] and invocation context [$invocationContext]"
        )
    }
  }

  def transformOriginalInvocationResult(
      originalInvocationResult: Any,
      outputValidationContext: ValidationContext,
      // TypingResult is for source-specific test data stubbing purpose where we still use ReturningType instead of ContextTransformation
      typingResult: TypingResult,
      compilationDependencies: NodeCompilationDependencies
  ): Any

}
