package pl.touk.esp.ui.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import argonaut.Argonaut._
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import pl.touk.esp.ui.api.helpers.EspItTest
import pl.touk.esp.ui.api.helpers.TestFactory._
import pl.touk.esp.ui.process.repository.ProcessActivityRepository.ProcessActivity

import scala.concurrent.duration._
import scala.language.higherKinds

class ProcessActivityResourceSpec extends FlatSpec with ScalatestRouteTest with Matchers with ScalaFutures with BeforeAndAfterEach with EspItTest  {

  import pl.touk.esp.ui.codec.UiCodecs._
  implicit override val patienceConfig = PatienceConfig(timeout = scaled(Span(1, Seconds)), interval = scaled(Span(100, Millis)))
  implicit val testtimeout = RouteTestTimeout(2.seconds)

  val processActivityRouteWithAllPermission = withAllPermissions(processActivityRoute)

  it should "add comment to process activity" in {
    val processToSave = ProcessTestData.sampleDisplayableProcess
    val commentContent = "test message"
    saveProcess(processToSave) { status shouldEqual StatusCodes.OK}
    Post(s"/processes/${processToSave.id}/1/activity/comments", commentContent) ~> processActivityRouteWithAllPermission ~> check {
      status shouldEqual StatusCodes.OK
      Get(s"/processes/${processToSave.id}/activity") ~> processActivityRouteWithAllPermission ~> check {
        val processActivity = responseAs[String].decodeOption[ProcessActivity].get
        processActivity.comments.head.content shouldBe commentContent
      }
    }
  }

}
