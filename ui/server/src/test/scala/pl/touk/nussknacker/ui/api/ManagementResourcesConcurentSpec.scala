package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import pl.touk.nussknacker.ui.api.helpers.EspItTest
import pl.touk.nussknacker.ui.sample.SampleProcess

class ManagementResourcesConcurentSpec extends FunSuite with ScalatestRouteTest
  with Matchers with ScalaFutures with OptionValues with BeforeAndAfterEach with BeforeAndAfterAll with EspItTest {

  implicit override val patienceConfig = PatienceConfig(timeout = scaled(Span(2, Seconds)), interval = scaled(Span(100, Millis)))

  //FIXME: can't test that. I give up for now

  ignore("not allow concurrent deployment of same process") {

    saveProcessAndAssertSuccess(SampleProcess.process.id, SampleProcess.process)

    def invokeDeploy =
      deployProcess(SampleProcess.process.id) ~> runRoute

    val firstRun = invokeDeploy
    invokeDeploy ~> check {
      status shouldBe StatusCodes.Conflict
    }
    firstRun ~> check {
      status shouldBe StatusCodes.OK
    }
    invokeDeploy ~> check {
      status shouldBe StatusCodes.OK
    }
  }

  ignore("not allow concurrent deployment and cancel of same process") {
    saveProcessAndAssertSuccess(SampleProcess.process.id, SampleProcess.process)

      val firstRun = deployProcess(SampleProcess.process.id) ~> runRoute
      cancelProcess(SampleProcess.process.id) ~> check {
        status shouldBe StatusCodes.Conflict
      }
      firstRun ~> check {
        status shouldBe StatusCodes.OK
      }
      cancelProcess(SampleProcess.process.id) ~> check {
        status shouldBe StatusCodes.OK
      }

  }

  ignore("not allow concurrent deployment of different processes") {
    saveProcessAndAssertSuccess(SampleProcess.process.id, SampleProcess.process)

    val secondId = SampleProcess.process.id + "-2"
    saveProcessAndAssertSuccess(secondId, SampleProcess.process)

      val firstRun = deployProcess(SampleProcess.process.id) ~> runRoute
      deployProcess(secondId) ~> check {
        status shouldBe StatusCodes.Conflict
      }
      runRoute ~> check {
        status shouldBe StatusCodes.OK
      }
  }



}
