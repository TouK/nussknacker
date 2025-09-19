package pl.touk.nussknacker.engine.process.scenariotesting

import pl.touk.nussknacker.engine.{ModelData, RuntimeMode}
import pl.touk.nussknacker.engine.api.{ProcessListener, ServiceInvoker}
import pl.touk.nussknacker.engine.api.component.{ComponentType, NodesDeploymentData}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.compile.nodecompilation.StaticComponentOutputValidationContextDeterminer
import pl.touk.nussknacker.engine.definition.component.{ComponentImplementationInvoker, NodeCompilationDependencies}
import pl.touk.nussknacker.engine.definition.model.ModelDefinition
import pl.touk.nussknacker.engine.process.compiler.{ComponentDefinitionContext, FlinkProcessCompilerDataFactory}
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer

object VerificationFlinkProcessCompilerDataFactory {

  def apply(modelData: ModelData): FlinkProcessCompilerDataFactory = {
    new FlinkProcessCompilerDataFactory(
      creator = modelData.configCreator,
      extractModelDefinition = modelData.extractModelDefinitionFun,
      modelConfig = modelData.modelConfig,
      runtimeMode = RuntimeMode.Live,
      configsFromProviderWithDictionaryEditor = modelData.additionalConfigsFromProvider,
      nodesData = NodesDeploymentData.empty,
      processListeners = List.empty,
    ) {

      // We don't want to open real, production listeners
      override protected def adjustListeners(defaults: List[ProcessListener]): List[ProcessListener] = Nil

      override protected def adjustDefinitions(
          originalModelDefinition: ModelDefinition,
          definitionContext: ComponentDefinitionContext
      ): ModelDefinition = {
        val outputValidationContextDeterminer = new StaticComponentOutputValidationContextDeterminer(
          GlobalVariablesPreparer(definitionContext.modelDefinitionWithClasses.modelDefinition.expressionConfig)
        )

        val transformedComponents = originalModelDefinition.components.components.map {
          case component if component.componentType == ComponentType.Source =>
            component.withImplementationInvoker(
              new StubbedComponentImplementationInvoker(component, outputValidationContextDeterminer) {
                override def transformOriginalInvocationResult(
                    originalInvocationResult: Any,
                    outputValidationContext: ValidationContext,
                    typingResult: TypingResult,
                    compilationDependencies: NodeCompilationDependencies,
                ): Any = {
                  new DataRecordsSource(List.empty, outputValidationContext, None)
                }
              }
            )
          case component if component.componentType == ComponentType.Service =>
            // We don't want to open services
            component.withImplementationInvoker(
              ComponentImplementationInvoker.dummyImplementationInvoker[ServiceInvoker]
            )
          case other => other
        }
        originalModelDefinition.copy(components =
          originalModelDefinition.components.copy(components = transformedComponents)
        )
      }
    }
  }

}
