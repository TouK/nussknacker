package pl.touk.nussknacker.engine.process.compiler

import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.process.{ComponentUseCase, ProcessObjectDependencies}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.api.{NodeId, ProcessListener}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.definition.component.ComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.flink.util.source.EmptySource

object VerificationFlinkProcessCompilerDataFactory {

  def apply(process: CanonicalProcess, modelData: ModelData): FlinkProcessCompilerDataFactory = {
    new StubbedFlinkProcessCompilerDataFactory(
      process,
      modelData.configCreator,
      modelData.extractModelDefinitionFun,
      modelData.modelConfig,
      modelData.namingStrategy,
      componentUseCase = ComponentUseCase.Validation
    ) {

      override protected def adjustListeners(
          defaults: List[ProcessListener],
          modelDependencies: ProcessObjectDependencies
      ): List[ProcessListener] = Nil

      override protected def prepareService(
          service: ComponentDefinitionWithImplementation,
          context: ComponentDefinitionContext
      ): ComponentDefinitionWithImplementation =
        service.withImplementationInvoker(new StubbedComponentImplementationInvoker(service) {
          override def handleInvoke(impl: Any, typingResult: TypingResult, nodeId: NodeId): Any = null
        })

      override protected def prepareSourceFactory(
          sourceFactory: ComponentDefinitionWithImplementation,
          context: ComponentDefinitionContext
      ): ComponentDefinitionWithImplementation =
        sourceFactory.withImplementationInvoker(new StubbedComponentImplementationInvoker(sourceFactory) {
          override def handleInvoke(impl: Any, typingResult: TypingResult, nodeId: NodeId): Any =
            EmptySource(typingResult)
        })

    }
  }

}
