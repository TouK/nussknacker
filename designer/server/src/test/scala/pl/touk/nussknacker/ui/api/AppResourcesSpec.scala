package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.typesafe.config.ConfigFactory
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Json
import io.circe.syntax.EncoderOps
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, OptionValues}
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessStateDefinitionManager, SimpleStateStatus}
import pl.touk.nussknacker.engine.api.deployment.{ProcessState, StateStatus}
import pl.touk.nussknacker.engine.api.process.{EmptyProcessConfigCreator, ProcessName}
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.version.BuildInfo
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.helpers.TestFactory.{emptyProcessingTypeDataProvider, mapProcessingTypeDataProvider, withAdminPermissions, withPermissions, withoutPermissions}
import pl.touk.nussknacker.ui.api.helpers.{EspItTest, StubManagementService, TestFactory}
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataReload

import scala.jdk.CollectionConverters._
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

import java.util.Collections

class AppResourcesSpec extends AnyFunSuite with ScalatestRouteTest with Matchers with PatientScalaFutures with FailFastCirceSupport
  with OptionValues with BeforeAndAfterEach with BeforeAndAfterAll with EspItTest {

  private val emptyReload = new ProcessingTypeDataReload {
    override def reloadAll(): Unit = ???
  }

  private def processStatus(status: StateStatus): ProcessState =
    SimpleProcessStateDefinitionManager.processState(status)

  private def prepareBasicAppResources(statuses: Map[ProcessName, StateStatus], withConfigExposed: Boolean = true) = {
    val processService = createDBProcessService(new StubManagementService(statuses.mapValuesNow(processStatus)))
    new AppResources(ConfigFactory.empty(), emptyReload, emptyProcessingTypeDataProvider, fetchingProcessRepository,
      TestFactory.processValidation, processService, exposeConfig = withConfigExposed, processCategoryService
    )
  }

  test("it should return healthcheck also if cannot retrieve statuses") {
    createDeployedProcess(ProcessName("id1"))
    createDeployedProcess(ProcessName("id2"))
    createDeployedProcess(ProcessName("id3"))
    val statuses = Map(
      ProcessName("id1") -> SimpleStateStatus.FailedToGet,
      ProcessName("id2") -> SimpleStateStatus.Running,
      ProcessName("id3") -> SimpleStateStatus.NotDeployed,
    )

    val resources = prepareBasicAppResources(statuses)
    val result = Get("/app/healthCheck/process/deployment") ~> withPermissions(resources, testPermissionRead)

    result ~> check {
      status shouldBe StatusCodes.InternalServerError
      val expectedResponse = HealthCheckProcessResponse(ERROR, Some("Deployed scenarios not running (probably failed)"), Some(Set("id1", "id3")))
      entityAs[HealthCheckProcessResponse] shouldBe expectedResponse
    }
  }

  test("it shouldn't return healthcheck when scenario canceled") {
    createDeployedCanceledProcess(ProcessName("id1"))
    createDeployedProcess(ProcessName("id2"))
    val statuses = Map(
      ProcessName("id2") -> SimpleStateStatus.NotDeployed,
    )

    val resources = prepareBasicAppResources(statuses)
    val result = Get("/app/healthCheck/process/deployment") ~> withPermissions(resources, testPermissionRead)

    result ~> check {
      status shouldBe StatusCodes.InternalServerError
      val expectedResponse = HealthCheckProcessResponse(ERROR, Some("Deployed scenarios not running (probably failed)"), Some(Set("id2")))
      entityAs[HealthCheckProcessResponse] shouldBe expectedResponse
    }
  }

  test("it should return healthcheck ok if statuses are ok") {
    createDeployedProcess(ProcessName("id1"))
    createDeployedProcess(ProcessName("id2"))
    val statuses = Map(
      ProcessName("id1") -> SimpleStateStatus.Running,
      ProcessName("id2") -> SimpleStateStatus.Running,
    )

    val resources = prepareBasicAppResources(statuses)
    val result = Get("/app/healthCheck/process/deployment") ~> withPermissions(resources, testPermissionRead)

    result ~> check {
      status shouldBe StatusCodes.OK
    }
  }

  test("it should not report deployment in progress as fail") {
    createDeployedProcess(ProcessName("id1"))
    val statuses = Map(
      ProcessName("id1") -> SimpleStateStatus.Running,
    )

    val resources = prepareBasicAppResources(statuses)
    val result = Get("/app/healthCheck/process/deployment") ~> withPermissions(resources, testPermissionRead)

    result ~> check {
      status shouldBe StatusCodes.OK
    }
  }

  test("it should return config") {
    val resources = prepareBasicAppResources(Map.empty)

    val result = Get("/app/config") ~> withAdminPermissions(resources)

    result ~> check {
      status shouldBe StatusCodes.OK
    }
  }

  test("it shouldn't return config when disabled") {
    val resources = prepareBasicAppResources(Map.empty, withConfigExposed = false)

    val result = Get("/app/config") ~> withAdminPermissions(resources)

    result ~> check {
      rejections
    }
  }

  test("it should return build info without authentication") {
    val creatorWithBuildInfo = new EmptyProcessConfigCreator {
      override def buildInfo(): Map[String, String] = Map("fromModel" -> "value1")
    }
    val modelData = LocalModelData(ConfigFactory.empty(), creatorWithBuildInfo)

    val globalConfig = Map("testConfig" -> "testValue", "otherConfig" -> "otherValue")

    val resources = new AppResources(ConfigFactory.parseMap(Collections.singletonMap("globalBuildInfo", globalConfig.asJava)), emptyReload,
       mapProcessingTypeDataProvider("test1" -> modelData), fetchingProcessRepository, TestFactory.processValidation,
      processService, exposeConfig = false, processCategoryService)

    val result = Get("/app/buildInfo") ~> withoutPermissions(resources)
    result ~> check {
      status shouldBe StatusCodes.OK
      entityAs[Map[String, Json]] shouldBe (BuildInfo.toMap.mapValuesNow(_.toString) ++ globalConfig).mapValuesNow(_.asJson) + ("processingType" -> Map("test1" -> creatorWithBuildInfo.buildInfo()).asJson)
    }
  }

  test("it should should return simple designer health check (not checking scenario statuses) without authentication") {
    val resources = prepareBasicAppResources(Map.empty)

    val result = Get("/app/healthCheck") ~> withoutPermissions(resources)
    result ~> check {
      status shouldBe StatusCodes.OK
    }
  }

}
