package pl.touk.nussknacker.engine.management

import java.util.UUID

import com.typesafe.config.ConfigFactory
import org.scalatest.{FlatSpec, Matchers}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Millis, Seconds, Span}
import pl.touk.nussknacker.engine.api.deployment.TestProcess.{NodeResult, ResultContext, TestData}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller

import scala.concurrent.Await

class FlinkProcessTestRunnerSpec extends FlatSpec with Matchers with ScalaFutures with Eventually {

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(
    timeout = Span(10, Seconds),
    interval = Span(100, Millis)
  )

  it should "run process in test mode" in {
    val config = ConfigFactory.load()
    val processManager = FlinkProcessManagerProvider.defaultProcessManager(config)

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

    val config = ConfigFactory.load()
    val processManager = FlinkProcessManagerProvider.defaultProcessManager(config)

    val processData = ProcessMarshaller.toJson(ProcessCanonizer.canonize(process)).spaces2


    val caught = intercept[IllegalArgumentException] {
      Await.result(processManager.test(ProcessName(processId), processData, TestData("terefere"), _ => null), patienceConfig.timeout)
    }
    caught.getMessage shouldBe "Compilation errors: MissingParameters(Set(param1),$process)"
  }

}
