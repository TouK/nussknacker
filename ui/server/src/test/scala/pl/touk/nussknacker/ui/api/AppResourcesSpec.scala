package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.testkit.TestProbe
import com.typesafe.config.ConfigFactory
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Json
import io.circe.syntax.EncoderOps
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, FunSuite, Matchers, OptionValues}
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessStateDefinitionManager, SimpleStateStatus}
import pl.touk.nussknacker.engine.api.deployment.{ProcessState, StateStatus}
import pl.touk.nussknacker.engine.api.process.{EmptyProcessConfigCreator, ProcessName}
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.version.BuildInfo
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.helpers.TestFactory.{emptyProcessingTypeDataProvider, mapProcessingTypeDataProvider, withPermissions}
import pl.touk.nussknacker.ui.api.helpers.{EspItTest, TestFactory}
import pl.touk.nussknacker.ui.process.deployment.CheckStatus
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataReload

import java.util.Collections

class AppResourcesSpec extends FunSuite with ScalatestRouteTest with Matchers with PatientScalaFutures with FailFastCirceSupport
  with OptionValues with BeforeAndAfterEach with BeforeAndAfterAll with EspItTest {

  private val emptyReload = new ProcessingTypeDataReload {
    override def reloadAll(): Unit = ???
  }

  private def processStatus(status: StateStatus): ProcessState =
    SimpleProcessStateDefinitionManager.processState(status)

  private def prepareBasicAppResources(statusCheck: TestProbe) = {
    val processService = createDBProcessService(statusCheck.ref)
    new AppResources(ConfigFactory.empty(), emptyReload, emptyProcessingTypeDataProvider, fetchingProcessRepository, TestFactory.processValidation, processService)
  }

  test("it should return healthcheck also if cannot retrieve statuses") {
    val statusCheck = TestProbe()
    val resources = prepareBasicAppResources(statusCheck)

    createDeployedProcess(ProcessName("id1"))
    createDeployedProcess(ProcessName("id2"))
    createDeployedProcess(ProcessName("id3"))

    val result = Get("/app/healthCheck/process/deployment") ~> withPermissions(resources, testPermissionRead)

    val first = statusCheck.expectMsgClass(classOf[CheckStatus])
    statusCheck.reply(processStatus(SimpleStateStatus.FailedToGet))

    statusCheck.expectMsgClass(classOf[CheckStatus])
    statusCheck.reply(processStatus(SimpleStateStatus.Running))

    val third = statusCheck.expectMsgClass(classOf[CheckStatus])
    statusCheck.reply(processStatus(SimpleStateStatus.NotDeployed))

    result ~> check {
      status shouldBe StatusCodes.InternalServerError
      val expectedResponse = HealthCheckProcessResponse(ERROR, Some("Deployed scenarios not running (probably failed)"), Some(Set(first.id.name.value, third.id.name.value)))
      entityAs[HealthCheckProcessResponse] shouldBe expectedResponse
    }
  }

  test("it shouldn't return healthcheck when scenario canceled") {
    val statusCheck = TestProbe()
    val resources = prepareBasicAppResources(statusCheck)

    createDeployedCanceledProcess(ProcessName("id1"),  isSubprocess = false)
    createDeployedProcess(ProcessName("id2"),  isSubprocess = false)

    val result = Get("/app/healthCheck/process/deployment") ~> withPermissions(resources, testPermissionRead)

    val second = statusCheck.expectMsgClass(classOf[CheckStatus])
    statusCheck.reply(processStatus(SimpleStateStatus.NotDeployed))

    result ~> check {
      status shouldBe StatusCodes.InternalServerError
      val expectedResponse = HealthCheckProcessResponse(ERROR, Some("Deployed scenarios not running (probably failed)"), Some(Set(second.id.name.value)))
      entityAs[HealthCheckProcessResponse] shouldBe expectedResponse
    }
  }

  test("it should return healthcheck ok if statuses are ok") {
    val statusCheck = TestProbe()
    val resources = prepareBasicAppResources(statusCheck)

    createDeployedProcess(ProcessName("id1"))
    createDeployedProcess(ProcessName("id2"))

    val result = Get("/app/healthCheck/process/deployment") ~> withPermissions(resources, testPermissionRead)

    statusCheck.expectMsgClass(classOf[CheckStatus])
    statusCheck.reply(processStatus(SimpleStateStatus.Running))
    statusCheck.expectMsgClass(classOf[CheckStatus])
    statusCheck.reply(processStatus(SimpleStateStatus.Running))

    result ~> check {
      status shouldBe StatusCodes.OK
    }
  }

  test("it should not report deployment in progress as fail") {
    val statusCheck = TestProbe()
    val resources = prepareBasicAppResources(statusCheck)

    createDeployedProcess(ProcessName("id1"))

    val result = Get("/app/healthCheck/process/deployment") ~> withPermissions(resources, testPermissionRead)

    statusCheck.expectMsgClass(classOf[CheckStatus])
    statusCheck.reply(processStatus(SimpleStateStatus.Running))

    result ~> check {
      status shouldBe StatusCodes.OK
    }
  }

  test("it should return build info without authentication") {
    val creatorWithBuildInfo = new EmptyProcessConfigCreator {
      override def buildInfo(): Map[String, String] = Map("fromModel" -> "value1")
    }
    val modelData = LocalModelData(ConfigFactory.empty(), creatorWithBuildInfo)

    val globalConfig = Map("testConfig" -> "testValue", "otherConfig" -> "otherValue")

    val processService = createDBProcessService(TestProbe().ref)
    val resources = new AppResources(ConfigFactory.parseMap(Collections.singletonMap("globalBuildInfo", globalConfig.asJava)), emptyReload,
       mapProcessingTypeDataProvider("test1" -> modelData), fetchingProcessRepository, TestFactory.processValidation, processService)

    val result = Get("/app/buildInfo") ~> TestFactory.withoutPermissions(resources)
    result ~> check {
      status shouldBe StatusCodes.OK
      entityAs[Map[String, Json]] shouldBe (BuildInfo.toMap.mapValues(_.toString) ++ globalConfig).mapValues(_.asJson) + ("processingType" -> Map("test1" -> creatorWithBuildInfo.buildInfo()).asJson)
    }
  }
}
