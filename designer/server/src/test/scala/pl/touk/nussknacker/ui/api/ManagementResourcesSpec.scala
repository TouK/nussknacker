package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.{ContentTypeRange, ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import cats.instances.all._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Json
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.BeMatcher
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, OptionValues}
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.deployment.{ProcessAction, ProcessActionType, ScenarioActionName}
import pl.touk.nussknacker.engine.api.process.{ProcessName, VersionId}
import pl.touk.nussknacker.engine.api.{MetaData, StreamMetaData}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.kafka.KafkaFactory
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.restmodel.scenariodetails._
import pl.touk.nussknacker.restmodel.{CustomActionRequest, CustomActionResponse}
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.test.utils.domain.TestFactory.{withAllPermissions, withPermissions}
import pl.touk.nussknacker.test.base.it.NuResourcesTest
import pl.touk.nussknacker.test.mock.MockDeploymentManager
import pl.touk.nussknacker.test.utils.domain.{ProcessTestData, TestFactory}
import pl.touk.nussknacker.ui.process.ScenarioQuery
import pl.touk.nussknacker.ui.process.exception.ProcessIllegalAction
import pl.touk.nussknacker.ui.process.repository.DbProcessActivityRepository.ProcessActivity

// TODO: all these tests should be migrated to ManagementApiHttpServiceBusinessSpec or ManagementApiHttpServiceSecuritySpec
class ManagementResourcesSpec
    extends AnyFunSuite
    with ScalatestRouteTest
    with FailFastCirceSupport
    with Matchers
    with PatientScalaFutures
    with OptionValues
    with BeforeAndAfterEach
    with BeforeAndAfterAll
    with NuResourcesTest {

  import KafkaFactory._

  private implicit final val string: FromEntityUnmarshaller[String] =
    Unmarshaller.stringUnmarshaller.forContentTypes(ContentTypeRange.*)

  private val processName: ProcessName = ProcessTestData.sampleScenario.name

  private def deployedWithVersions(versionId: Long): BeMatcher[Option[ProcessAction]] = {
    BeMatcher[(ProcessActionType, VersionId)](equal((ProcessActionType.Deploy, VersionId(versionId))))
      .compose[ProcessAction](a => (a.actionType, a.processVersionId))
      .compose[Option[ProcessAction]](opt => opt.value)
  }

  test("process deployment should be visible in process history") {
    saveCanonicalProcessAndAssertSuccess(ProcessTestData.sampleScenario)
    deployProcess(processName) ~> checkThatEventually {
      status shouldBe StatusCodes.OK
      getProcess(processName) ~> check {
        decodeDetails.lastStateAction shouldBe deployedWithVersions(2)
        updateCanonicalProcessAndAssertSuccess(ProcessTestData.sampleScenario)
        deployProcess(processName) ~> checkThatEventually {
          getProcess(processName) ~> check {
            decodeDetails.lastStateAction shouldBe deployedWithVersions(2)
          }
        }
      }
    }
  }

  test("process during deploy cannot be deployed again") {
    createDeployedExampleScenario(processName)

    deploymentManager.withProcessStateStatus(processName, SimpleStateStatus.DuringDeploy) {
      deployProcess(processName) ~> check {
        status shouldBe StatusCodes.Conflict
      }
    }
  }

  test("canceled process can't be canceled again") {
    createDeployedCanceledExampleScenario(processName)

    deploymentManager.withProcessStateStatus(processName, SimpleStateStatus.Canceled) {
      cancelProcess(processName) ~> check {
        status shouldBe StatusCodes.Conflict
      }
    }
  }

  test("can't deploy archived process") {
    createArchivedProcess(processName)

    deploymentManager.withProcessStateStatus(processName, SimpleStateStatus.Canceled) {
      deployProcess(processName) ~> check {
        status shouldBe StatusCodes.Conflict
        responseAs[String] shouldBe ProcessIllegalAction
          .archived(ScenarioActionName(ProcessActionType.Deploy), processName)
          .message
      }
    }
  }

  test("can't deploy fragment") {
    createValidProcess(processName, isFragment = true)

    deployProcess(processName) ~> check {
      status shouldBe StatusCodes.Conflict
      responseAs[String] shouldBe ProcessIllegalAction
        .fragment(ScenarioActionName(ProcessActionType.Deploy), processName)
        .message
    }
  }

  test("can't cancel fragment") {
    createValidProcess(processName, isFragment = true)

    deployProcess(processName) ~> check {
      status shouldBe StatusCodes.Conflict
      responseAs[String] shouldBe ProcessIllegalAction
        .fragment(ScenarioActionName(ProcessActionType.Deploy), processName)
        .message
    }
  }

  test("deploys and cancels with comment") {
    saveCanonicalProcessAndAssertSuccess(ProcessTestData.sampleScenario)
    deployProcess(
      ProcessTestData.sampleScenario.name,
      comment = Some("deployComment")
    ) ~> checkThatEventually {
      getProcess(processName) ~> check {
        val processDetails = responseAs[ScenarioWithDetails]
        processDetails.lastStateAction.exists(_.actionType.equals(ProcessActionType.Deploy)) shouldBe true
      }
      cancelProcess(
        ProcessTestData.sampleScenario.name,
        comment = Some("cancelComment")
      ) ~> check {
        status shouldBe StatusCodes.OK
        // TODO: remove Deployment:, Stop: after adding custom icons
        val expectedDeployComment = "Deployment: deployComment"
        val expectedStopComment   = "Stop: cancelComment"
        getActivity(ProcessTestData.sampleScenario.name) ~> check {
          val comments = responseAs[ProcessActivity].comments.sortBy(_.id)
          comments.map(_.content) shouldBe List(expectedDeployComment, expectedStopComment)

          val firstCommentId :: secondCommentId :: Nil = comments.map(_.id)

          Get(s"/processes/${ProcessTestData.sampleScenario.name}/deployments") ~> withAllPermissions(
            processesRoute
          ) ~> check {
            val deploymentHistory = responseAs[List[ProcessAction]]
            deploymentHistory.map(a =>
              (a.processVersionId, a.user, a.actionType, a.commentId, a.comment, a.buildInfo)
            ) shouldBe List(
              (
                VersionId(2),
                TestFactory.user().username,
                ProcessActionType.Cancel,
                Some(secondCommentId),
                Some(expectedStopComment),
                Map()
              ),
              (
                VersionId(2),
                TestFactory.user().username,
                ProcessActionType.Deploy,
                Some(firstCommentId),
                Some(expectedDeployComment),
                TestFactory.buildInfo
              )
            )
          }
        }
      }
    }
  }

  test("deploy technical process and mark it as deployed") {
    createValidProcess(processName)

    deployProcess(processName) ~> checkThatEventually {
      status shouldBe StatusCodes.OK
      getProcess(processName) ~> check {
        val processDetails = responseAs[ScenarioWithDetails]
        processDetails.lastStateAction shouldBe deployedWithVersions(1)
        processDetails.lastStateAction.exists(_.actionType.equals(ProcessActionType.Deploy)) shouldBe true
      }
    }
  }

  test("recognize process cancel in deployment list") {
    saveCanonicalProcessAndAssertSuccess(ProcessTestData.sampleScenario)
    deployProcess(ProcessTestData.sampleScenario.name) ~> checkThatEventually {
      status shouldBe StatusCodes.OK
      getProcess(processName) ~> check {
        decodeDetails.lastStateAction shouldBe deployedWithVersions(2)
        cancelProcess(ProcessTestData.sampleScenario.name) ~> check {
          getProcess(processName) ~> check {
            decodeDetails.lastStateAction.exists(_.actionType.equals(ProcessActionType.Cancel)) shouldBe true
          }
        }
      }
    }
  }

  test("recognize process deploy and cancel in global process list") {
    saveCanonicalProcessAndAssertSuccess(ProcessTestData.sampleScenario)
    deployProcess(ProcessTestData.sampleScenario.name) ~> checkThatEventually {
      status shouldBe StatusCodes.OK

      forScenariosReturned(ScenarioQuery.empty) { processes =>
        val process = processes.find(_.name == ProcessTestData.sampleScenario.name.value).head
        process.lastActionVersionId shouldBe Some(2L)
        process.isDeployed shouldBe true

        cancelProcess(ProcessTestData.sampleScenario.name) ~> check {
          forScenariosReturned(ScenarioQuery.empty) { processes =>
            val process = processes.find(_.name == ProcessTestData.sampleScenario.name.value).head
            process.lastActionVersionId shouldBe Some(2L)
            process.isCanceled shouldBe true
          }
        }
      }
    }
  }

  test("not authorize user with write permission to deploy") {
    saveCanonicalProcessAndAssertSuccess(ProcessTestData.sampleScenario)
    Post(s"/processManagement/deploy/${ProcessTestData.sampleScenario.name}") ~> withPermissions(
      deployRoute(),
      Permission.Write
    ) ~> check {
      rejection shouldBe server.AuthorizationFailedRejection
    }
  }

  test("should allow deployment of scenario with warning") {
    val processWithDisabledFilter = ScenarioBuilder
      .streaming(processName.value)
      .parallelism(1)
      .source("startProcess", "csv-source")
      .filter("input", "#input != null", Some(true))
      .emptySink("end", "kafka-string", TopicParamName.value -> "'end.topic'", SinkValueParamName.value -> "#input")

    saveCanonicalProcessAndAssertSuccess(processWithDisabledFilter)
    deployProcess(processName) ~> check {
      status shouldBe StatusCodes.OK
    }
  }

  test("should return failure for not validating scenario") {
    val invalidScenario = ScenarioBuilder
      .streaming(processName.value)
      .parallelism(1)
      .source("start", "not existing")
      .emptySink("end", "kafka-string", TopicParamName.value -> "'end.topic'", SinkValueParamName.value -> "#output")
    saveCanonicalProcessAndAssertSuccess(invalidScenario)

    deploymentManager.withEmptyProcessState(invalidScenario.name) {
      deployProcess(invalidScenario.name) ~> check {
        responseAs[String] shouldBe "Cannot deploy invalid scenario"
        status shouldBe StatusCodes.Conflict
      }
      getProcess(invalidScenario.name) ~> check {
        decodeDetails.state.value.status shouldEqual SimpleStateStatus.NotDeployed
      }
    }
  }

  test("should return failure for not validating deployment") {
    val largeParallelismScenario = ProcessTestData.sampleScenario.copy(metaData =
      MetaData(
        ProcessTestData.sampleScenario.name.value,
        StreamMetaData(parallelism = Some(MockDeploymentManager.maxParallelism + 1))
      )
    )
    saveCanonicalProcessAndAssertSuccess(largeParallelismScenario)

    deploymentManager.withFailingDeployment(largeParallelismScenario.name) {
      deployProcess(largeParallelismScenario.name) ~> check {
        status shouldBe StatusCodes.BadRequest
        responseAs[String] shouldBe "Parallelism too large"
      }
    }
  }

  test("return from deploy before deployment manager proceeds") {
    saveCanonicalProcessAndAssertSuccess(ProcessTestData.sampleScenario)

    deploymentManager.withWaitForDeployFinish(ProcessTestData.sampleScenario.name) {
      deployProcess(ProcessTestData.sampleScenario.name) ~> check {
        status shouldBe StatusCodes.OK
      }
    }
  }

  test("snapshots process") {
    saveCanonicalProcessAndAssertSuccess(ProcessTestData.sampleScenario)
    deploymentManager.withProcessRunning(ProcessTestData.sampleScenario.name) {
      snapshot(ProcessTestData.sampleScenario.name) ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldBe MockDeploymentManager.savepointPath
      }
    }
  }

  test("stops process") {
    saveCanonicalProcessAndAssertSuccess(ProcessTestData.sampleScenario)
    deploymentManager.withProcessRunning(ProcessTestData.sampleScenario.name) {
      stop(ProcessTestData.sampleScenario.name) ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldBe MockDeploymentManager.stopSavepointPath
      }
    }
  }

  test("return test results") {
    val testDataContent =
      """{"sourceId":"startProcess","record":"ala"}
        |{"sourceId":"startProcess","record":"bela"}""".stripMargin
    saveCanonicalProcessAndAssertSuccess(ProcessTestData.sampleScenario)

    testScenario(ProcessTestData.sampleScenario, testDataContent) ~> check {

      status shouldEqual StatusCodes.OK

      val ctx = responseAs[Json].hcursor
        .downField("results")
        .downField("nodeResults")
        .downField("endsuffix")
        .downArray
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
      .streaming(processName.value)
      .parallelism(1)
      .source("startProcess", "csv-source")
      .filter("input", "new java.math.BigDecimal(null) == 0")
      .emptySink("end", "kafka-string", TopicParamName.value -> "'end.topic'", SinkValueParamName.value -> "''")
    val testDataContent =
      """{"sourceId":"startProcess","record":"ala"}
        |"bela"""".stripMargin
    saveCanonicalProcessAndAssertSuccess(process)

    testScenario(process, testDataContent) ~> check {
      status shouldEqual StatusCodes.OK
    }
  }

  test("refuses to test if too much data") {

    import pl.touk.nussknacker.engine.spel.Implicits._

    val process = {
      ScenarioBuilder
        .streaming(processName.value)
        .parallelism(1)
        .source("startProcess", "csv-source")
        .emptySink("end", "kafka-string", TopicParamName.value -> "'end.topic'")
    }
    saveCanonicalProcessAndAssertSuccess(process)
    val tooLargeTestDataContentList = List((1 to 50).mkString("\n"), (1 to 50000).mkString("-"))

    tooLargeTestDataContentList.foreach { tooLargeData =>
      testScenario(process, tooLargeData) ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }
  }

  test("rejects test record with non-existing source") {
    saveCanonicalProcessAndAssertSuccess(ProcessTestData.sampleScenario)
    val testDataContent =
      """{"sourceId":"startProcess","record":"ala"}
        |{"sourceId":"unknown","record":"bela"}""".stripMargin

    testScenario(ProcessTestData.sampleScenario, testDataContent) ~> check {
      status shouldEqual StatusCodes.BadRequest
      responseAs[String] shouldBe "Record 2 - scenario does not have source id: 'unknown'"
    }
  }

  test("execute valid custom action") {
    createEmptyProcess(ProcessTestData.sampleProcessName)
    customAction(
      ProcessTestData.sampleProcessName,
      CustomActionRequest(ScenarioActionName("hello"))
    ) ~> check {
      status shouldBe StatusCodes.OK
      responseAs[CustomActionResponse] shouldBe CustomActionResponse(isSuccess = true, msg = "Hi")
    }
  }

  test("execute non existing custom action") {
    createEmptyProcess(ProcessTestData.sampleProcessName)
    customAction(
      ProcessTestData.sampleProcessName,
      CustomActionRequest(ScenarioActionName("non-existing"))
    ) ~> check {
      status shouldBe StatusCodes.NotFound
      responseAs[String] shouldBe "non-existing is not existing"
    }
  }

  test("execute not implemented custom action") {
    createEmptyProcess(ProcessTestData.sampleProcessName)
    customAction(
      ProcessTestData.sampleProcessName,
      CustomActionRequest(ScenarioActionName("not-implemented"))
    ) ~> check {
      status shouldBe StatusCodes.NotImplemented
      responseAs[String] shouldBe "an implementation is missing"
    }
  }

  test("execute custom action with not allowed process status") {
    createEmptyProcess(ProcessTestData.sampleProcessName)
    customAction(
      ProcessTestData.sampleProcessName,
      CustomActionRequest(ScenarioActionName("invalid-status"))
    ) ~> check {
      // TODO: "conflict" is coherrent with "canceled process can't be canceled again" above, consider changing to Forbidden
      status shouldBe StatusCodes.Conflict
      responseAs[String] shouldBe "Action: invalid-status is not allowed in scenario (fooProcess) state: NOT_DEPLOYED, allowed actions: not-implemented,ARCHIVE,DEPLOY,RENAME,hello."
    }
  }

  test("should return 403 when execute custom action on archived process") {
    createArchivedProcess(ProcessTestData.sampleProcessName)
    customAction(
      ProcessTestData.sampleProcessName,
      CustomActionRequest(ScenarioActionName("hello"))
    ) ~> check {
      // TODO: "conflict" is coherrent with "can't deploy fragment" above, consider changing to Forbidden
      status shouldBe StatusCodes.Conflict
    }
  }

  test("should return 403 when execute custom action on fragment") {
    createEmptyProcess(ProcessTestData.sampleProcessName, isFragment = true)
    customAction(
      ProcessTestData.sampleProcessName,
      CustomActionRequest(ScenarioActionName("hello"))
    ) ~> check {
      // TODO: "conflict" is coherrent with "can't deploy fragment" above, consider changing to Forbidden
      status shouldBe StatusCodes.Conflict
    }
  }

  def decodeDetails: ScenarioWithDetails = responseAs[ScenarioWithDetails]

  def checkThatEventually[T](body: => T): RouteTestResult => T = check(eventually(body))

}
