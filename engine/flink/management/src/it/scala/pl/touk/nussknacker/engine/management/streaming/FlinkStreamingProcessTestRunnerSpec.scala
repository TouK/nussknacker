package pl.touk.nussknacker.engine.management.streaming

import java.util.{Collections, UUID}

import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.deployment.TestProcess.{NodeResult, ResultContext, TestData}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.management.FlinkStreamingProcessManagerProvider
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.engine.util.config.ScalaMajorVersionConfig
import pl.touk.nussknacker.test.VeryPatientScalaFutures

import scala.concurrent.Await

class FlinkStreamingProcessTestRunnerSpec extends FlatSpec with Matchers with VeryPatientScalaFutures {

  private val classPath: String = s"./engine/flink/management/sample/target/scala-${ScalaMajorVersionConfig.scalaMajorVersion}/managementSample.jar"

  private val config = ConfigFactory.load()
    .withValue("processConfig.kafka.kafkaAddress", ConfigValueFactory.fromAnyRef("kafka:1234"))
    .withValue("flinkConfig.classpath", ConfigValueFactory.fromIterable(Collections.singletonList(classPath)))

  it should "run process in test mode" in {
    val processManager = FlinkStreamingProcessManagerProvider.defaultProcessManager(config)

    val processId = UUID.randomUUID().toString

    val process = SampleProcess.prepareProcess(processId)
    val processData = ProcessMarshaller.toJson(ProcessCanonizer.canonize(process)).spaces2

    whenReady(processManager.test(ProcessName(processId), processData, TestData("terefere"), identity)) { r =>
      r.nodeResults shouldBe Map(
        "startProcess" -> List(NodeResult(ResultContext(s"$processId-startProcess-0-0", Map("input" -> "terefere")))),
        "nightFilter" -> List(NodeResult(ResultContext(s"$processId-startProcess-0-0", Map("input" -> "terefere")))),
        "endSend" -> List(NodeResult(ResultContext(s"$processId-startProcess-0-0", Map("input" -> "terefere"))))
      )
    }
  }

  it should "return correct error messages" in {
    val processId = UUID.randomUUID().toString

    val process = EspProcessBuilder
      .id(processId)
      .exceptionHandler()
      .source("startProcess", "kafka-transaction")
      .emptySink("endSend", "sendSmsNotExist")

    val processManager = FlinkStreamingProcessManagerProvider.defaultProcessManager(config)

    val processData = ProcessMarshaller.toJson(ProcessCanonizer.canonize(process)).spaces2


    val caught = intercept[IllegalArgumentException] {
      Await.result(processManager.test(ProcessName(processId), processData, TestData("terefere"), _ => null), patienceConfig.timeout)
    }
    caught.getMessage shouldBe "Compilation errors: MissingSinkFactory(sendSmsNotExist,endSend)"
  }

}
