package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.{ContentTypeRange, StatusCodes}
import akka.http.scaladsl.server
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import cats.instances.all._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Json
import io.dropwizard.metrics5.MetricRegistry
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.BeMatcher
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, OptionValues}
import org.scalatestplus.mockito.MockitoSugar
import pl.touk.nussknacker.engine.ClassLoaderModelData
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.deployment.{ProcessAction, ProcessActionType, ScenarioActionName}
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessName, VersionId}
import pl.touk.nussknacker.engine.api.test.ScenarioTestData
import pl.touk.nussknacker.engine.api.{Context, MetaData, StreamMetaData}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.definition.test.ModelDataTestInfoProvider
import pl.touk.nussknacker.engine.kafka.KafkaFactory
import pl.touk.nussknacker.engine.schemedkafka.AvroUtils
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testmode.TestProcess.TestResults
import pl.touk.nussknacker.engine.util.loader.ModelClassLoader
import pl.touk.nussknacker.restmodel.scenariodetails._
import pl.touk.nussknacker.restmodel.{CustomActionRequest, CustomActionResponse}
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.test.base.it.NuResourcesTest
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig.TestProcessingType.Streaming
import pl.touk.nussknacker.test.mock.{MockDeploymentManager, StubModelDataWithModelDefinition}
import pl.touk.nussknacker.test.utils.domain.TestFactory._
import pl.touk.nussknacker.test.utils.domain.{ProcessTestData, TestFactory}
import pl.touk.nussknacker.ui.process.ScenarioQuery
import pl.touk.nussknacker.ui.process.deployment.ScenarioTestExecutorService
import pl.touk.nussknacker.ui.process.exception.ProcessIllegalAction
import pl.touk.nussknacker.ui.process.repository.DbProcessActivityRepository.ProcessActivity
import pl.touk.nussknacker.ui.process.test.{PreliminaryScenarioTestDataSerDe, ScenarioTestService}
import pl.touk.nussknacker.ui.processreport.ProcessCounter
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}

