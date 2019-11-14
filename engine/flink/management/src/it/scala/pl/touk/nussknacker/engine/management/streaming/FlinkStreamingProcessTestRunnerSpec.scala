package pl.touk.nussknacker.engine.management.streaming

import java.util.{Collections, UUID}

import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.deployment.TestProcess.{NodeResult, ResultContext, TestData}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.management.FlinkStreamingProcessManagerProvider
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.engine.util.config.ScalaBinaryConfig

import scala.concurrent.Await

class FlinkStreamingProcessTestRunnerSpec extends FlatSpec with Matchers with ScalaFutures with Eventually {

  private val classPath: String = s"./engine/flink/management/sample/target/scala-${ScalaBinaryConfig.scalaBinaryVersion}/managementSample.jar"

  private val config = ConfigFactory.load()
    .withValue("processConfig.kafka.kafkaAddress", ConfigValueFactory.fromAnyRef("kafka:1234"))
    .withValue("flinkConfig.classpath", ConfigValueFactory.fromIterable(Collections.singletonList(classPath)))

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(
    timeout = Span(10, Seconds),
    interval = Span(100, Millis)
  )

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
      .emptySink("endSend", "sendSms")

    val processManager = FlinkStreamingProcessManagerProvider.defaultProcessManager(config)

    val processData = ProcessMarshaller.toJson(ProcessCanonizer.canonize(process)).spaces2


    val caught = intercept[IllegalArgumentException] {
      Await.result(processManager.test(ProcessName(processId), processData, TestData("terefere"), _ => null), patienceConfig.timeout)
    }
    caught.getMessage shouldBe "Compilation errors: MissingParameters(Set(param1),$exceptionHandler)"
  }

}
