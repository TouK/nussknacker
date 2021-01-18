package pl.touk.nussknacker.engine.process.compiler

import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.restartstrategy.RestartStrategies
import org.apache.flink.streaming.api.scala._
import pl.touk.nussknacker.engine.api.ProcessListener
import pl.touk.nussknacker.engine.api.exception.EspExceptionInfo
import pl.touk.nussknacker.engine.api.namespaces.ObjectNaming
import pl.touk.nussknacker.engine.api.process.{ProcessConfigCreator, ProcessObjectDependencies}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor
import pl.touk.nussknacker.engine.flink.api.exception.FlinkEspExceptionHandler
import pl.touk.nussknacker.engine.flink.util.source.EmptySource
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.modelconfig.ModelConfigToLoad

class VerificationFlinkProcessCompiler(process: EspProcess, executionConfig: ExecutionConfig,
                                       creator: ProcessConfigCreator, config: ModelConfigToLoad,
                                       objectNaming: ObjectNaming)
  extends StubbedFlinkProcessCompiler(process, creator, config, objectNaming) {

  override protected def listeners(processObjectDependencies: ProcessObjectDependencies): Seq[ProcessListener] = List()

  override protected def prepareService(service: DefinitionExtractor.ObjectWithMethodDef): DefinitionExtractor.ObjectWithMethodDef =
    overrideObjectWithMethod(service, (_, _, _, _) => null)

  override protected def prepareExceptionHandler(exceptionHandlerFactory: DefinitionExtractor.ObjectWithMethodDef)
    : DefinitionExtractor.ObjectWithMethodDef = overrideObjectWithMethod(exceptionHandlerFactory, (_, _, _, _) => new FlinkEspExceptionHandler {

    override def restartStrategy: RestartStrategies.RestartStrategyConfiguration = RestartStrategies.noRestart()

    override def handle(exceptionInfo: EspExceptionInfo[_ <: Throwable]): Unit = {}
  })

  override protected def prepareSourceFactory(sourceFactory: DefinitionExtractor.ObjectWithMethodDef): DefinitionExtractor.ObjectWithMethodDef
    = overrideObjectWithMethod(sourceFactory, (_, _, _, returnType) =>  new EmptySource[Object](returnType()))
}
