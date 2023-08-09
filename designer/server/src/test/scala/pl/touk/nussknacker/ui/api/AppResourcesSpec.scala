package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.typesafe.config.{Config, ConfigFactory}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Json
import io.circe.syntax.EncoderOps
import org.scalatest.exceptions.TestFailedException
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, OptionValues}
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.ProblemStateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessStateDefinitionManager, SimpleStateStatus}
import pl.touk.nussknacker.engine.api.deployment.{ProcessState, StateStatus, StatusDetails}
import pl.touk.nussknacker.engine.api.process.{EmptyProcessConfigCreator, ProcessName, VersionId}
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.version.BuildInfo
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.helpers.TestFactory.{emptyProcessingTypeDataProvider, mapProcessingTypeDataProvider, withAdminPermissions, withPermissions, withoutPermissions}
import pl.touk.nussknacker.ui.api.helpers.{NuItTest, StubDeploymentService, TestFactory}
import pl.touk.nussknacker.ui.process.processingtypedata.{ProcessingTypeDataProvider, ProcessingTypeDataReload}

import java.util.Collections
import scala.jdk.CollectionConverters._

class AppResourcesSpec
  extends AnyFunSuite
    with ScalatestRouteTest
    with Matchers
    with PatientScalaFutures
    with FailFastCirceSupport
    with OptionValues
    with BeforeAndAfterEach
    with BeforeAndAfterAll
    with NuItTest {

  test("it should return healthcheck also if cannot retrieve statuses") {
    createDeployedProcess(ProcessName("id1"))
    createDeployedProcess(ProcessName("id2"))
    createDeployedProcess(ProcessName("id3"))
    val resources = prepareBasicAppResources(
      statuses = Map(
        ProcessName("id1") -> ProblemStateStatus.FailedToGet,
        ProcessName("id2") -> SimpleStateStatus.Running,
        ProcessName("id3") -> ProblemStateStatus.shouldBeRunning(VersionId(1L), "user"),
      )
    )

    val result = Get("/app/healthCheck/process/deployment") ~> withPermissions(resources, testPermissionRead)

    result ~> check {
      status shouldBe StatusCodes.InternalServerError
      val expectedResponse = HealthCheckProcessResponse(ERROR, Some("Scenarios with status PROBLEM"), Some(Set("id1", "id3")))
      entityAs[HealthCheckProcessResponse] shouldBe expectedResponse
    }
  }

  test("it shouldn't return healthcheck when scenario canceled") {
    createDeployedCanceledProcess(ProcessName("id1"))
    createDeployedProcess(ProcessName("id2"))
    val resources = prepareBasicAppResources(
      statuses = Map(
        ProcessName("id2") -> ProblemStateStatus.shouldBeRunning(VersionId(1L), "user"),
      )
    )

    val result = Get("/app/healthCheck/process/deployment") ~> withPermissions(resources, testPermissionRead)

    result ~> check {
      status shouldBe StatusCodes.InternalServerError
      val expectedResponse = HealthCheckProcessResponse(ERROR, Some("Scenarios with status PROBLEM"), Some(Set("id2")))
      entityAs[HealthCheckProcessResponse] shouldBe expectedResponse
    }
  }

  test("it should return healthcheck ok if statuses are ok") {
    createDeployedProcess(ProcessName("id1"))
    createDeployedProcess(ProcessName("id2"))
    val resources = prepareBasicAppResources(
      statuses = Map(
        ProcessName("id1") -> SimpleStateStatus.Running,
        ProcessName("id2") -> SimpleStateStatus.Running,
      )
    )

    val result = Get("/app/healthCheck/process/deployment") ~> withPermissions(resources, testPermissionRead)

    result ~> check {
      status shouldBe StatusCodes.OK
    }
  }

  test("it should not report deployment in progress as fail") {
    createDeployedProcess(ProcessName("id1"))
    val resources = prepareBasicAppResources(
      statuses = Map(
        ProcessName("id1") -> SimpleStateStatus.Running,
      )
    )

    val result = Get("/app/healthCheck/process/deployment") ~> withPermissions(resources, testPermissionRead)

    result ~> check {
      status shouldBe StatusCodes.OK
    }
  }

  test("it should return config") {
    val resources = prepareBasicAppResources(statuses = Map.empty)

    val result = Get("/app/config") ~> withAdminPermissions(resources)

    result ~> check {
      status shouldBe StatusCodes.OK
    }
  }

  test("it shouldn't return config when disabled") {
    val resources = prepareBasicAppResources(
      statuses = Map.empty,
      withConfigExposed = false
    )

    val result = Get("/app/config") ~> withAdminPermissions(resources)

    result ~> check {
      rejections
    }
  }

  test("it should return build info without authentication") {
    val modelData = LocalModelData(
      inputConfig = ConfigFactory.empty(),
      configCreator = new EmptyProcessConfigCreator {
        override def buildInfo(): Map[String, String] = Map("fromModel" -> "value1")
      }
    )
    val globalConfig = Map("testConfig" -> "testValue", "otherConfig" -> "otherValue")
    val resources = prepareBasicAppResources(
      config = ConfigFactory.parseMap(Collections.singletonMap("globalBuildInfo", globalConfig.asJava)),
      statuses = Map.empty,
      modelData = mapProcessingTypeDataProvider("test1" -> modelData),
      withConfigExposed = false
    )

    val result = Get("/app/buildInfo") ~> withoutPermissions(resources)

    result ~> check {
      status shouldBe StatusCodes.OK
      entityAs[Map[String, Json]] shouldBe (BuildInfo.toMap.mapValuesNow(_.toString) ++ globalConfig).mapValuesNow(_.asJson) + ("processingType" -> Map("test1" -> creatorWithBuildInfo.buildInfo()).asJson)
    }
  }

  test("it should should return simple designer health check (not checking scenario statuses) without authentication") {
    val resources = prepareBasicAppResources(statuses = Map.empty)

    val result = Get("/app/healthCheck") ~> withoutPermissions(resources)

    result ~> check {
      status shouldBe StatusCodes.OK
    }
  }

  private val emptyReload = new ProcessingTypeDataReload {
    override def reloadAll(): Unit = throw new TestFailedException(
      message = Some("Should not be called in this test case"),
      cause = None,
      failedCodeStackDepth = 0
    )
  }

  private def processStatus(status: StateStatus): ProcessState =
    SimpleProcessStateDefinitionManager.processState(StatusDetails(status, None))

  private def prepareBasicAppResources(config: Config = ConfigFactory.empty(),
                                       statuses: Map[ProcessName, StateStatus],
                                       modelData: ProcessingTypeDataProvider[ModelData, _] = emptyProcessingTypeDataProvider,
                                       withConfigExposed: Boolean = true) = {
    val deploymentService = new StubDeploymentService(statuses.mapValuesNow(processStatus))
    new AppResources(
      config = config,
      processingTypeDataReload = emptyReload,
      modelData = modelData,
      processRepository = futureFetchingProcessRepository,
      processValidation = TestFactory.processValidation,
      deploymentService = deploymentService,
      exposeConfig = withConfigExposed,
      processCategoryService = processCategoryService
    )
  }

}
