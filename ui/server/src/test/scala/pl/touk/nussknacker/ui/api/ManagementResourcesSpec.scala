package pl.touk.nussknacker.ui.api

import java.time.LocalDateTime

import akka.http.scaladsl.model.{ContentTypeRange, StatusCodes}
import akka.http.scaladsl.server
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import pl.touk.nussknacker.engine.api.deployment.CustomProcess
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.ui.api.helpers.{EspItTest, SampleProcess, TestFactory, TestProcessingTypes}
import pl.touk.nussknacker.ui.api.helpers.TestFactory._
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.restmodel.processdetails._
import pl.touk.nussknacker.ui.security.api.Permission
import pl.touk.nussknacker.ui.util.MultipartUtils
import cats.syntax.semigroup._
import cats.instances.all._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Json
import org.scalatest.matchers.BeMatcher
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.ui.process.repository.ProcessActivityRepository.ProcessActivity
import io.circe.syntax._

class ManagementResourcesSpec extends FunSuite with ScalatestRouteTest with FailFastCirceSupport
  with Matchers with ScalaFutures with OptionValues with BeforeAndAfterEach with BeforeAndAfterAll with EspItTest {

  implicit override val patienceConfig = PatienceConfig(timeout = scaled(Span(2, Seconds)), interval = scaled(Span(100, Millis)))
  private implicit final val string: FromEntityUnmarshaller[String] = Unmarshaller.stringUnmarshaller.forContentTypes(ContentTypeRange.*)

  private val fixedTime = LocalDateTime.now()

  private def deployedWithVersions(versionIds: Long*) =
    BeMatcher(
      equal(versionIds.map(l => DeploymentEntry(l, TestFactory.testEnvironment, fixedTime, user("userId").id, buildInfo))).matcher[List[DeploymentEntry]]
    ).compose[List[DeploymentEntry]](_.map(_.copy(deployedAt = fixedTime)))



  test("process deployment should be visible in process history") {

    saveProcessAndAssertSuccess(SampleProcess.process.id, SampleProcess.process)
    deployProcess(SampleProcess.process.id) ~> check {
      status shouldBe StatusCodes.OK
      getSampleProcess ~> check {
        val oldDeployments = getHistoryDeployments
        decodeDetails.currentlyDeployedAt shouldBe deployedWithVersions(2)
        oldDeployments.size shouldBe 1
        updateProcessAndAssertSuccess(SampleProcess.process.id, SampleProcess.process)
        deployProcess(SampleProcess.process.id) ~> check {
          getSampleProcess ~> check {
            decodeDetails.currentlyDeployedAt shouldBe deployedWithVersions(2)

            val currentDeployments = getHistoryDeployments
            currentDeployments.size shouldBe 1
            currentDeployments.head.environment shouldBe env
            currentDeployments.head.deployedAt should not be oldDeployments.head.deployedAt
            val buildInfo = currentDeployments.head.buildInfo
            buildInfo("engine-version") should not be empty
          }
        }
      }
    }
  }

  test("deploys and cancels with comment") {
    saveProcessAndAssertSuccess(SampleProcess.process.id, SampleProcess.process)
    deployProcess(SampleProcess.process.id, true, Some("deployComment")) ~> check {
      cancelProcess(SampleProcess.process.id, true, Some("cancelComment")) ~> check {
        status shouldBe StatusCodes.OK
        Get(s"/processes/${SampleProcess.process.id}/activity") ~> withAllPermissions(processActivityRoute) ~> check {
          val comments = responseAs[ProcessActivity].comments.sortBy(_.id)
          comments.map(_.content) shouldBe List("Deployment: deployComment", "Stop: cancelComment")

          val firstCommentId::secondCommentId::Nil = comments.map(_.id)

          Get(s"/processes/${SampleProcess.process.id}/deployments") ~> withAllPermissions(processesRoute) ~> check {
            val deploymentHistory = responseAs[List[DeploymentHistoryEntry]]
            val curTime = LocalDateTime.now()
            deploymentHistory.map(_.copy(time = curTime)) shouldBe List(
              DeploymentHistoryEntry(2, curTime, user("userId").id, DeploymentAction.Cancel, Some(secondCommentId), Some("Stop: cancelComment"), Map()),
              DeploymentHistoryEntry(2, curTime, user("userId").id, DeploymentAction.Deploy, Some(firstCommentId), Some("Deployment: deployComment"), TestFactory.buildInfo)
            )
          }
        }
      }
    }
  }

  test("rejects deploy without comment if comment needed") {
    saveProcessAndAssertSuccess(SampleProcess.process.id, SampleProcess.process)
    deployProcess(SampleProcess.process.id, true) ~> check {
      rejection shouldBe server.ValidationRejection("Comment is required", None)
    }
  }

  test("deploy technical process and mark it as deployed") {
    implicit val loggedUser = user("userId", Map(testCategoryName->Set(Permission.Write, Permission.Deploy, Permission.Read)))
    val processId = "Process1"
    whenReady(writeProcessRepository.saveNewProcess(ProcessName(processId), testCategoryName, CustomProcess(""), TestProcessingTypes.Streaming, false)) { res =>
      deployProcess(processId) ~> check { status shouldBe StatusCodes.OK }
      getProcess(processId) ~> check {
        val processDetails = responseAs[ProcessDetails]
        processDetails.currentlyDeployedAt shouldBe deployedWithVersions(1)
      }
    }
  }

  test("recognize process cancel in deployment list") {
    saveProcessAndAssertSuccess(SampleProcess.process.id, SampleProcess.process)
    deployProcess(SampleProcess.process.id) ~> check {
      status shouldBe StatusCodes.OK
      getSampleProcess ~> check {
        decodeDetails.currentlyDeployedAt shouldBe deployedWithVersions(2)
        cancelProcess(SampleProcess.process.id) ~> check {
          getSampleProcess ~> check {
            decodeDetails.currentlyDeployedAt shouldBe deployedWithVersions()
            val currentDeployments = getHistoryDeployments
            currentDeployments shouldBe empty
          }
        }
      }
    }
  }


  test("recognize process deploy and cancel in global process list") {
    saveProcessAndAssertSuccess(SampleProcess.process.id, SampleProcess.process)
    deployProcess(SampleProcess.process.id) ~> check {
      status shouldBe StatusCodes.OK
      getProcesses ~> check {
        decodeDetailsFromAll.currentlyDeployedAt shouldBe deployedWithVersions(2)
        cancelProcess(SampleProcess.process.id) ~> check {
          getProcesses ~> check {
            decodeDetailsFromAll.currentlyDeployedAt shouldBe deployedWithVersions()
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

  test("return error on deployment failure") {
    saveProcessAndAssertSuccess(SampleProcess.process.id, SampleProcess.process)

    processManager.withFailingDeployment {
      deployProcess(SampleProcess.process.id) ~> check {
        status shouldBe StatusCodes.InternalServerError
      }
    }
  }

  test("return test results") {
    saveProcessAndAssertSuccess(SampleProcess.process.id, SampleProcess.process)
    val displayableProcess = ProcessConverter.toDisplayable(ProcessCanonizer.canonize(SampleProcess.process)
      , TestProcessingTypes.Streaming)
    val multiPart = MultipartUtils.prepareMultiParts("testData" -> "ala\nbela", "processJson" -> displayableProcess.asJson.noSpaces)()
    Post(s"/processManagement/test/${SampleProcess.process.id}", multiPart) ~> withPermissions(deployRoute(), testPermissionDeploy |+| testPermissionRead) ~> check {
      status shouldEqual StatusCodes.OK

      val ctx = responseAs[Json] .hcursor
              .downField("results")
              .downField("nodeResults")
              .downField("endsuffix")
              .downArray
              .first
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

    val process = {
        EspProcessBuilder
          .id("sampleProcess")
          .parallelism(1)
          .exceptionHandler("param1" -> "'ala'")
          .source("startProcess", "csv-source")
          .filter("input", "new java.math.BigDecimal(null) == 0")
          .emptySink("end", "kafka-string", "topic" -> "'end.topic'")
      }

    saveProcessAndAssertSuccess(process.id, process)

    val displayableProcess = ProcessConverter.toDisplayable(ProcessCanonizer.canonize(process), TestProcessingTypes.Streaming)

    val multiPart = MultipartUtils.prepareMultiParts("testData" -> "ala\nbela", "processJson" -> displayableProcess.asJson.noSpaces)()
    Post(s"/processManagement/test/${process.id}", multiPart) ~> withPermissions(deployRoute(), testPermissionDeploy |+| testPermissionRead) ~> check {
      status shouldEqual StatusCodes.OK
    }
  }

  private def getHistoryDeployments = decodeDetails.history.flatMap(_.deployments)

  def decodeDetails: ProcessDetails = {
    responseAs[ProcessDetails]
  }


  def decodeDetailsFromAll: BasicProcess = {
    responseAs[List[BasicProcess]].find(_.name == SampleProcess.process.id).get
  }
}
