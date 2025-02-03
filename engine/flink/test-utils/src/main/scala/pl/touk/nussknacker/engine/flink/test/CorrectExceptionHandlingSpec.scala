package pl.touk.nussknacker.engine.flink.test

import cats.data.NonEmptyList
import org.apache.flink.api.common.JobExecutionResult
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.scalatest.Suite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.StandardTimestampWatermarkHandler
import pl.touk.nussknacker.engine.flink.test.ScalatestMiniClusterJobStatusCheckingOps._
import pl.touk.nussknacker.engine.flink.util.source.CollectionSource
import pl.touk.nussknacker.engine.graph.node.SourceNode
import pl.touk.nussknacker.engine.testing.LocalModelData

import scala.jdk.CollectionConverters._

/*
  Extend this trait to test if exceptions are handled properly in operators/UDFs using LazyParameters
  ExceptionGenerator.throwFromString/Long generates expressions which will throw exceptions for prepared test data
 */
trait CorrectExceptionHandlingSpec extends FlinkSpec with Matchers {
  self: Suite =>

  protected def checkExceptions(
      components: List[ComponentDefinition]
  )(prepareScenario: (GraphBuilder[SourceNode], ExceptionGenerator) => NonEmptyList[SourceNode]): Unit = {
    val generator                 = new ExceptionGenerator
    val NonEmptyList(start, rest) = prepareScenario(GraphBuilder.source("source", "source"), generator)
    val scenario                  = ScenarioBuilder.streaming("test").sources(start, rest: _*)
    val sourceComponentDefinition = ComponentDefinition("source", SamplesComponent.create(generator.count))

    flinkMiniCluster.withDetachedStreamExecutionEnvironment { env =>
      val executionResult = runScenario(
        env,
        LocalModelData(config, sourceComponentDefinition :: components),
        scenario
      )
      flinkMiniCluster.waitForJobIsFinished(executionResult.getJobID)
    }
    RecordingExceptionConsumer.exceptionsFor(runId) should have length generator.count
  }

  /**
    * TestFlinkRunner should be invoked, it's not accessible in this module
    */
  protected def runScenario(
      env: StreamExecutionEnvironment,
      modelData: ModelData,
      scenario: CanonicalProcess
  ): JobExecutionResult

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

object SamplesComponent extends Serializable {

  def create(samplesCount: Int): SourceFactory = {
    def createSource = {
      val samples = (0 to samplesCount)
        .map(sample => (0 to samplesCount).map(idx => if (sample == idx) 0 else 1).toList.asJava)
        .toList
      CollectionSource(
        samples,
        Some(StandardTimestampWatermarkHandler.afterEachEvent[AnyRef]((_: AnyRef) => 1L)),
        Typed.fromDetailedType[java.util.List[Int]]
      )
    }
    SourceFactory.noParamUnboundedStreamFactory(createSource, Typed.fromDetailedType[java.util.List[Int]])
  }

}
