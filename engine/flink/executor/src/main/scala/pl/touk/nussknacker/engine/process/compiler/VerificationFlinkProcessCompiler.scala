package pl.touk.nussknacker.engine.process.compiler

import com.typesafe.config.Config
import org.apache.flink.api.common.typeinfo.TypeInformation
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.{NodeId, ProcessListener}
import pl.touk.nussknacker.engine.api.namespaces.ObjectNaming
import pl.touk.nussknacker.engine.api.process.{ComponentUseCase, ProcessConfigCreator, ProcessObjectDependencies}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.Unknown
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.definition.DefinitionExtractor
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ModelDefinitionWithTypes
import pl.touk.nussknacker.engine.flink.util.source.EmptySource

object VerificationFlinkProcessCompiler {

  def apply(modelData: ModelData, process: CanonicalProcess): FlinkProcessCompiler = {
    VerificationFlinkProcessCompiler(
      modelData.modelDefinitionWithTypes,
      modelData.configCreator,
      modelData.processConfig,
      modelData.objectNaming,
      process)
  }

  def apply(modelDefinitionWithTypes: ModelDefinitionWithTypes,
            creator: ProcessConfigCreator,
            processConfig: Config,
            objectNaming: ObjectNaming,
            process: CanonicalProcess): FlinkProcessCompiler = {
    val stubbedDefinitions = new ComponentsImplementationStubber(process) {
      override protected def prepareService(service: DefinitionExtractor.ObjectWithMethodDef): DefinitionExtractor.ObjectWithMethodDef = {
        service.withImplementationInvoker(new StubbedComponentImplementationInvoker(service) {
          override def handleInvoke(impl: Any, typingResult: Option[typing.TypingResult], nodeId: NodeId): Any = null
        })
      }

      override protected def prepareSourceFactory(sourceFactory: DefinitionExtractor.ObjectWithMethodDef): DefinitionExtractor.ObjectWithMethodDef =
        sourceFactory.withImplementationInvoker(new StubbedComponentImplementationInvoker(sourceFactory) {
          override def handleInvoke(impl: Any, returnType: Option[typing.TypingResult], nodeId: NodeId): Any =
            new EmptySource[Object](returnType.getOrElse(Unknown))(TypeInformation.of(classOf[Object]))
        })
    }.stubImplementations(modelDefinitionWithTypes)
    new FlinkProcessCompiler(stubbedDefinitions, creator, processConfig, diskStateBackendSupport = true, objectNaming, componentUseCase = ComponentUseCase.Validation) {
      override protected def adjustListeners(defaults: List[ProcessListener], processObjectDependencies: ProcessObjectDependencies): List[ProcessListener] = List.empty
    }
  }
}
