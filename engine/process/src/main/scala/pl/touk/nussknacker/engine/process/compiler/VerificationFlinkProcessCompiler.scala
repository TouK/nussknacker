package pl.touk.nussknacker.engine.process.compiler

import com.typesafe.config.Config
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.restartstrategy.RestartStrategies
import pl.touk.nussknacker.engine.api.ProcessListener
import pl.touk.nussknacker.engine.api.exception.EspExceptionInfo
import pl.touk.nussknacker.engine.api.process.ProcessConfigCreator
import pl.touk.nussknacker.engine.definition.DefinitionExtractor
import pl.touk.nussknacker.engine.flink.api.exception.FlinkEspExceptionHandler
import pl.touk.nussknacker.engine.flink.util.source.{CollectionSource, EmptySource}
import pl.touk.nussknacker.engine.graph.EspProcess
import org.apache.flink.streaming.api.scala._

class VerificationFlinkProcessCompiler(process: EspProcess, executionConfig: ExecutionConfig,
                                       creator: ProcessConfigCreator, config: Config)
  extends StubbedFlinkProcessCompiler(process, creator, config) {

  override protected def listeners(): Seq[ProcessListener] = List()

  override protected def prepareService(service: DefinitionExtractor.ObjectWithMethodDef): DefinitionExtractor.ObjectWithMethodDef =
    overrideObjectWithMethod(service, (_, _) => null)

  override protected def prepareExceptionHandler(exceptionHandlerFactory: DefinitionExtractor.ObjectWithMethodDef)
    : DefinitionExtractor.ObjectWithMethodDef = overrideObjectWithMethod(exceptionHandlerFactory, (_, _) => new FlinkEspExceptionHandler {

    override def restartStrategy: RestartStrategies.RestartStrategyConfiguration = RestartStrategies.noRestart()

    override def handle(exceptionInfo: EspExceptionInfo[_ <: Throwable]): Unit = {}
  })

  override protected def prepareSourceFactory(sourceFactory: DefinitionExtractor.ObjectWithMethodDef): Option[DefinitionExtractor.ObjectWithMethodDef]
    = Option(overrideObjectWithMethod(sourceFactory, (_, _) =>  new EmptySource[Object]))
}
