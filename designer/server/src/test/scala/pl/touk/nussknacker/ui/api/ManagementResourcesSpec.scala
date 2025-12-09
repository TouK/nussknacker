package pl.touk.nussknacker.ui.api

import cats.instances.all._
import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport
import io.circe.Json
import org.apache.pekko.http.scaladsl.model.{ContentTypeRange, ContentTypes, HttpEntity, StatusCodes}
import org.apache.pekko.http.scaladsl.server
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.apache.pekko.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, OptionValues}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.BeMatcher
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.{MetaData, NodeId, StreamMetaData}
import pl.touk.nussknacker.engine.api.deployment.{ProcessAction, ScenarioActionName}
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.{ProcessName, VersionId}
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.graph.node.SubsequentNode
import pl.touk.nussknacker.engine.kafka.KafkaFactory
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.restmodel.DeployRequest
import pl.touk.nussknacker.restmodel.scenariodetails._
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.test.base.it.NuResourcesTest
import pl.touk.nussknacker.test.mock.MockDeploymentManagerSyntaxSugar.Ops
import pl.touk.nussknacker.test.utils.domain.ProcessTestData
import pl.touk.nussknacker.test.utils.domain.TestFactory.withPermissions
import pl.touk.nussknacker.ui.api.description.scenarioActivity.Dtos
import pl.touk.nussknacker.ui.process.ScenarioQuery
import pl.touk.nussknacker.ui.process.exception.ProcessIllegalAction
import pl.touk.nussknacker.ui.process.periodic.flink.FlinkClientStub
import pl.touk.nussknacker.ui.process.test.testcase.{Assertion, TestCase}

