package pl.touk.esp.engine.management

import java.util.UUID

import argonaut.PrettyParams
import com.typesafe.config.ConfigFactory
import org.scalatest.{FlatSpec, Matchers}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Millis, Seconds, Span}
import pl.touk.esp.engine.api.Context
import pl.touk.esp.engine.api.deployment.GraphProcess
import pl.touk.esp.engine.api.deployment.test.{NodeResult, TestData, TestResults}
import pl.touk.esp.engine.build.EspProcessBuilder
import pl.touk.esp.engine.marshall.ProcessMarshaller

import scala.concurrent.Await

class FlinkProcessTestRunnerSpec extends FlatSpec with Matchers with ScalaFutures with Eventually {

  override implicit val patienceConfig = PatienceConfig(
    timeout = Span(10, Seconds),
    interval = Span(100, Millis)
  )
  val ProcessMarshaller = new ProcessMarshaller


  it should "run process in test mode" in {
    val config = ConfigFactory.load()
    val processManager = FlinkProcessManager(config)

    val processId = UUID.randomUUID().toString

    val process = SampleProcess.prepareProcess(processId)
    val processData = ProcessMarshaller.toJson(process, PrettyParams.spaces2)


    val results = Await.result(processManager.test(processId, processData,
      TestData("terefere")), patienceConfig.timeout)

    results.nodeResults shouldBe Map(
      "startProcess" -> List(NodeResult(Context(s"$processId-startProcess-0-0", Map("input" -> "terefere")))),
      "nightFilter" -> List(NodeResult(Context(s"$processId-startProcess-0-0", Map("input" -> "terefere")))),
      "endSend" -> List(NodeResult(Context(s"$processId-startProcess-0-0", Map("input" -> "terefere"))))
    )
  }

  it should "return correct error messages" in {
    val processId = UUID.randomUUID().toString

    val process = EspProcessBuilder
      .id(processId)
      .exceptionHandler()
      .source("startProcess", "kafka-transaction")
      .sink("endSend", "sendSms")

    val config = ConfigFactory.load()
    val processManager = FlinkProcessManager(config)

    val processData = ProcessMarshaller.toJson(process, PrettyParams.spaces2)


    val caught = intercept[IllegalArgumentException] {
      Await.result(processManager.test(processId, processData, TestData("terefere")), patienceConfig.timeout)
    }
    caught.getMessage shouldBe "Compilation errors: MissingParameters(Set(param1),$process)"
  }
}
