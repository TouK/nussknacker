package pl.touk.nussknacker.engine.process.compiler

import org.apache.flink.streaming.api.scala._
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.ProcessListener
import pl.touk.nussknacker.engine.api.process.{ComponentUseCase, ProcessObjectDependencies}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor
import pl.touk.nussknacker.engine.flink.util.source.EmptySource
import pl.touk.nussknacker.engine.graph.EspProcess

class VerificationFlinkProcessCompiler(process: EspProcess, modelData: ModelData)
  extends StubbedFlinkProcessCompiler(process, modelData, diskStateBackendSupport = true, componentUseCase = ComponentUseCase.Validation) {

  override protected def listeners(processObjectDependencies: ProcessObjectDependencies): Seq[ProcessListener] = List()

  override protected def prepareService(service: DefinitionExtractor.ObjectWithMethodDef): DefinitionExtractor.ObjectWithMethodDef =
    overrideObjectWithMethod(service, (_, _) => null)

  override protected def prepareSourceFactory(sourceFactory: DefinitionExtractor.ObjectWithMethodDef): DefinitionExtractor.ObjectWithMethodDef
    = overrideObjectWithMethod(sourceFactory, (_, returnType) =>  new EmptySource[Object](returnType))
}
