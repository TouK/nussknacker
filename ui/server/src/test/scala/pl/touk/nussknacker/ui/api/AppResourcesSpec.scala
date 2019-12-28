package pl.touk.nussknacker.ui.api

import java.util.Collections

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.testkit.TestProbe
import com.typesafe.config.ConfigFactory
import io.circe.Json
import io.circe.syntax._
import org.scalatest._
import pl.touk.nussknacker.engine.api.deployment.StatusState.StateStatus
import pl.touk.nussknacker.engine.api.deployment.{CustomProcess}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.customs.deployment.{CustomStateStatus, ProcessStateCustomConfigurator}
import pl.touk.nussknacker.engine.testing.{EmptyProcessConfigCreator, LocalModelData}
import pl.touk.nussknacker.restmodel.displayedgraph.ProcessStatus
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.helpers.TestFactory.withPermissions
import pl.touk.nussknacker.ui.api.helpers.{EspItTest, TestFactory, TestProcessingTypes}
import pl.touk.nussknacker.ui.process.JobStatusService
import pl.touk.nussknacker.ui.process.deployment.CheckStatus
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.collection.JavaConverters._

class AppResourcesSpec extends FunSuite with ScalatestRouteTest
  with Matchers with PatientScalaFutures with OptionValues with BeforeAndAfterEach with BeforeAndAfterAll with EspItTest {

  def processStatus(deploymentId: Option[String], status: StateStatus): ProcessStatus =
    ProcessStatus(deploymentId, status, allowedActions = ProcessStateCustomConfigurator.getStatusActions(status))

  test("it should return healthcheck also if cannot retrieve statuses") {

    val statusCheck = TestProbe()

    val resources = new AppResources(ConfigFactory.empty(), Map(), processRepository, TestFactory.processValidation,
      new JobStatusService(statusCheck.ref))

    saveProcessWithDeployInfo("id1")
    saveProcessWithDeployInfo("id2")
    saveProcessWithDeployInfo("id3")

    val result = Get("/app/healthCheck") ~> withPermissions(resources, testPermissionRead)

    val first = statusCheck.expectMsgClass(classOf[CheckStatus])
    statusCheck.reply(akka.actor.Status.Failure(new Exception("Failed to check status")))

    val second = statusCheck.expectMsgClass(classOf[CheckStatus])
    statusCheck.reply(Some(processStatus(None, CustomStateStatus.Running)))

    val third = statusCheck.expectMsgClass(classOf[CheckStatus])
    statusCheck.reply(None)

    result ~> check {
      status shouldBe StatusCodes.InternalServerError
      entityAs[String] shouldBe s"Deployed processes not running (probably failed): \n${first.id.name.value}, ${third.id.name.value}"
    }
  }

  test("it should return healthcheck ok if statuses are ok") {

    val statusCheck = TestProbe()

    val resources = new AppResources(ConfigFactory.empty(), Map(), processRepository, TestFactory.processValidation,
      new JobStatusService(statusCheck.ref))

    saveProcessWithDeployInfo("id1")
    saveProcessWithDeployInfo("id2")

    val result = Get("/app/healthCheck") ~> withPermissions(resources, testPermissionRead)

    statusCheck.expectMsgClass(classOf[CheckStatus])
    statusCheck.reply(Some(processStatus(None, CustomStateStatus.Running)))
    statusCheck.expectMsgClass(classOf[CheckStatus])
    statusCheck.reply(Some(processStatus(None, CustomStateStatus.Running)))

    result ~> check {
      status shouldBe StatusCodes.OK
    }
  }

  test("it should not report deployment in progress as fail") {
    val statusCheck = TestProbe()

    val resources = new AppResources(ConfigFactory.empty(),
      Map(), processRepository,
      TestFactory.processValidation,
      new JobStatusService(statusCheck.ref)
    )

    saveProcessWithDeployInfo("id1")

    val result = Get("/app/healthCheck") ~> withPermissions(resources, testPermissionRead)

    statusCheck.expectMsgClass(classOf[CheckStatus])
    statusCheck.reply(Some(processStatus(None, CustomStateStatus.Running)))

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
      Map("test1" -> modelData), processRepository, TestFactory.processValidation, new JobStatusService(TestProbe().ref))

    val result = Get("/app/buildInfo") ~> TestFactory.withoutPermissions(resources)
    result ~> check {
      status shouldBe StatusCodes.OK
      entityAs[Map[String, Json]] shouldBe globalConfig.mapValues(_.asJson) + ("processingType" -> Map("test1" -> creatorWithBuildInfo.buildInfo()).asJson)
    }
  }

  private def saveProcessWithDeployInfo(id: String) = {
    implicit val logged: LoggedUser = TestFactory.adminUser("userId")
    writeProcessRepository.saveNewProcess(ProcessName(id), TestFactory.testCategoryName, CustomProcess(""), TestProcessingTypes.Streaming, false)
      .futureValue shouldBe ('right)
    val processId = processRepository.fetchProcessId(ProcessName(id)).futureValue.get
    deploymentProcessRepository.markProcessAsDeployed(processId, 1, TestProcessingTypes.Streaming,
      "", Some("")).map(_ => ()).futureValue shouldBe (())
  }
}
