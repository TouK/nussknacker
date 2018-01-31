package pl.touk.nussknacker.engine.management

import java.util.UUID

import argonaut.PrettyParams
import com.typesafe.config.ConfigFactory
import org.scalatest.{FlatSpec, Matchers}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Millis, Seconds, Span}
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.deployment.test.{NodeResult, TestData}
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller

import scala.concurrent.Await

class FlinkProcessTestRunnerSpec extends FlatSpec with Matchers with ScalaFutures with Eventually {

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(
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

    whenReady(processManager.test(processId, processData, TestData("terefere"))) { r =>
      r.nodeResults shouldBe Map(
        "startProcess" -> List(NodeResult(Context(s"$processId-startProcess-0-0").withVariable("input", "terefere"))),
        "nightFilter" -> List(NodeResult(Context(s"$processId-startProcess-0-0").withVariable("input", "terefere"))),
        "endSend" -> List(NodeResult(Context(s"$processId-startProcess-0-0").withVariable("input", "terefere")))
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

    val config = ConfigFactory.load()
    val processManager = FlinkProcessManager(config)

    val processData = ProcessMarshaller.toJson(process, PrettyParams.spaces2)


    val caught = intercept[IllegalArgumentException] {
      Await.result(processManager.test(processId, processData, TestData("terefere")), patienceConfig.timeout)
    }
    caught.getMessage shouldBe "Compilation errors: MissingParameters(Set(param1),$process)"
  }

}