class ManagementResourcesSpec
    extends AnyFunSuite
    with ScalatestRouteTest
    with FailFastCirceSupport
    with Matchers
    with PatientScalaFutures
    with OptionValues
    with MockitoSugar
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
      .emptySink("end", "kafka-string", TopicParamName -> "'end.topic'", SinkValueParamName -> "#input")

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
      .emptySink("end", "kafka-string", TopicParamName -> "'end.topic'", SinkValueParamName -> "#output")
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
    val process = ScenarioBuilder
      .streaming(processName.value)
      .parallelism(1)
      .source("startProcess", "csv-source")
      .filter("input", "new java.math.BigDecimal(null) == 0")
      .emptySink("end", "kafka-string", TopicParamName -> "'end.topic'", SinkValueParamName -> "''")
    val testDataContent =
      """{"sourceId":"startProcess","record":"ala"}
        |"bela"""".stripMargin
    saveCanonicalProcessAndAssertSuccess(process)

    testScenario(process, testDataContent) ~> check {
      status shouldEqual StatusCodes.OK
    }
  }

  test("refuses to test if too much data") {
    val process =
      ScenarioBuilder
        .streaming(processName.value)
        .source("startProcess", "csv-source")
        .emptySink("end", "kafka-string", TopicParamName -> "'end.topic'")

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

  test("return proper converted to JSON Object (with including Model's ClassLoader) test results data") {
    val sourceId                   = "source"
    val managementResourcesForTest = prepareStubbedManagementResourcesForTest(sourceId, None)

    val dumpScenario = ScenarioBuilder
      .streaming(processName.value)
      .source(sourceId, ProcessTestData.existingSourceFactory)
      .emptySink("sink", ProcessTestData.existingSinkFactory)

    saveCanonicalProcessAndAssertSuccess(dumpScenario)

    testScenario(dumpScenario, "", managementResourcesForTest) ~> check {
      status shouldEqual StatusCodes.OK

      val ctx = responseAs[Json].hcursor
        .downField("results")
        .downField("nodeResults")
        .downField(sourceId)
        .downArray
        .downField("variables")

      // Default behavior with GenericRecordWithSchemaId, source value is converted to JSON Object
      ctx
        .downField("input")
        .downField("pretty")
        .focus shouldBe Some(
        Json.fromFields(
          Map(
            "name" -> Json.fromString("lcl"),
            "age"  -> Json.fromInt(18)
          )
        )
      )
    }
  }

  test("return proper converted to String (without including Model's ClassLoader) test results data") {
    object EmptyClassLoader extends ClassLoader(null)

    val sourceId                   = "source"
    val managementResourcesForTest = prepareStubbedManagementResourcesForTest(sourceId, Some(EmptyClassLoader))

    val dumpScenario = ScenarioBuilder
      .streaming(processName.value)
      .source(sourceId, ProcessTestData.existingSourceFactory)
      .emptySink("sink", ProcessTestData.existingSinkFactory)

    saveCanonicalProcessAndAssertSuccess(dumpScenario)

    testScenario(dumpScenario, "", managementResourcesForTest) ~> check {
      status shouldEqual StatusCodes.OK

      val ctx = responseAs[Json].hcursor
        .downField("results")
        .downField("nodeResults")
        .downField(sourceId)
        .downArray
        .downField("variables")

      // In the tests on the classPath there are all classes, so converter uses .toString on GenericRecordWithSchemaId and as a result we have string
      ctx
        .downField("input")
        .downField("pretty")
        .focus shouldBe Some(Json.fromString("{\"name\": \"lcl\", \"age\": 18}"))
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
      responseAs[String] shouldBe "Action: invalid-status is not allowed in scenario (fooProcess) state: NOT_DEPLOYED, allowed actions: hello,not-implemented."
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

  private def prepareStubbedManagementResourcesForTest(
      sourceId: String,
      maybeClassLoader: Option[ClassLoader]
  ): ManagementResources = {
    val stubbedValueWhichClassShouldNotBeOnClassPath = {
      val schema = AvroUtils.parseSchema(s"""{
                                            |  "type": "record",
                                            |  "name": "SampleRecord",
                                            |  "fields": [
                                            |    { "name": "name", "type": "string" },
                                            |    { "name": "age", "type": "int" }
                                            |  ]
                                            |}
    """.stripMargin)

      AvroUtils.createRecord(schema, Map("name" -> "lcl", "age" -> 18))
    }

    val stubbedTestResults = TestResults(
      nodeResults = Map(
        sourceId -> List(Context(sourceId, Map("input" -> stubbedValueWhichClassShouldNotBeOnClassPath)))
      ),
      invocationResults = Map.empty,
      externalInvocationResults = Map.empty,
      exceptions = List.empty
    )

    val stubbedTestExecutorServiceImpl = mock[ScenarioTestExecutorService]
    when(
      stubbedTestExecutorServiceImpl.testProcess(any[ProcessIdWithName], any[CanonicalProcess], any[ScenarioTestData])(
        any[LoggedUser],
        any[ExecutionContext]
      )
    ).thenReturn(Future.successful(stubbedTestResults))

    val scenarioTestService = new ScenarioTestService(
      new ModelDataTestInfoProvider(modelData),
      processResolver,
      featureTogglesConfig.testDataSettings,
      new PreliminaryScenarioTestDataSerDe(featureTogglesConfig.testDataSettings),
      new ProcessCounter(TestFactory.prepareSampleFragmentRepository),
      stubbedTestExecutorServiceImpl
    )

    val stubbedTypeToConfig = typeToConfig.mapValues { value =>
      val modelData = value.designerModelData.modelData.asInstanceOf[ClassLoaderModelData]

      new StubModelDataWithModelDefinition(modelData.modelDefinition) {
        override def modelClassLoader: ModelClassLoader =
          maybeClassLoader
            .map(ModelClassLoader(_, List.empty))
            .getOrElse(modelData.modelClassLoader)
      }
    }

    new ManagementResources(
      processAuthorizer = processAuthorizer,
      processService = processService,
      deploymentService = deploymentService,
      dispatcher = dmDispatcher,
      metricRegistry = new MetricRegistry,
      scenarioTestServices = mapProcessingTypeDataProvider(
        Streaming.stringify -> scenarioTestService
      ),
      typeToConfig = stubbedTypeToConfig
    )
  }

}
