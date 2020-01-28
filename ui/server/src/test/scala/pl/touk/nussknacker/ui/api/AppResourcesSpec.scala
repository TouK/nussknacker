package pl.touk.nussknacker.ui.api

import java.util.Collections

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.testkit.TestProbe
import com.typesafe.config.ConfigFactory
import io.circe.Json
import io.circe.syntax._
import org.scalatest._
import pl.touk.nussknacker.engine.api.deployment.StateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessStateDefinitionManager, SimpleStateStatus}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.testing.{EmptyProcessConfigCreator, LocalModelData}
import pl.touk.nussknacker.restmodel.displayedgraph.ProcessStatus
import pl.touk.nussknacker.restmodel.process
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.helpers.TestFactory.withPermissions
import pl.touk.nussknacker.ui.api.helpers.{EspItTest, TestFactory}
import pl.touk.nussknacker.ui.process.JobStatusService
import pl.touk.nussknacker.ui.process.deployment.CheckStatus

import scala.collection.JavaConverters._

class AppResourcesSpec extends FunSuite with ScalatestRouteTest with Matchers with PatientScalaFutures
  with OptionValues with BeforeAndAfterEach with BeforeAndAfterAll with EspItTest {

  def processStatus(deploymentId: Option[String], status: StateStatus): ProcessStatus =
    ProcessStatus.simple(status, deploymentId, List.empty)

  test("it should return healthcheck also if cannot retrieve statuses") {
    val statusCheck = TestProbe()

    val resources = new AppResources(ConfigFactory.empty(), typeToConfig, Map(), processRepository, TestFactory.processValidation,
      new JobStatusService(statusCheck.ref))

    createDeployedProcess("id1")
    createDeployedProcess("id2")
    createDeployedProcess("id3")

    val result = Get("/app/healthCheck") ~> withPermissions(resources, testPermissionRead)

    val first = statusCheck.expectMsgClass(classOf[CheckStatus])
    statusCheck.reply(akka.actor.Status.Failure(new Exception("Failed to check status")))

    val second = statusCheck.expectMsgClass(classOf[CheckStatus])
    statusCheck.reply(Some(processStatus(None, SimpleStateStatus.Running)))

    val third = statusCheck.expectMsgClass(classOf[CheckStatus])
    statusCheck.reply(None)

    result ~> check {
      status shouldBe StatusCodes.InternalServerError
      entityAs[String] shouldBe s"Deployed processes not running (probably failed): \n${first.id.name.value}, ${third.id.name.value}"
    }
  }

  test("it shouldn't return healthcheck when process canceled") {
    val statusCheck = TestProbe()
    val resources = new AppResources(ConfigFactory.empty(), typeToConfig, Map(), processRepository, TestFactory.processValidation,
      new JobStatusService(statusCheck.ref))

    createDeployedCanceledProcess(ProcessName("id1"),  false)
    createDeployedProcess(ProcessName("id2"),  false)

    val result = Get("/app/healthCheck") ~> withPermissions(resources, testPermissionRead)

    val second = statusCheck.expectMsgClass(classOf[CheckStatus])
    statusCheck.reply(None)

    result ~> check {
      status shouldBe StatusCodes.InternalServerError
      entityAs[String] shouldBe s"Deployed processes not running (probably failed): \n${second.id.name.value}"
    }
  }

  test("it should return healthcheck ok if statuses are ok") {
    val statusCheck = TestProbe()

    val resources = new AppResources(ConfigFactory.empty(), typeToConfig, Map(), processRepository, TestFactory.processValidation,
      new JobStatusService(statusCheck.ref))

    createDeployedProcess("id1")
    createDeployedProcess("id2")

    val result = Get("/app/healthCheck") ~> withPermissions(resources, testPermissionRead)

    statusCheck.expectMsgClass(classOf[CheckStatus])
    statusCheck.reply(Some(processStatus(None, SimpleStateStatus.Running)))
    statusCheck.expectMsgClass(classOf[CheckStatus])
    statusCheck.reply(Some(processStatus(None, SimpleStateStatus.Running)))

    result ~> check {
      status shouldBe StatusCodes.OK
    }
  }

  test("it should not report deployment in progress as fail") {
    val statusCheck = TestProbe()

    val resources = new AppResources(ConfigFactory.empty(), typeToConfig, Map(), processRepository, TestFactory.processValidation,
      new JobStatusService(statusCheck.ref))

    createDeployedProcess("id1")

    val result = Get("/app/healthCheck") ~> withPermissions(resources, testPermissionRead)

    statusCheck.expectMsgClass(classOf[CheckStatus])
    statusCheck.reply(Some(processStatus(None, SimpleStateStatus.Running)))

    result ~> check {
      status shouldBe StatusCodes.OK
    }
  }

  test("it should return build info without authentication") {
    import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._

    val creatorWithBuildInfo = new EmptyProcessConfigCreator {
      override def buildInfo(): Map[String, String] = Map("fromModel" -> "value1")
    }
    val modelData = LocalModelData(ConfigFactory.empty(), creatorWithBuildInfo)

    val globalConfig = Map("testConfig" -> "testValue", "otherConfig" -> "otherValue")
    val resources = new AppResources(ConfigFactory.parseMap(Collections.singletonMap("globalBuildInfo", globalConfig.asJava)),
      typeToConfig, Map("test1" -> modelData), processRepository, TestFactory.processValidation, new JobStatusService(TestProbe().ref))

    val result = Get("/app/buildInfo") ~> TestFactory.withoutPermissions(resources)
    result ~> check {
      status shouldBe StatusCodes.OK
      entityAs[Map[String, Json]] shouldBe globalConfig.mapValues(_.asJson) + ("processingType" -> Map("test1" -> creatorWithBuildInfo.buildInfo()).asJson)
    }
  }
}
