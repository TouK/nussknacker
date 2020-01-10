package pl.touk.nussknacker.engine.process.functional

import java.util.Date

import com.typesafe.config.ConfigFactory
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.flink.test.{FlinkTestConfiguration, StoppableExecutionEnvironment}
import pl.touk.nussknacker.engine.kafka.KafkaSpec
import pl.touk.nussknacker.engine.process.FlinkStreamingProcessRegistrar
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.process.helpers.ProcessTestHelpers
import pl.touk.nussknacker.engine.process.helpers.ProcessTestHelpers.processInvoker
import pl.touk.nussknacker.engine.process.helpers.SampleNodes.{MockService, SimpleRecord}
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.test.VeryPatientScalaFutures

class KafkaSignalsSpec extends FunSuite with Matchers with BeforeAndAfterAll with KafkaSpec with VeryPatientScalaFutures {

  private val env: StoppableExecutionEnvironment = StoppableExecutionEnvironment(FlinkTestConfiguration.configuration())

  override protected def afterAll(): Unit = {
    env.stop()
    super.afterAll()
  }

  test("signals don't cause watermarks to stop") {
    kafkaClient.createTopic(ProcessTestHelpers.signalTopic)
    MockService.clear()

    val process =
      EspProcessBuilder
        .id("proc1")
        .exceptionHandler()
        .source("id", "input")
        .customNodeNoOutput("signal", "signalReader")
        .customNode("cid", "count", "transformWithTime", "seconds" -> "1")
        .processorEnd("out", "logService", "all" -> "#count")

    def record(time: Long) = SimpleRecord(time.toString, 0, "", new Date(time))

    val creator = processInvoker.prepareCreator(List(
      record(1000),
      record(1200),
      record(2000)
    ), kafkaConfig)

    val modelData = LocalModelData(ConfigFactory.load(), creator)
    FlinkStreamingProcessRegistrar(new FlinkProcessCompiler(modelData), modelData.processConfig)
      .register(new StreamExecutionEnvironment(env), process, ProcessVersion.empty)

    env.withJobRunning(process.id) {
      eventually {
        MockService.data shouldBe List(2, 1)
      }
    }

  }

}