import java.time.Instant

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
    BeMatcher[(ScenarioActionName, VersionId)](equal((ScenarioActionName.Deploy, VersionId(versionId))))
      .compose[ProcessAction](a => (a.actionName, a.processVersionId))
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

    deploymentManager.withScenarioStateStatus(processName, SimpleStateStatus.DuringDeploy(VersionId(1))) {
      deployProcess(processName) ~> check {
        status shouldBe StatusCodes.Conflict
      }
    }
  }

  test("canceled process can't be canceled again") {
    createDeployedCanceledExampleScenario(processName)

    deploymentManager.withScenarioStateStatus(processName, SimpleStateStatus.Canceled) {
      cancelProcess(processName) ~> check {
        status shouldBe StatusCodes.Conflict
      }
    }
  }

  test("can't deploy archived process") {
    createArchivedProcess(processName)

    deploymentManager.withScenarioStateStatus(processName, SimpleStateStatus.Canceled) {
      deployProcess(processName) ~> check {
        status shouldBe StatusCodes.Conflict
        responseAs[String] shouldBe ProcessIllegalAction
          .archived(ScenarioActionName.Deploy, processName)
          .message
      }
    }
  }

  test("can't deploy fragment") {
    createValidProcess(processName, isFragment = true)

    deployProcess(processName) ~> check {
      status shouldBe StatusCodes.Conflict
      responseAs[String] shouldBe ProcessIllegalAction
        .fragment(ScenarioActionName.Deploy, processName)
        .message
    }
  }

  test("can't cancel fragment") {
    createValidProcess(processName, isFragment = true)

    deployProcess(processName) ~> check {
      status shouldBe StatusCodes.Conflict
      responseAs[String] shouldBe ProcessIllegalAction
        .fragment(ScenarioActionName.Deploy, processName)
        .message
    }
  }

  // TODO: To be removed. See comment in ManagementResources.deployRequestEntity
  test("deploys and cancels with plain text comment") {
    saveCanonicalProcessAndAssertSuccess(ProcessTestData.sampleScenario)
    deployProcessCommentDeprecated(
      ProcessTestData.sampleScenario.name,
      comment = Some("deployComment")
    ) ~> checkThatEventually {
      getProcess(processName) ~> check {
        val processDetails = responseAs[ScenarioWithDetails]
        processDetails.lastStateAction.exists(_.actionName == ScenarioActionName.Deploy) shouldBe true
      }
      cancelProcessCommentDeprecated(
        ProcessTestData.sampleScenario.name,
        comment = Some("cancelComment")
      ) ~> checkThatEventually {
        status shouldBe StatusCodes.OK
        getProcess(processName) ~> check {
          val processDetails = responseAs[ScenarioWithDetails]
          processDetails.lastStateAction.exists(_.actionName == ScenarioActionName.Cancel) shouldBe true
        }
      }
    }
  }

  // TODO: To be removed. See comment in ManagementResources.deployRequestEntity
  test("deploys and cancels with plain text no comment") {
    saveCanonicalProcessAndAssertSuccess(ProcessTestData.sampleScenario)
    deployProcessCommentDeprecated(
      ProcessTestData.sampleScenario.name,
      comment = None
    ) ~> checkThatEventually {
      status shouldBe StatusCodes.OK
      getProcess(processName) ~> check {
        val processDetails = responseAs[ScenarioWithDetails]
        processDetails.lastStateAction.exists(_.actionName == ScenarioActionName.Deploy) shouldBe true
      }
      cancelProcessCommentDeprecated(
        ProcessTestData.sampleScenario.name,
        comment = None
      ) ~> checkThatEventually {
        status shouldBe StatusCodes.OK
        getProcess(processName) ~> check {
          val processDetails = responseAs[ScenarioWithDetails]
          processDetails.lastStateAction.exists(_.actionName == ScenarioActionName.Cancel) shouldBe true
        }
      }
    }
  }

  test("deploys and cancels with comment") {
    saveCanonicalProcessAndAssertSuccess(ProcessTestData.sampleScenario)
    deployProcess(
      ProcessTestData.sampleScenario.name,
      comment = Some("deployComment")
    ) ~> check {
      eventually {
        getProcess(processName) ~> check {
          val processDetails = responseAs[ScenarioWithDetails]
          processDetails.lastStateAction.exists(_.actionName == ScenarioActionName.Deploy) shouldBe true
        }
      }
      cancelProcess(
        ProcessTestData.sampleScenario.name,
        comment = Some("cancelComment")
      ) ~> check {
        status shouldBe StatusCodes.OK
        // TODO: remove Deployment:, Stop: after adding custom icons
        val expectedDeployComment                = "deployComment"
        val expectedStopComment                  = "cancelComment"
        val expectedDeployCommentInLegacyService = s"Deployment: $expectedDeployComment"
        val expectedStopCommentInLegacyService   = s"Stop: $expectedStopComment"
        getActivity(ProcessTestData.sampleScenario.name) ~> check {
          val comments = responseAs[Dtos.Legacy.ProcessActivity].comments.sortBy(_.id)
          comments.map(_.content) shouldBe List(
            expectedDeployCommentInLegacyService,
            expectedStopCommentInLegacyService
          )
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
        processDetails.lastStateAction.exists(_.actionName == ScenarioActionName.Deploy) shouldBe true
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
            decodeDetails.lastStateAction.exists(_.actionName == ScenarioActionName.Cancel) shouldBe true
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
    import io.circe.syntax._
    saveCanonicalProcessAndAssertSuccess(ProcessTestData.sampleScenario)
    Post(
      s"/processManagement/deploy/${ProcessTestData.sampleScenario.name}",
      HttpEntity(
        ContentTypes.`application/json`,
        DeployRequest(None, None, None).asJson.noSpaces
      )
    ) ~> withPermissions(
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
      .filter("input", "#input != null".spel, Some(true))
      .emptySink(
        "end",
        "kafka-string",
        TopicParamName.value     -> "'end.topic'".spel,
        SinkValueParamName.value -> "#input".spel
      )

    saveCanonicalProcessAndAssertSuccess(processWithDisabledFilter)
    deployProcess(processName) ~> check {
      status shouldBe StatusCodes.OK
    }
  }

  test("should invoke actionInfoService to get default params in deploy without deployment data") {
    val testScenarioName = "test-scenario-with-default-params"
    val scenario = ScenarioBuilder
      .streaming(testScenarioName)
      .parallelism(1)
      .source("startProcess", "csv-source")
      .filter("input", "#input != null".spel)
      .processor(
        "logger",
        "log",
        "message" -> "Current #{#input}".spelTemplate,
        "logger"  -> "'deployment-test'".spel,
        "level"   -> "T(org.slf4j.event.Level).DEBUG".spel
      )
      .emptySink("end", "monitor")
    saveCanonicalProcessAndAssertSuccess(scenario)
    deployProcess(scenario.name) ~> checkThatEventually {
      status shouldBe StatusCodes.OK
      getProcess(scenario.name) ~> check {
        decodeDetails.lastStateAction shouldBe deployedWithVersions(2)
        updateCanonicalProcessAndAssertSuccess(scenario)
      }
    }
  }

  test("should return failure for not validating scenario") {
    val invalidScenario = ScenarioBuilder
      .streaming(processName.value)
      .parallelism(1)
      .source("start", "not existing")
      .emptySink(
        "end",
        "kafka-string",
        TopicParamName.value     -> "'end.topic'".spel,
        SinkValueParamName.value -> "#output".spel
      )
    saveCanonicalProcessAndAssertSuccess(invalidScenario)

    deploymentManager.withEmptyScenarioState(invalidScenario.name) {
      deployProcess(invalidScenario.name) ~> check {
        responseAs[String] shouldBe "Cannot deploy invalid scenario"
        status shouldBe StatusCodes.Conflict
      }
      getProcess(invalidScenario.name) ~> check {
        decodeDetails.state.value.status.name shouldEqual SimpleStateStatus.NotDeployed.name
      }
    }
  }

  test("should return failure for not validating deployment") {
    val requestedParallelism = FlinkClientStub.maxParallelism + 1
    val largeParallelismScenario = ProcessTestData.sampleScenario.copy(metaData =
      MetaData(
        ProcessTestData.sampleScenario.name.value,
        StreamMetaData(parallelism = Some(requestedParallelism))
      )
    )
    saveCanonicalProcessAndAssertSuccess(largeParallelismScenario)

    deploymentManager.withFailingDeployment(largeParallelismScenario.name) {
      deployProcess(largeParallelismScenario.name) ~> check {
        status shouldBe StatusCodes.BadRequest
        responseAs[
          String
        ] shouldBe s"Not enough free slots on Flink cluster. Available slots: ${FlinkClientStub.maxParallelism}, requested: ${requestedParallelism}. " +
          s"Decrease scenario's parallelism or extend Flink cluster resources"
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
    deploymentManager.withScenarioRunning(
      ProcessTestData.sampleScenario.name,
      VersionId(1),
      startedAt = Instant.now
    ) {
      snapshot(ProcessTestData.sampleScenario.name) ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldBe FlinkClientStub.savepointPath
      }
    }
  }

  test("stops process") {
    saveCanonicalProcessAndAssertSuccess(ProcessTestData.sampleScenario)
    deploymentManager.withScenarioRunning(
      ProcessTestData.sampleScenario.name,
      VersionId(1),
      startedAt = Instant.now
    ) {
      stop(ProcessTestData.sampleScenario.name) ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldBe FlinkClientStub.stopSavepointPath
      }
    }
  }

  test("return test results") {
    val testDataContent =
      """[
        |  {"sourceId":"startProcess","variables":{"input":["ala"]}},
        |  {"sourceId":"startProcess","variables":{"input":["bela"]}}
        |]""".stripMargin
    saveCanonicalProcessAndAssertSuccess(ProcessTestData.sampleScenario)

    testScenario(ProcessTestData.sampleScenario, testDataContent) ~> check {

      status shouldEqual StatusCodes.OK

      val responseJson = responseAs[Json]
      logger.debug(s"The response from the endpoint running scenario test from files was: $responseJson")

      val ctx = responseJson.hcursor
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
        .downN(0)
        .focus shouldBe Some(Json.fromString("ala"))
    }
  }

  test("run test case") {
    val testDataContent =
      """[
        |  {"sourceId":"startProcess","variables":{"input":["ala"]}},
        |  {"sourceId":"startProcess","variables":{"input":["bela"]}}
        |]""".stripMargin
    saveCanonicalProcessAndAssertSuccess(ProcessTestData.sampleScenario)

    val testCase = TestCase("dummy", testDataContent, Map.empty, Map(NodeId("endsuffix") -> List(
      Assertion("#TESTS.assertEquals('ala', #contexts[0].input[0])"),
      Assertion("#TESTS.assertEquals('ala', #contexts[1].input[0])"),
    )))
    runTestCase(ProcessTestData.sampleScenario, testCase) ~> check {
      status shouldEqual StatusCodes.OK

      val responseJson = responseAs[Json]
      responseJson.hcursor
        .downField("assertionsResults")
        .downField("endsuffix")
        .downN(0)
        .downField("type")
        .focus shouldBe Some(Json.fromString("SuccessfulAssertion"))

      responseJson.hcursor
        .downField("assertionsResults")
        .downField("endsuffix")
        .downN(1)
        .focus shouldBe Some(Json.obj(
        "type" -> Json.fromString("FailedAssertion"),
        "message" -> Json.fromString("Expected: [ala] but found [bela]")
      ))
    }
  }

  test("running test case with invalid test configuration (assertion on not existing node) should return bad request") {
    val testDataContent =
      """[
        |  {"sourceId":"startProcess","variables":{"input":["ala"]}},
        |  {"sourceId":"startProcess","variables":{"input":["bela"]}}
        |]""".stripMargin
    saveCanonicalProcessAndAssertSuccess(ProcessTestData.sampleScenario)

    val invalidTestCase = TestCase("dummy", testDataContent, Map.empty, Map(NodeId("someNotExistingNode") -> List(
      Assertion("#TESTS.assertEquals('ala', #contexts[0].input[0])"),
    )))
    runTestCase(ProcessTestData.sampleScenario, invalidTestCase) ~> check {
      status shouldEqual StatusCodes.BadRequest
      responseAs[String] shouldBe "Test case configuration contains errors: TestConfigurationRefersToNotExistingNode(someNotExistingNode,dummy,Assertion)"
    }
  }

  test("running test case with invalid scenario should return bad request") {
    val testDataContent =
      """[
        |  {"sourceId":"source","variables":{"input":["ala"]}},
        |  {"sourceId":"source","variables":{"input":["bela"]}}
        |]""".stripMargin
    saveCanonicalProcessAndAssertSuccess(ProcessTestData.sampleScenario)

    val testCase = TestCase("dummy", testDataContent, Map.empty, Map.empty)
    runTestCase(ProcessTestData.invalidProcess, testCase) ~> check {
      status shouldEqual StatusCodes.BadRequest
      val responseAsString = responseAs[String]
      //todo: should we return something more structured than toString of errors?
      responseAsString.startsWith("Only scenario without validation errors can be tested. Errors: ") shouldBe true
    }
  }

  test("nodeTransitionResults returned in test results should include null variables") {
    val scenario = ScenarioBuilder
      .streaming(ProcessTestData.sampleProcessName.value)
      .parallelism(1)
      .source("startProcess", "csv-source")
      .emptySink(
        "end",
        "kafka-string",
        TopicParamName.value     -> "'end.topic'".spel,
        SinkValueParamName.value -> "'foo'".spel
      )
    saveCanonicalProcessAndAssertSuccess(scenario)

    val testDataContent =
      """[
        |  {"sourceId":"startProcess","variables":{"input":null}}
        |]""".stripMargin
    testScenario(scenario, testDataContent) ~> check {
      status shouldEqual StatusCodes.OK

      val responseJson = responseAs[Json]
      logger.debug(s"The response from the endpoint running scenario test from files was: $responseJson")

      val inputVariable = responseJson.hcursor
        .downField("results")
        .downField("nodeTransitionResults")
        .downN(0)
        .downField("results")
        .downN(0)
        .downField("variables")
        .downField("input")
        .focus

      inputVariable.value shouldBe Json.Null
    }
  }

  test("return test results of errors, including null") {

    import pl.touk.nussknacker.engine.spel.SpelExtension._

    val process = ScenarioBuilder
      .streaming(processName.value)
      .parallelism(1)
      .source("startProcess", "csv-source")
      .filter("input", "new java.math.BigDecimal(null) == 0".spel)
      .emptySink(
        "end",
        "kafka-string",
        TopicParamName.value     -> "'end.topic'".spel,
        SinkValueParamName.value -> "''".spel
      )
    val testDataContent =
      """[
        |  {"sourceId":"startProcess","variables":{"input":["ala"]}},
        |  {"sourceId":"startProcess","variables":{"input":["bela"]}}
        |]""".stripMargin
    saveCanonicalProcessAndAssertSuccess(process)

    testScenario(process, testDataContent) ~> check {
      status shouldEqual StatusCodes.OK
    }
  }

  test("refuses to test if too much data received") {

    import pl.touk.nussknacker.engine.spel.SpelExtension._

    val process = {
      ScenarioBuilder
        .streaming(processName.value)
        .parallelism(1)
        .source("startProcess", "csv-source")
        .emptySink(
          "end",
          "kafka-string",
          TopicParamName.value     -> "'end.topic'".spel,
          SinkValueParamName.value -> "''".spel
        )
    }
    saveCanonicalProcessAndAssertSuccess(process)

    val tooManyRecords = List
      .fill(50)("""{"sourceId":"startProcess","variables":{"input":[]}}""")
      .mkString("[", ",", "]")
    testScenario(process, tooManyRecords) ~> check {
      status shouldEqual StatusCodes.BadRequest
      responseAs[
        String
      ] shouldBe "Test data has too many records (50). The maximum number of records permitted is 20. Contact the system administrator to increase this limit."
    }

    val longString = "a long json string".repeat(50)
    val tooManyCharacters = List
      .fill(20)(s"""{"sourceId":"startProcess","variables":{"input":"["$longString"]"}}""")
      .mkString("[", ",", "]")
    testScenario(process, tooManyCharacters) ~> check {
      status shouldEqual StatusCodes.BadRequest
      responseAs[
        String
      ] shouldBe "Test data has too many characters (19141). The maximum numbers of permitted characters is 10000. Contact the system administrator to increase this limit."
    }
  }

  test("refuses to test if too big test results generated") {
    import pl.touk.nussknacker.engine.spel.SpelExtension._

    val bigListExpression = (1 to 1000).mkString("{", ",", "}").spel
    val process = {
      ScenarioBuilder
        .streaming(processName.value)
        .parallelism(1)
        .source("startProcess", "csv-source")
        .buildSimpleVariable("big list", "bigList", bigListExpression)
        .emptySink(
          "end",
          "kafka-string",
          TopicParamName.value     -> "'end.topic'".spel,
          SinkValueParamName.value -> "''".spel
        )
    }
    saveCanonicalProcessAndAssertSuccess(process)

    val testDataContent = List
      .fill(10)("""{"sourceId":"startProcess","variables":{"input":[]}}""")
      .mkString("[", ",", "]")
    testScenario(process, testDataContent) ~> check {
      status shouldEqual StatusCodes.BadRequest
      // Approximate size can differ slightly depending on test execution environment.
      responseAs[
        String
      ] should fullyMatch regex "Test results size exceeded \\(approximate size is \\d+ B\\). The maximum permitted size is 500000 B. Contact the system administrator to increase this limit."
    }
  }

  test("rejects test record with non-existing source") {
    saveCanonicalProcessAndAssertSuccess(ProcessTestData.sampleScenario)
    val testDataContent =
      """[
        |  {"sourceId":"startProcess","variables":{"input":[]}},
        |  {"sourceId":"unknown","variables":{"input":"foo"}}
        |]""".stripMargin

    testScenario(ProcessTestData.sampleScenario, testDataContent) ~> check {
      status shouldEqual StatusCodes.BadRequest
      responseAs[String] shouldBe "Problem in sample 2 detected: source with id 'unknown' doesn't exist in the scenario"
    }
  }

  def decodeDetails: ScenarioWithDetails = responseAs[ScenarioWithDetails]

  def checkThatEventually[T](body: => T): RouteTestResult => T = check(eventually(body))

}
