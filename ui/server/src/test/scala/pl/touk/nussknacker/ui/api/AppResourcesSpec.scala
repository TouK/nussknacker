package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.testkit.TestProbe
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import pl.touk.nussknacker.engine.api.deployment.CustomProcess
import pl.touk.nussknacker.ui.api.helpers.TestFactory.withPermissions
import pl.touk.nussknacker.ui.api.helpers.{EspItTest, TestFactory}
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessingType
import pl.touk.nussknacker.ui.process.JobStatusService
import pl.touk.nussknacker.ui.process.deployment.CheckStatus
import pl.touk.nussknacker.ui.process.displayedgraph.ProcessStatus
import pl.touk.nussknacker.ui.security.api.Permission

class AppResourcesSpec extends FunSuite with ScalatestRouteTest
  with Matchers with ScalaFutures with OptionValues with BeforeAndAfterEach with BeforeAndAfterAll with EspItTest {

  implicit override val patienceConfig = PatienceConfig(timeout = scaled(Span(2, Seconds)), interval = scaled(Span(20, Millis)))

  test("it should return healthcheck also if cannot retrieve statuses") {

    val statusCheck = TestProbe()

    val resources = new AppResources(Map(), processRepository, TestFactory.processValidation,
      new JobStatusService(statusCheck.ref))

    saveProcessWithDeployInfo("id1")
    saveProcessWithDeployInfo("id2")
    saveProcessWithDeployInfo("id3")

    val result = Get("/app/healthCheck") ~> withPermissions(resources, Permission.Read)

    val first = statusCheck.expectMsgClass(classOf[CheckStatus])
    statusCheck.reply(new Exception("Failed to check status"))

    val second = statusCheck.expectMsgClass(classOf[CheckStatus])
    statusCheck.reply(Some(ProcessStatus(None, "RUNNING", 0l, true, true)))

    val third = statusCheck.expectMsgClass(classOf[CheckStatus])
    statusCheck.reply(None)


    result ~> check {
      status shouldBe StatusCodes.InternalServerError
      entityAs[String] shouldBe s"Deployed processes not running (probably failed): \n${first.id}, ${third.id}"
    }
  }

  test("it should return healthcheck ok if statuses are ok") {

    val statusCheck = TestProbe()

    val resources = new AppResources(Map(), processRepository, TestFactory.processValidation,
      new JobStatusService(statusCheck.ref))

    saveProcessWithDeployInfo("id1")
    saveProcessWithDeployInfo("id2")

    val result = Get("/app/healthCheck") ~> withPermissions(resources, Permission.Read)

    statusCheck.expectMsgClass(classOf[CheckStatus])
    statusCheck.reply(Some(ProcessStatus(None, "RUNNING", 0l, true, true)))
    statusCheck.expectMsgClass(classOf[CheckStatus])
    statusCheck.reply(Some(ProcessStatus(None, "RUNNING", 0l, true, true)))

    result ~> check {
      status shouldBe StatusCodes.OK
    }
  }

  private def saveProcessWithDeployInfo(id: String) = {
    implicit val logged = TestFactory.user(Permission.Admin)
    writeProcessRepository.saveNewProcess(id, TestFactory.testCategory, CustomProcess(""), ProcessingType.Streaming, false)
      .futureValue shouldBe Right(())
    deploymentProcessRepository.markProcessAsDeployed(id, 1, ProcessingType.Streaming, "", "").futureValue shouldBe (())
  }


}
