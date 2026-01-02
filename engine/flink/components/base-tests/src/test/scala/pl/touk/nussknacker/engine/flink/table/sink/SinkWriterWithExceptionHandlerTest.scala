package pl.touk.nussknacker.engine.flink.table.sink

import com.typesafe.config.ConfigFactory
import org.apache.flink.api.common.functions.FlatMapFunction
import org.apache.flink.api.connector.sink2.{Sink, SinkWriter, WriterInitContext}
import org.scalatest.{BeforeAndAfterAll, LoneElement}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.{Context, LazyParameter, MethodToInvoke, ParamName, ValueWithContext}
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.process.SinkFactory
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.flink.api.FlinkEngineContextOps._
import pl.touk.nussknacker.engine.flink.api.exception.ExceptionHandler
import pl.touk.nussknacker.engine.flink.api.process.{
  BasicFlinkSinkV2,
  FlinkCustomNodeContext,
  FlinkLazyParameterFunctionHelper
}
import pl.touk.nussknacker.engine.flink.minicluster.FlinkMiniClusterFactory
import pl.touk.nussknacker.engine.flink.table.utils.ModelClassLoaderSimulationSuite
import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner
import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner.FlinkTestScenarioRunnerExt
import pl.touk.nussknacker.engine.process.FlinkJobConfig.ExecutionMode
import pl.touk.nussknacker.engine.process.helpers.TestResultsHolder
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.test.{PatientScalaFutures, ValidatedValuesDetailedMessage}

import scala.annotation.nowarn

class SinkWriterWithExceptionHandlerTest
    extends AnyFunSuite
    with Matchers
    with PatientScalaFutures
    with LoneElement
    with ValidatedValuesDetailedMessage
    with BeforeAndAfterAll
    with ModelClassLoaderSimulationSuite {

  private lazy val sinkComponent: ComponentDefinition = ComponentDefinition(name = "sinkV2", component = CollectSink)
  private lazy val flinkMiniClusterWithServices       = FlinkMiniClusterFactory.createUnitTestsMiniClusterWithServices()

  private lazy val runner: FlinkTestScenarioRunner =
    TestScenarioRunner
      .flinkBased(ConfigFactory.empty(), flinkMiniClusterWithServices)
      .withExecutionMode(ExecutionMode.Streaming)
      .withExtraComponents(List(sinkComponent))
      .build()

  override protected def afterAll(): Unit = {
    super.afterAll()
    flinkMiniClusterWithServices.close()
  }

  test("SinkV2 handles exceptions properly when using ExceptionHandler") {
    val scenario = ScenarioBuilder
      .streaming("test")
      .source("source", "source")
      .emptySink("sinkV2", "sinkV2", "value" -> "#input".spel)

    runner.runWithData(scenario, List(1, 2, 3, 0, 4))

    // FIXME: https://github.com/TouK/nussknacker/issues/8679
    // val result = runner.runWithData(scenario, List(1, 2, 3, 0, 4))
    // result.validValue.errors shouldBe 1

    eventually {
      SinkResultHolder.buffer.results.length mustBe 4
    }
  }

}

object CollectSink extends SinkFactory {

  @MethodToInvoke
  def invoke(@ParamName("value") value: LazyParameter[Integer]): BasicFlinkSinkV2 = new BasicFlinkSinkV2 {
    override type Value = Integer

    override def valueFunction(
        helper: FlinkLazyParameterFunctionHelper
    ): FlatMapFunction[Context, ValueWithContext[Value]] =
      helper.lazyMapFunction(value)

    override def toFlinkFunction(flinkNodeContext: FlinkCustomNodeContext): Sink[Integer] = new Sink[Integer] {

      override def createWriter(context: WriterInitContext): SinkWriter[Integer] =
        new SinkWriter[Integer] {

          private val exceptionHandler: ExceptionHandler =
            flinkNodeContext.exceptionHandlerPreparer.narrowToWriterInitCtx(context)

          override def write(element: Integer, context: SinkWriter.Context): Unit = {
            val result = exceptionHandler.handling[Double](None, Context.dummy)(
              if (element != 0) (1.0 / element) else throw new IllegalArgumentException("Dividing by zero")
            )

            result match {
              case Some(reciprocalValue) => SinkResultHolder.buffer.add(reciprocalValue)
              case None                  => ()
            }
          }

          override def flush(endOfInput: Boolean): Unit = ()

          override def close(): Unit = exceptionHandler.close()
        }

      @nowarn("cat=deprecation")
      def createWriter(context: Sink.InitContext): SinkWriter[Integer] =
        throw new UnsupportedOperationException("Not implemented")

    }
  }

}

object SinkResultHolder {
  @transient var buffer = new TestResultsHolder[Double]
}
