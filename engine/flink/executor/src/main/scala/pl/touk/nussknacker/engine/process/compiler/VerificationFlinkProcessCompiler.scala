package pl.touk.nussknacker.engine.process.compiler

import com.typesafe.config.Config
import org.apache.flink.streaming.api.scala._
import pl.touk.nussknacker.engine.api.ProcessListener
import pl.touk.nussknacker.engine.api.namespaces.ObjectNaming
import pl.touk.nussknacker.engine.api.process.{ProcessConfigCreator, ProcessObjectDependencies, RunMode}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor
import pl.touk.nussknacker.engine.flink.util.source.EmptySource
import pl.touk.nussknacker.engine.graph.EspProcess

class VerificationFlinkProcessCompiler(process: EspProcess,
                                       creator: ProcessConfigCreator,
                                       processConfig: Config,
                                       objectNaming: ObjectNaming)
  extends StubbedFlinkProcessCompiler(process, creator, processConfig, diskStateBackendSupport = true, objectNaming, runMode = RunMode.Normal) {

  override protected def listeners(processObjectDependencies: ProcessObjectDependencies): Seq[ProcessListener] = List()

  override protected def prepareService(service: DefinitionExtractor.ObjectWithMethodDef): DefinitionExtractor.ObjectWithMethodDef =
    overrideObjectWithMethod(service, (_, _) => null)

  override protected def prepareSourceFactory(sourceFactory: DefinitionExtractor.ObjectWithMethodDef): DefinitionExtractor.ObjectWithMethodDef
    = overrideObjectWithMethod(sourceFactory, (_, returnType) =>  new EmptySource[Object](returnType))
}
