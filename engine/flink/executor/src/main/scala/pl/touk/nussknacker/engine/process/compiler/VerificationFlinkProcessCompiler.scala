package pl.touk.nussknacker.engine.process.compiler

import com.typesafe.config.Config
import org.apache.flink.api.common.typeinfo.TypeInformation
import pl.touk.nussknacker.engine.api.ProcessListener
import pl.touk.nussknacker.engine.api.namespaces.ObjectNaming
import pl.touk.nussknacker.engine.api.process.{ComponentUseCase, ProcessConfigCreator, ProcessObjectDependencies}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.definition.DefinitionExtractor
import pl.touk.nussknacker.engine.flink.util.source.EmptySource

class VerificationFlinkProcessCompiler(process: CanonicalProcess,
                                       creator: ProcessConfigCreator,
                                       processConfig: Config,
                                       objectNaming: ObjectNaming)
  extends StubbedFlinkProcessCompiler(process, creator, processConfig, diskStateBackendSupport = true, objectNaming, componentUseCase = ComponentUseCase.Validation) {

  override protected def adjustListeners(defaults: List[ProcessListener], processObjectDependencies: ProcessObjectDependencies): List[ProcessListener] = Nil

  override protected def prepareService(service: DefinitionExtractor.ObjectWithMethodDef): DefinitionExtractor.ObjectWithMethodDef =
    overrideObjectWithMethod(service, (_, _, _) => null)

  override protected def prepareSourceFactory(sourceFactory: DefinitionExtractor.ObjectWithMethodDef): DefinitionExtractor.ObjectWithMethodDef =
    overrideObjectWithMethod(
      sourceFactory,
      (_, returnType, _) =>  new EmptySource[Object](returnType)(TypeInformation.of(classOf[Object]))
    )
}
