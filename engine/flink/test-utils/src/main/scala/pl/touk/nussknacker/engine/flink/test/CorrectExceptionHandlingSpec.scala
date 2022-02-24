package pl.touk.nussknacker.engine.flink.test

import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.scala._
import org.scalatest.{Matchers, Suite}
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.process.{EmptyProcessConfigCreator, _}
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.api.{CustomStreamTransformer, Service}
import pl.touk.nussknacker.engine.build.{EspProcessBuilder, ProcessMetaDataBuilder}
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.StandardTimestampWatermarkHandler
import pl.touk.nussknacker.engine.flink.util.sink.EmptySink
import pl.touk.nussknacker.engine.flink.util.source.CollectionSource
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.testing.LocalModelData

import scala.jdk.CollectionConverters.seqAsJavaListConverter

/*
  Extend this trait to test if exceptions are handled properly in operators/UDFs using LazyParameters
  ExceptionGenerator.throwFromString/Long generates expressions which will throw exceptions for prepared test data
 */
trait CorrectExceptionHandlingSpec extends FlinkSpec with Matchers {
  self: Suite =>

  protected def checkExceptions(configCreator: ProcessConfigCreator)
                               (prepareScenario: (ProcessMetaDataBuilder#ProcessGraphBuilder, ExceptionGenerator) => EspProcess): Unit = {
    val generator = new ExceptionGenerator
    val scenario = prepareScenario(EspProcessBuilder.id("test").source("source", "source"), generator)
    val recordingCreator = new RecordingConfigCreator(configCreator, generator.count)

    val env = flinkMiniCluster.createExecutionEnvironment()
    registerInEnvironment(env, LocalModelData(config, recordingCreator), scenario)

    env.executeAndWaitForFinished("test")()
    RecordingExceptionConsumer.dataFor(runId) should have length generator.count
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

class RecordingConfigCreator(delegate: ProcessConfigCreator, samplesCount: Int) extends EmptyProcessConfigCreator {

  private val samples = (0 to samplesCount).map(sample => (0 to samplesCount).map(idx => if (sample == idx) 0 else 1).toList.asJava).toList

  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory]] = {
    val timestamps = StandardTimestampWatermarkHandler.afterEachEvent[AnyRef]((_: AnyRef) => 1L)
    val inputType = Typed.fromDetailedType[java.util.List[Int]]
    Map("source" -> WithCategories(SourceFactory.noParam(CollectionSource(new ExecutionConfig, samples, Some(timestamps), inputType
      ), inputType)))
  }

  override def customStreamTransformers(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[CustomStreamTransformer]]
    = delegate.customStreamTransformers(processObjectDependencies)

  override def services(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]]
    = delegate.services(processObjectDependencies)

  override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]]
    = delegate.sinkFactories(processObjectDependencies) + ("empty" -> WithCategories(SinkFactory.noParam(EmptySink)))

  override def expressionConfig(processObjectDependencies: ProcessObjectDependencies): ExpressionConfig = delegate.expressionConfig(processObjectDependencies)
}
