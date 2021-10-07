package pl.touk.nussknacker.engine.flink.test

import com.typesafe.config.{Config, ConfigFactory}
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.scala._
import org.scalatest.{Matchers, Suite}
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.api.{CustomStreamTransformer, Service}
import pl.touk.nussknacker.engine.build.{EspProcessBuilder, ProcessMetaDataBuilder}
import pl.touk.nussknacker.engine.flink.api.process.FlinkSourceFactory
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.StandardTimestampWatermarkHandler
import pl.touk.nussknacker.engine.flink.util.sink.EmptySink
import pl.touk.nussknacker.engine.flink.util.source.CollectionSource
import pl.touk.nussknacker.engine.api.graph.EspProcess
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator

import java.util.UUID
import scala.jdk.CollectionConverters.seqAsJavaListConverter

/*
  Extend this trait to test if exceptions are handled properly in operators/UDFs using LazyParameters
  ExceptionGenerator.throwFromString/Long generates expressions which will throw exceptions for prepared test data
 */
trait CorrectExceptionHandlingSpec extends FlinkSpec with Matchers {
  self: Suite =>

  protected def checkExceptions(configCreator: ProcessConfigCreator, config: Config = ConfigFactory.empty())
                               (prepareScenario: (ProcessMetaDataBuilder#ProcessExceptionHandlerBuilder#ProcessGraphBuilder, ExceptionGenerator) => EspProcess): Unit = {

    val generator = new ExceptionGenerator
    val scenario = prepareScenario(EspProcessBuilder.id("test").exceptionHandler().source("source", "source"), generator)
    val runId = UUID.randomUUID().toString
    val recordingCreator = new RecordingConfigCreator(configCreator, generator.count, runId)

    val env = flinkMiniCluster.createExecutionEnvironment()
    registerInEnvironment(env, LocalModelData(config, recordingCreator), scenario)

    env.executeAndWaitForFinished("test")()
    RecordingExceptionHandler.dataFor(runId) should have length generator.count
  }

  /**
    * TestFlinkRunner should be invoked, it's not accessible in this module
    */
  protected def registerInEnvironment(env: MiniClusterExecutionEnvironment, modelData: ModelData, scenario: EspProcess): Unit

  class ExceptionGenerator {

    var count = 0

    def throwFromString(): String = {
      s"'' + (${throwFromLong()})"
    }

    def throwFromLong(): String = {
      val output = s"1 / #input[$count]"
      count += 1
      output
    }
  }
}

class RecordingConfigCreator(delegate: ProcessConfigCreator, samplesCount: Int, runId: String) extends EmptyProcessConfigCreator {

  private val samples = (0 to samplesCount).map(sample => (0 to samplesCount).map(idx => if (sample == idx) 0 else 1).toList.asJava).toList

  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory[_]]] = {
    val timestamps = StandardTimestampWatermarkHandler.afterEachEvent[AnyRef]((_: AnyRef) => 1L)
    Map("source" -> WithCategories(FlinkSourceFactory.noParam(CollectionSource(new ExecutionConfig, samples, Some(timestamps),
      Typed.fromDetailedType[java.util.List[Int]]))))
  }

  override def exceptionHandlerFactory(processObjectDependencies: ProcessObjectDependencies): ExceptionHandlerFactory = {
    ExceptionHandlerFactory.noParams(_ => new RecordingExceptionHandler(runId))
  }

  override def customStreamTransformers(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[CustomStreamTransformer]]
    = delegate.customStreamTransformers(processObjectDependencies)

  override def services(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]]
    = delegate.services(processObjectDependencies)

  override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]]
    = delegate.sinkFactories(processObjectDependencies) + ("empty" -> WithCategories(SinkFactory.noParam(EmptySink)))

  override def expressionConfig(processObjectDependencies: ProcessObjectDependencies): ExpressionConfig = delegate.expressionConfig(processObjectDependencies)
}
