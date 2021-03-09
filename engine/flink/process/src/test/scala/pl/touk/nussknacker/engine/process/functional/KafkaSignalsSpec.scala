package pl.touk.nussknacker.engine.process.functional

import java.util.Date
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.DeploymentData
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.kafka.KafkaSpec
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.process.helpers.ProcessTestHelpers
import pl.touk.nussknacker.engine.process.helpers.SampleNodes.{MockService, SimpleRecord}
import pl.touk.nussknacker.engine.process.ExecutionConfigPreparer
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.test.VeryPatientScalaFutures

class KafkaSignalsSpec extends FunSuite with Matchers with ProcessTestHelpers with KafkaSpec with VeryPatientScalaFutures {

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

    val creator = ProcessTestHelpers.prepareCreator(List(
      record(1000),
      record(1200),
      record(2000)
    ), config)


    val env = flinkMiniCluster.createExecutionEnvironment()
    val modelData = LocalModelData(config, creator)
    FlinkProcessRegistrar(new FlinkProcessCompiler(modelData), modelData.processConfig, ExecutionConfigPreparer.unOptimizedChain(modelData))
      .register(new StreamExecutionEnvironment(env), process, ProcessVersion.empty, DeploymentData.empty)

    env.withJobRunning(process.id) {
      eventually {
        MockService.data shouldBe List(2, 1)
      }
    }
  }

}
