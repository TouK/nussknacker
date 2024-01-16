package pl.touk.nussknacker.engine.process.compiler

import org.apache.flink.api.common.typeinfo.TypeInformation
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.process.{ComponentUseCase, ProcessObjectDependencies}
import pl.touk.nussknacker.engine.api.typed.typing.{TypingResult, Unknown}
import pl.touk.nussknacker.engine.api.{NodeId, ProcessListener}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.definition.component.ComponentDefinitionWithLogic
import pl.touk.nussknacker.engine.flink.util.source.EmptySource

object VerificationFlinkProcessCompilerDataFactory {

  def apply(process: CanonicalProcess, modelData: ModelData): FlinkProcessCompilerDataFactory = {
    new StubbedFlinkProcessCompilerDataFactory(
      process,
      modelData.configCreator,
      modelData.extractModelDefinitionFun,
      modelData.modelConfig,
      modelData.objectNaming,
      componentUseCase = ComponentUseCase.Validation
    ) {

      override protected def adjustListeners(
          defaults: List[ProcessListener],
          modelDependencies: ProcessObjectDependencies
      ): List[ProcessListener] = Nil

      override protected def prepareService(
          service: ComponentDefinitionWithLogic,
          context: ComponentDefinitionContext
      ): ComponentDefinitionWithLogic =
        service.withComponentLogic(new StubbedComponentLogic(service) {
          override def handleRun(impl: Any, typingResult: TypingResult, nodeId: NodeId): Any = null
        })

      override protected def prepareSourceFactory(
          sourceFactory: ComponentDefinitionWithLogic,
          context: ComponentDefinitionContext
      ): ComponentDefinitionWithLogic =
        sourceFactory.withComponentLogic(new StubbedComponentLogic(sourceFactory) {
          override def handleRun(impl: Any, typingResult: TypingResult, nodeId: NodeId): Any =
            new EmptySource[Object](typingResult)(TypeInformation.of(classOf[Object]))
        })

    }
  }

}
