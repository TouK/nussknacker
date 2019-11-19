package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.scalatest._
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.helpers.EspItTest

class SettingsResourcesSpec extends FunSpec with ScalatestRouteTest with FailFastCirceSupport
  with Matchers with PatientScalaFutures with EitherValues with BeforeAndAfterEach with BeforeAndAfterAll with EspItTest {

  //Values are exists at test/resources/application.conf
  val intervalTimeProcesses = 20000
  val intervalTimeHealthCheck = 30000

  it("should return base intervalSettings") {
    getSettings ~> check {
      status shouldBe StatusCodes.OK
      val responseSettings = responseAs[UISettings]
      val data = responseSettings.features

      data.intervalTimeSettings.processes shouldBe intervalTimeProcesses
      data.intervalTimeSettings.healthCheck shouldBe intervalTimeHealthCheck
    }
  }
}
