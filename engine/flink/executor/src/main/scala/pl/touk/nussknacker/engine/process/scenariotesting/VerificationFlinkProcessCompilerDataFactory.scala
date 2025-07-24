package pl.touk.nussknacker.engine.process.scenariotesting

import pl.touk.nussknacker.engine.{ModelConfig, ModelData, RuntimeMode}
import pl.touk.nussknacker.engine.api.ProcessListener
import pl.touk.nussknacker.engine.api.component.NodesDeploymentData
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.definition.component.{
  ComponentDefinitionWithImplementation,
  NodeCompilationDependencies
}
import pl.touk.nussknacker.engine.flink.util.source.EmptySource
import pl.touk.nussknacker.engine.process.compiler.{ComponentDefinitionContext, FlinkProcessCompilerDataFactory}

object VerificationFlinkProcessCompilerDataFactory {

  def apply(process: CanonicalProcess, modelData: ModelData): FlinkProcessCompilerDataFactory = {
    new StubbedFlinkProcessCompilerDataFactory(
      process,
      modelData.configCreator,
      modelData.extractModelDefinitionFun,
      modelData.modelConfig,
      runtimeMode = RuntimeMode.Live,
      modelData.additionalConfigsFromProvider,
      NodesDeploymentData.empty,
      List.empty,
    ) {

      override protected def adjustListeners(
          defaults: List[ProcessListener],
          modelConfig: ModelConfig
      ): List[ProcessListener] = Nil

      override protected def prepareService(
          service: ComponentDefinitionWithImplementation,
          context: ComponentDefinitionContext
      ): ComponentDefinitionWithImplementation =
        service.withImplementationInvoker(new StubbedComponentImplementationInvoker(service) {
          override def transformOriginalInvocationResult(
              impl: Any,
              typingResult: TypingResult,
              compilationDependencies: NodeCompilationDependencies
          ): Any = null
        })

      override protected def prepareSourceFactory(
          sourceFactory: ComponentDefinitionWithImplementation,
          context: ComponentDefinitionContext
      ): ComponentDefinitionWithImplementation =
        sourceFactory.withImplementationInvoker(new StubbedComponentImplementationInvoker(sourceFactory) {
          override def transformOriginalInvocationResult(
              impl: Any,
              typingResult: TypingResult,
              compilationDependencies: NodeCompilationDependencies
          ): Any =
            EmptySource(typingResult)
        })

    }
  }

}
