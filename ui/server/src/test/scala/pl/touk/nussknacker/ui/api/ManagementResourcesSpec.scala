package pl.touk.nussknacker.ui.api

import java.time.LocalDateTime

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server
import akka.http.scaladsl.testkit.ScalatestRouteTest
import argonaut.Parse
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import pl.touk.nussknacker.engine.api.deployment.CustomProcess
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.ui.api.helpers.{EspItTest, TestFactory}
import pl.touk.nussknacker.ui.api.helpers.TestFactory._
import pl.touk.nussknacker.ui.codec.UiCodecs
import pl.touk.nussknacker.ui.api.helpers.TestProcessingTypes
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.restmodel.processdetails.{BasicProcess, DeploymentEntry, ProcessDetails}
import pl.touk.nussknacker.ui.sample.SampleProcess
import pl.touk.nussknacker.ui.security.api.Permission
import pl.touk.nussknacker.ui.util.MultipartUtils

import cats.syntax.semigroup._
import cats.instances.all._
import org.scalatest.matchers.{BeMatcher, MatchResult}
import pl.touk.nussknacker.engine.api.process.ProcessName
class ManagementResourcesSpec extends FunSuite with ScalatestRouteTest
  with Matchers with ScalaFutures with OptionValues with BeforeAndAfterEach with BeforeAndAfterAll with EspItTest {

  import UiCodecs._

  implicit override val patienceConfig = PatienceConfig(timeout = scaled(Span(2, Seconds)), interval = scaled(Span(100, Millis)))

  private val fixedTime = LocalDateTime.now()

  private def deployedWithVersions(versionIds: Long*) =
    BeMatcher(
      equal(versionIds.map(l => DeploymentEntry(l, TestFactory.testEnvironment, fixedTime, user().id, buildInfo))).matcher[List[DeploymentEntry]]
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

  test("deploy technical process and mark it as deployed") {
    implicit val loggedUser = user() copy(categoryPermissions = Map(testCategoryName->Set(Permission.Admin, Permission.Write, Permission.Deploy, Permission.Read)))
    val processId = "Process1"
    whenReady(writeProcessRepository.saveNewProcess(ProcessName(processId), testCategoryName, CustomProcess(""), TestProcessingTypes.Streaming, false)) { res =>
      deployProcess(processId) ~> check { status shouldBe StatusCodes.OK }
      getProcess(processId) ~> check {
        val processDetails = responseAs[String].decodeOption[ProcessDetails].get
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
    Post(s"/processManagement/deploy/${SampleProcess.process.id}") ~> withPermissions(deployRoute, testPermissionWrite) ~> check {
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
    val multiPart = MultipartUtils.prepareMultiParts("testData" -> "ala\nbela", "processJson" -> displayableProcess.asJson.nospaces)()
    Post(s"/processManagement/test/${SampleProcess.process.id}", multiPart) ~> withPermissions(deployRoute, testPermissionDeploy |+| testPermissionRead) ~> check {
      status shouldEqual StatusCodes.OK
      val results = Parse.parse(responseAs[String]).right.get
      for {
        invocation <- results.cursor --\ "invocationResults"
        endsuffix <- invocation --\ "endsuffix"
        firstRes <- endsuffix.first
        params <- firstRes --\ "params"
        context <- firstRes --\ "context"
        output <- params --\ "ouput"
        input <- context --\ "input"
      } yield {
        output.focus shouldBe jString("{message=message}")
        input.focus shouldBe jString("ala")
      }
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

    val multiPart = MultipartUtils.prepareMultiParts("testData" -> "ala\nbela", "processJson" -> displayableProcess.asJson.nospaces)()
    Post(s"/processManagement/test/${process.id}", multiPart) ~> withPermissions(deployRoute, testPermissionDeploy |+| testPermissionRead) ~> check {
      status shouldEqual StatusCodes.OK
    }
  }

  private def getHistoryDeployments = decodeDetails.history.flatMap(_.deployments)

  def decodeDetails: ProcessDetails = {
    responseAs[String].decodeOption[ProcessDetails].get
  }


  def decodeDetailsFromAll: BasicProcess = {
    responseAs[String].decodeOption[List[BasicProcess]].flatMap(_.find(_.name == SampleProcess.process.id)).get
  }
}
