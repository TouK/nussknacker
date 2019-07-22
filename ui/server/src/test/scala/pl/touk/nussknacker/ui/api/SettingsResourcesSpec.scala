package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import pl.touk.nussknacker.ui.api.helpers.EspItTest

class SettingsResourcesSpec extends FunSpec with ScalatestRouteTest
  with Matchers with ScalaFutures with EitherValues with BeforeAndAfterEach with BeforeAndAfterAll with EspItTest {

  import argonaut.ArgonautShapeless._
  import argonaut.Argonaut._

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = scaled(Span(2, Seconds)), interval = scaled(Span(100, Millis)))

  it("should return base intervalSettings") {
    getSettings ~> check {
      status shouldBe StatusCodes.OK
      val baseIntervalSettings = IntervalSettings.baseIntervalSettings
      val responseSettings = responseAs[String].decodeOption[UISettings].get
      val data = responseSettings.features

      data.intervalSettings.base shouldBe baseIntervalSettings.base
      data.intervalSettings.processes shouldBe baseIntervalSettings.processes
      data.intervalSettings.healthCheck shouldBe baseIntervalSettings.healthCheck
    }
  }
}
