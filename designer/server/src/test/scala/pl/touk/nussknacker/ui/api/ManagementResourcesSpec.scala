package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.{ContentTypeRange, StatusCodes}
import akka.http.scaladsl.server
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import cats.instances.all._
import cats.syntax.semigroup._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Json
import io.circe.syntax._
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, OptionValues}
import org.scalatest.matchers.BeMatcher
import pl.touk.nussknacker.engine.api.{MetaData, StreamMetaData}
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.{ProcessName, VersionId}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.restmodel.process.ProcessIdWithName
import pl.touk.nussknacker.restmodel.processdetails._
import pl.touk.nussknacker.restmodel.{CustomActionRequest, CustomActionResponse}
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.helpers.TestFactory._
import pl.touk.nussknacker.ui.api.helpers._
import pl.touk.nussknacker.ui.process.exception.ProcessIllegalAction
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.repository.DbProcessActivityRepository.ProcessActivity
import pl.touk.nussknacker.ui.util.MultipartUtils
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.kafka.KafkaFactory
import pl.touk.nussknacker.ui.api.ProcessesResources.ProcessesQuery

import java.time.Instant

class ManagementResourcesSpec extends AnyFunSuite with ScalatestRouteTest with FailFastCirceSupport
  with Matchers with PatientScalaFutures with OptionValues with BeforeAndAfterEach with BeforeAndAfterAll with EspItTest {

  import TestCategories._
  import KafkaFactory._

  private implicit final val string: FromEntityUnmarshaller[String] = Unmarshaller.stringUnmarshaller.forContentTypes(ContentTypeRange.*)

  private val processName: ProcessName = ProcessName(SampleProcess.process.id)
  private val fixedTime = Instant.now()

  private def deployedWithVersions(versionId: Long): BeMatcher[Option[ProcessAction]] =
    BeMatcher(equal(
        Option(ProcessAction(VersionId(versionId), fixedTime, user().username, ProcessActionType.Deploy, Option.empty, Option.empty, buildInfo))
      ).matcher[Option[ProcessAction]]
    ).compose[Option[ProcessAction]](_.map(_.copy(performedAt = fixedTime)))

   test("process deployment should be visible in process history") {
    saveProcessAndAssertSuccess(SampleProcess.process.id, SampleProcess.process)
    deployProcess(SampleProcess.process.id) ~> check {
      status shouldBe StatusCodes.OK
      getProcess(processName) ~> check {
        decodeDetails.lastAction shouldBe deployedWithVersions(2)
        updateProcessAndAssertSuccess(SampleProcess.process.id, SampleProcess.process)
        deployProcess(SampleProcess.process.id) ~> check {
          getProcess(processName) ~> check {
            decodeDetails.lastAction shouldBe deployedWithVersions(2)
          }
        }
      }
    }
  }

  test("process during deploy can be deployed again") {
    createDeployedProcess(processName, TestCat)

    deploymentManager.withProcessStateStatus(SimpleStateStatus.DuringDeploy) {
      deployProcess(processName.value) ~> check {
        status shouldBe StatusCodes.OK
      }
    }
  }

  test("canceled process can't be canceled again") {
    createDeployedCanceledProcess(processName, TestCat)

    deploymentManager.withProcessStateStatus(SimpleStateStatus.Canceled) {
      cancelProcess(processName.value) ~> check {
        status shouldBe StatusCodes.Conflict
      }
    }
  }

  test("can't deploy archived process") {
    val id = createArchivedProcess(processName)
    val processIdWithName = ProcessIdWithName(id, processName)

    deploymentManager.withProcessStateStatus(SimpleStateStatus.Canceled) {
      deployProcess(processName.value) ~> check {
        status shouldBe StatusCodes.Conflict
        responseAs[String] shouldBe ProcessIllegalAction.archived(ProcessActionType.Deploy, processIdWithName).message
      }
    }
  }

  test("can't deploy fragment") {
    val id = createValidProcess(processName, TestCat, isSubprocess = true)
    val processIdWithName = ProcessIdWithName(id, processName)

    deployProcess(processName.value) ~> check {
      status shouldBe StatusCodes.Conflict
      responseAs[String] shouldBe ProcessIllegalAction.subprocess(ProcessActionType.Deploy, processIdWithName).message
    }
  }

  test("can't cancel fragment") {
    val id = createValidProcess(processName, TestCat, isSubprocess = true)
    val processIdWithName = ProcessIdWithName(id, processName)

    deployProcess(processName.value) ~> check {
      status shouldBe StatusCodes.Conflict
      responseAs[String] shouldBe ProcessIllegalAction.subprocess(ProcessActionType.Deploy, processIdWithName).message
    }
  }

  test("deploys and cancels with comment") {
    saveProcessAndAssertSuccess(SampleProcess.process.id, SampleProcess.process)
    deployProcess(SampleProcess.process.id, Some(DeploymentCommentSettings.unsafe("deploy.*", Some("deployComment"))), comment = Some("deployComment")) ~> check {
      eventually {
        getProcess(processName) ~> check {
          val processDetails = responseAs[ProcessDetails]
          processDetails.isDeployed shouldBe true
        }
      }
      cancelProcess(SampleProcess.process.id, Some(DeploymentCommentSettings.unsafe("cancel.*", Some("cancelComment"))), comment = Some("cancelComment")) ~> check {
        status shouldBe StatusCodes.OK
        //TODO: remove Deployment:, Stop: after adding custom icons
        val expectedDeployComment = "Deployment: deployComment"
        val expectedStopComment = "Stop: cancelComment"
        getActivity(ProcessName(SampleProcess.process.id)) ~> check {
          val comments = responseAs[ProcessActivity].comments.sortBy(_.id)
          comments.map(_.content) shouldBe List(expectedDeployComment, expectedStopComment)

          val firstCommentId::secondCommentId::Nil = comments.map(_.id)

          Get(s"/processes/${SampleProcess.process.id}/deployments") ~> withAllPermissions(processesRoute) ~> check {
            val deploymentHistory = responseAs[List[ProcessAction]]
            val curTime = Instant.now()
            deploymentHistory.map(_.copy(performedAt = curTime)) shouldBe List(
              ProcessAction(VersionId(2), curTime, user().username, ProcessActionType.Cancel, Some(secondCommentId), Some(expectedStopComment), Map()),
              ProcessAction(VersionId(2), curTime, user().username, ProcessActionType.Deploy, Some(firstCommentId), Some(expectedDeployComment), TestFactory.buildInfo)
            )
          }
        }
      }
    }
  }

  test("rejects deploy without comment if comment required") {
    saveProcessAndAssertSuccess(SampleProcess.process.id, SampleProcess.process)
    deployProcess(SampleProcess.process.id, deploymentCommentSettings = Some(DeploymentCommentSettings.unsafe("requiredCommentPattern", Some("exampleRequiredComment")))) ~> check {
      status shouldBe StatusCodes.BadRequest
    }
  }

  test("deploy technical process and mark it as deployed") {
    createValidProcess(processName, TestCat, false)

    deployProcess(processName.value) ~> check { status shouldBe StatusCodes.OK }

    getProcess(processName) ~> check {
      val processDetails = responseAs[ProcessDetails]
      processDetails.lastAction shouldBe deployedWithVersions(1)
      processDetails.isDeployed shouldBe true
    }
  }

  test("recognize process cancel in deployment list") {
    saveProcessAndAssertSuccess(SampleProcess.process.id, SampleProcess.process)
    deployProcess(SampleProcess.process.id) ~> check {
      status shouldBe StatusCodes.OK
      getProcess(processName) ~> check {
        decodeDetails.lastAction shouldBe deployedWithVersions(2)
        cancelProcess(SampleProcess.process.id) ~> check {
          getProcess(processName) ~> check {
            decodeDetails.lastAction should not be None
            decodeDetails.isCanceled shouldBe  true
          }
        }
      }
    }
  }

  test("recognize process deploy and cancel in global process list") {
    saveProcessAndAssertSuccess(SampleProcess.process.id, SampleProcess.process)
    deployProcess(SampleProcess.process.id) ~> check {
      status shouldBe StatusCodes.OK

      forScenariosReturned(ProcessesQuery.empty) { processes =>
        val process = processes.find(_.name == SampleProcess.process.id).head
        process.lastActionVersionId shouldBe Some(2L)
        process.isDeployed shouldBe true

        cancelProcess(SampleProcess.process.id) ~> check {
          forScenariosReturned(ProcessesQuery.empty) { processes =>
            val process = processes.find(_.name == SampleProcess.process.id).head
            process.lastActionVersionId shouldBe Some(2L)
            process.isCanceled shouldBe true
          }
        }
      }
    }
  }

  test("not authorize user with write permission to deploy") {
    saveProcessAndAssertSuccess(SampleProcess.process.id, SampleProcess.process)
    Post(s"/processManagement/deploy/${SampleProcess.process.id}") ~> withPermissions(deployRoute(), testPermissionWrite) ~> check {
      rejection shouldBe server.AuthorizationFailedRejection
    }
  }

  test("should allow deployment of scenario with warning") {
    val processWithDisabledFilter = ScenarioBuilder
      .streaming("sampleProcess")
      .parallelism(1)
      .source("startProcess", "csv-source")
      .filter("input", "#input != null", Some(true))
      .emptySink("end", "kafka-string", "topic" -> "'end.topic'", "value" -> "#input")

    saveProcessAndAssertSuccess(SampleProcess.process.id, processWithDisabledFilter)
    deployProcess(processName.value) ~> check {
      status shouldBe StatusCodes.OK
    }
  }

  test("should return failure for not validating scenario") {
    val invalidScenario = ScenarioBuilder
      .streaming("sampleProcess")
      .parallelism(1)
      .source("start", "not existing")
      .emptySink("end", "kafka-string", TopicParamName -> "'end.topic'", SinkValueParamName -> "#output")
    saveProcessAndAssertSuccess(invalidScenario.id, invalidScenario)

    deploymentManager.withFailingDeployment(ProcessName(invalidScenario.id)) {
      deployProcess(invalidScenario.id) ~> check {
        responseAs[String] shouldBe "Cannot deploy invalid scenario"
        status shouldBe StatusCodes.Conflict
      }
    }
  }

  test("should return failure for not validating deployment") {
    val largeParallelismScenario = SampleProcess.process.copy(metaData = MetaData(SampleProcess.process.id, StreamMetaData(parallelism = Some(MockDeploymentManager.maxParallelism + 1))))
    saveProcessAndAssertSuccess(largeParallelismScenario.id, largeParallelismScenario)

    deploymentManager.withFailingDeployment(ProcessName(largeParallelismScenario.id)) {
      deployProcess(largeParallelismScenario.id) ~> check {
        status shouldBe StatusCodes.BadRequest
        responseAs[String] shouldBe "Parallelism too large"
      }
    }
  }

  test("return from deploy before deployment manager proceeds") {
    saveProcessAndAssertSuccess(SampleProcess.process.id, SampleProcess.process)

    deploymentManager.withWaitForDeployFinish(ProcessName(SampleProcess.process.id)) {
      deployProcess(SampleProcess.process.id) ~> check {
        status shouldBe StatusCodes.OK
      }
    }
  }

  test("snapshots process") {
    saveProcessAndAssertSuccess(SampleProcess.process.id, SampleProcess.process)
    snapshot(SampleProcess.process.id) ~> check {
      status shouldBe StatusCodes.OK
      responseAs[String] shouldBe MockDeploymentManager.savepointPath
    }
  }

  test("stops process") {
    saveProcessAndAssertSuccess(SampleProcess.process.id, SampleProcess.process)
    stop(SampleProcess.process.id) ~> check {
      status shouldBe StatusCodes.OK
      responseAs[String] shouldBe MockDeploymentManager.stopSavepointPath
    }
  }

  test("return test results") {
    saveProcessAndAssertSuccess(SampleProcess.process.id, SampleProcess.process)
    val displayableProcess = ProcessConverter.toDisplayable(SampleProcess.process, TestProcessingTypes.Streaming, Category1)
    val testDataContent =
      """{"sourceId":"startProcess","record":"ala"}
        |{"sourceId":"startProcess","record":"bela"}""".stripMargin
    val multiPart = MultipartUtils.prepareMultiParts("testData" -> testDataContent, "processJson" -> displayableProcess.asJson.noSpaces)()
    Post(s"/processManagement/test/${SampleProcess.process.id}", multiPart) ~> withPermissions(deployRoute(), testPermissionDeploy |+| testPermissionRead) ~> check {

      status shouldEqual StatusCodes.OK

      val ctx = responseAs[Json] .hcursor
              .downField("results")
              .downField("nodeResults")
              .downField("endsuffix")
              .downArray
              .downField("context")
              .downField("variables")

      ctx
        .downField("output")
        .downField("pretty")
        .downField("message")
        .focus shouldBe Some(Json.fromString("message"))

      ctx
        .downField("input")
        .downField("pretty")
        .downField("firstField")
        .focus shouldBe Some(Json.fromString("ala"))
    }
  }

  test("return test results of errors, including null") {

    import pl.touk.nussknacker.engine.spel.Implicits._

    val process = ScenarioBuilder
      .streaming("sampleProcess")
      .parallelism(1)
      .source("startProcess", "csv-source")
      .filter("input", "new java.math.BigDecimal(null) == 0")
      .emptySink("end", "kafka-string", TopicParamName -> "'end.topic'", "value" -> "''")
    val testDataContent =
      """{"sourceId":"startProcess","record":"ala"}
        |{"sourceId":"startProcess","record":"bela"}""".stripMargin

    saveProcessAndAssertSuccess(process.id, process)

    val displayableProcess = ProcessConverter.toDisplayable(process, TestProcessingTypes.Streaming, Category1)

    val multiPart = MultipartUtils.prepareMultiParts("testData" -> testDataContent, "processJson" -> displayableProcess.asJson.noSpaces)()
    Post(s"/processManagement/test/${process.id}", multiPart) ~> withPermissions(deployRoute(), testPermissionDeploy |+| testPermissionRead) ~> check {
      status shouldEqual StatusCodes.OK
    }
  }

  test("refuses to test if too much data") {

    import pl.touk.nussknacker.engine.spel.Implicits._

    val process = {
        ScenarioBuilder
          .streaming("sampleProcess")
          .parallelism(1)
          .source("startProcess", "csv-source")
          .emptySink("end", "kafka-string", KafkaFactory.TopicParamName -> "'end.topic'")
    }

    saveProcessAndAssertSuccess(process.id, process)

    val displayableProcess = ProcessConverter.toDisplayable(process, TestProcessingTypes.Streaming, Category1)

    List((1 to 50).mkString("\n"), (1 to 50000).mkString("-")).foreach { tooLargeData =>
      val multiPart = MultipartUtils.prepareMultiParts("testData" -> tooLargeData, "processJson" -> displayableProcess.asJson.noSpaces)()
      Post(s"/processManagement/test/${process.id}", multiPart) ~> withPermissions(deployRoute(), testPermissionDeploy |+| testPermissionRead) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }
  }

  test("execute valid custom action") {
    saveProcessAndAssertSuccess(SampleProcess.process.id, SampleProcess.process)
    customAction(SampleProcess.process.id, CustomActionRequest("hello")) ~> check {
      status shouldBe StatusCodes.OK
      responseAs[CustomActionResponse] shouldBe CustomActionResponse(isSuccess = true, msg = "Hi")
    }
  }

  test("execute non existing custom action") {
    saveProcessAndAssertSuccess(SampleProcess.process.id, SampleProcess.process)
    customAction(SampleProcess.process.id, CustomActionRequest("non-existing")) ~> check {
      status shouldBe StatusCodes.NotFound
      responseAs[CustomActionResponse] shouldBe CustomActionResponse(isSuccess = false, msg = "non-existing is not existing")
    }
  }

  test("execute not implemented custom action") {
    saveProcessAndAssertSuccess(SampleProcess.process.id, SampleProcess.process)
    customAction(SampleProcess.process.id, CustomActionRequest("not-implemented")) ~> check {
      status shouldBe StatusCodes.NotImplemented
      responseAs[CustomActionResponse] shouldBe CustomActionResponse(isSuccess = false, msg = "not-implemented is not implemented")
    }
  }

  test("execute custom action with not allowed process status") {
    saveProcessAndAssertSuccess(SampleProcess.process.id, SampleProcess.process)
    customAction(SampleProcess.process.id, CustomActionRequest("invalid-status")) ~> check {
      status shouldBe StatusCodes.Forbidden
      responseAs[CustomActionResponse] shouldBe CustomActionResponse(isSuccess = false, msg = s"Scenario status: WARNING is not allowed for action invalid-status")
    }
  }

  def decodeDetails: ProcessDetails = responseAs[ProcessDetails]
}
