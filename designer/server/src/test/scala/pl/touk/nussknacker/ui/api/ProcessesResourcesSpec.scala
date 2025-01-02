package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.headers.{BasicHttpCredentials, RawHeader}
import akka.http.scaladsl.model.{ContentTypeRange, StatusCode, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import cats.data.OptionT
import cats.instances.all._
import com.typesafe.config.{Config, ConfigValueFactory}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.scalatest.LoneElement._
import org.scalatest._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.development.manager.MockableDeploymentManagerProvider.MockableDeploymentManager
import pl.touk.nussknacker.engine.api.ProcessAdditionalFields
import pl.touk.nussknacker.engine.api.component.ProcessingMode
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessStateDefinitionManager, SimpleStateStatus}
import pl.touk.nussknacker.engine.api.graph.{ProcessProperties, ScenarioGraph}
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioWithDetails
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationResult
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.test.base.it._
import pl.touk.nussknacker.test.config.WithAccessControlCheckingDesignerConfig.TestCategory.{Category1, Category2}
import pl.touk.nussknacker.test.config.WithAccessControlCheckingDesignerConfig.TestProcessingType.{
  Streaming1,
  Streaming2
}
import pl.touk.nussknacker.test.config.WithAccessControlCheckingDesignerConfig.{TestCategory, TestProcessingType}
import pl.touk.nussknacker.test.config.{WithAccessControlCheckingDesignerConfig, WithMockableDeploymentManager}
import pl.touk.nussknacker.test.utils.domain.{ProcessTestData, TestFactory}
import pl.touk.nussknacker.test.utils.scalas.AkkaHttpExtensions.toRequestEntity
import pl.touk.nussknacker.ui.api.description.scenarioActivity.Dtos.Legacy.ProcessActivity
import pl.touk.nussknacker.ui.api.description.scenarioActivity.Dtos.ScenarioActivityCommentContent.{
  Available,
  NotAvailable
}
import pl.touk.nussknacker.ui.api.description.scenarioActivity.Dtos.{
  ScenarioActivities,
  ScenarioActivity,
  ScenarioActivityComment,
  ScenarioActivityType
}
import pl.touk.nussknacker.ui.config.scenariotoolbar.CategoriesScenarioToolbarsConfigParser
import pl.touk.nussknacker.ui.config.scenariotoolbar.ToolbarButtonConfigType.{CustomLink, ProcessDeploy, ProcessSave}
import pl.touk.nussknacker.ui.config.scenariotoolbar.ToolbarPanelTypeConfig.{
  CreatorPanel,
  ProcessActionsPanel,
  SearchPanel,
  TipsPanel
}
import pl.touk.nussknacker.ui.process.ProcessService.{CreateScenarioCommand, UpdateScenarioCommand}
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository
import pl.touk.nussknacker.ui.process.{ScenarioQuery, ScenarioToolbarSettings, ToolbarButton, ToolbarPanel}
import pl.touk.nussknacker.ui.security.api.SecurityError.ImpersonationMissingPermissionError
import pl.touk.nussknacker.ui.security.api.{AuthManager, LoggedUser}
import pl.touk.nussknacker.ui.server.RouteInterceptor

import scala.concurrent.Future

/**
 * TODO: On resource tests we should verify permissions and encoded response data. All business logic should be tested at ProcessServiceDb.
 */
class ProcessesResourcesSpec
    extends AnyFunSuite
    with NuItTest
    with WithAccessControlCheckingDesignerConfig
    with WithAccessControlCheckingConfigScenarioHelper
    with WithMockableDeploymentManager
    with ScalatestRouteTest
    with Matchers
    with Inside
    with FailFastCirceSupport
    with PatientScalaFutures
    with OptionValues
    with EitherValues {

  import ProcessesQueryEnrichments._
  import io.circe._
  import io.circe.parser._

  private implicit final val string: FromEntityUnmarshaller[String] =
    Unmarshaller.stringUnmarshaller.forContentTypes(ContentTypeRange.*)

  // TODO: when the spec is rewritten with RestAssured instead of Akka-http test kit, remember to remove
  //       the RouteInterceptor and its usages
  private lazy val applicationRoute = RouteInterceptor.get()

  private val processName: ProcessName = ProcessTestData.sampleProcessName
  private val archivedProcessName      = ProcessName("archived")
  private val fragmentName             = ProcessName("fragment")
  private val archivedFragmentName     = ProcessName("archived-fragment")

  override def designerConfig: Config = super.designerConfig
    .withValue(
      "scenarioTypes.streaming1.modelConfig.kafka.topicsExistenceValidationConfig.enabled",
      ConfigValueFactory.fromAnyRef("false")
    )

  test("should return list of process with state") {
    createDeployedExampleScenario(processName, category = Category1)
    verifyProcessWithStateOnList(processName, Some(SimpleStateStatus.Running))
  }

  test("should return list of fragment with no state") {
    createEmptyFragment(processName, category = Category1)
    verifyProcessWithStateOnList(processName, None)
  }

  test("should return list of archived process with no state") {
    createArchivedExampleScenario(processName, category = Category1)
    verifyProcessWithStateOnList(processName, Some(SimpleStateStatus.NotDeployed))
  }

  test("/api/processes and /api/processesDetails should return lighter details without history versions") {
    saveCanonicalProcess(ProcessTestData.validProcess, category = Category1) {
      forScenariosReturned(ScenarioQuery.empty) { processes =>
        every(processes.map(_.history)) shouldBe empty
      }
      forScenariosDetailsReturned(ScenarioQuery.empty) { processes =>
        every(processes.map(_.history)) shouldBe empty
      }
    }
  }

  test("/api/processes should return lighter details without ProcessAction's additional fields and null values") {
    def hasNullAttributes(json: Json): Boolean = {
      json.fold(
        jsonNull = true, // null found
        jsonBoolean = _ => false,
        jsonNumber = _ => false,
        jsonString = _ => false,
        jsonArray = _.exists(hasNullAttributes),
        jsonObject = _.values.exists(hasNullAttributes)
      )
    }
    createDeployedExampleScenario(processName, category = Category1)
    Get(s"/api/processes") ~> withReaderUser() ~> applicationRoute ~> check {
      status shouldEqual StatusCodes.OK
      // verify that unnecessary fields were omitted
      val decodedScenarios = responseAs[List[ScenarioWithDetails]]
      decodedScenarios.head.lastAction should matchPattern {
        case Some(
              ProcessAction(_, _, _, _, _, _, _, _, None, None, None, buildInfo)
            ) if buildInfo.isEmpty =>
      }
      decodedScenarios.head.lastStateAction should matchPattern {
        case Some(
              ProcessAction(_, _, _, _, _, _, _, _, None, None, None, buildInfo)
            ) if buildInfo.isEmpty =>
      }
      decodedScenarios.head.lastDeployedAction should matchPattern {
        case Some(
              ProcessAction(_, _, _, _, _, _, _, _, None, None, None, buildInfo)
            ) if buildInfo.isEmpty =>
      }
      // verify that null values were not present in JSON response
      val rawFetchedScenarios = responseAs[Json]
      hasNullAttributes(rawFetchedScenarios) shouldBe false
    }
  }

  test("return single process") {
    createDeployedExampleScenario(processName, category = Category1)
    MockableDeploymentManager.configureScenarioStatuses(
      Map(processName.value -> SimpleStateStatus.Running)
    )

    forScenarioReturned(processName) { process =>
      process.name shouldBe processName.value
      process.state.map(_.name) shouldBe Some(SimpleStateStatus.Running.name)
      process.state.map(_.tooltip) shouldBe Some(
        SimpleProcessStateDefinitionManager.statusTooltip(SimpleStateStatus.Running)
      )
      process.state.map(_.description) shouldBe Some(
        SimpleProcessStateDefinitionManager.statusDescription(SimpleStateStatus.Running)
      )
      process.state.map(_.icon) shouldBe Some(
        SimpleProcessStateDefinitionManager.statusIcon(SimpleStateStatus.Running)
      )
    }
  }

  test("spel template expression is validated properly") {
    createDeployedScenario(ProcessTestData.sampleSpelTemplateProcess, category = Category1)

    Get(
      s"/api/processes/${ProcessTestData.sampleSpelTemplateProcess.name.value}"
    ) ~> withReaderUser() ~> applicationRoute ~> check {
      val newProcessDetails = responseAs[ScenarioWithDetails]
      newProcessDetails.processVersionId shouldBe VersionId.initialVersionId

      responseAs[String] should include("validationResult")
      responseAs[String] should not include "ExpressionParserCompilationError"
    }
  }

  test("return validated and non-validated process") {
    createEmptyScenario(processName, category = Category1)

    Get(s"/api/processes/$processName") ~> withReaderUser() ~> applicationRoute ~> check {
      status shouldEqual StatusCodes.OK
      val validated = responseAs[ScenarioWithDetails]
      validated.name shouldBe processName
      validated.validationResult.value.errors should not be empty
    }

    Get(
      s"/api/processes/$processName?skipValidateAndResolve=true"
    ) ~> withReaderUser() ~> applicationRoute ~> check {
      status shouldEqual StatusCodes.OK
      val validated = responseAs[ScenarioWithDetails]
      validated.name shouldBe processName
      validated.validationResult shouldBe empty
    }
  }

  // FIXME: Implement fragment validation
  ignore("not allow to archive still used fragment") {
    val processWithFragment = ProcessTestData.validProcessWithFragment(processName)
    val fragmentGraph       = CanonicalProcessConverter.toScenarioGraph(processWithFragment.fragment)
    saveFragment(ProcessName("f1"), fragmentGraph, category = Category1)(succeed)
    saveCanonicalProcess(processWithFragment.process, category = Category1)(succeed)

    archiveProcess(processName) { status =>
      status shouldEqual StatusCodes.Conflict
    }
  }

  test("not allow to archive still running process") {
    createDeployedExampleScenario(processName, category = Category1)
    MockableDeploymentManager.configureScenarioStatuses(
      Map(processName.value -> SimpleStateStatus.Running)
    )

    archiveProcess(processName) { status =>
      status shouldEqual StatusCodes.Conflict
    }
  }

  test("allow to archive fragment used in archived process") {
    val processWithFragment = ProcessTestData.validProcessWithFragment(processName)
    val fragmentName        = processWithFragment.fragment.name
    val fragmentGraph       = CanonicalProcessConverter.toScenarioGraph(processWithFragment.fragment)
    saveFragment(fragmentName, fragmentGraph, category = Category1)(succeed)
    saveCanonicalProcess(processWithFragment.process, category = Category1)(succeed)

    archiveProcess(processName) { status =>
      status shouldEqual StatusCodes.OK
    }

    archiveProcess(fragmentName) { status =>
      status shouldEqual StatusCodes.OK
    }
  }

  test("or not allow to create new scenario named as archived one") {
    val process = ProcessTestData.validProcess
    saveCanonicalProcess(process, category = Category1)(succeed)

    archiveProcess(processName) { status =>
      status shouldEqual StatusCodes.OK
    }

    createProcessRequest(processName, category = Category1, isFragment = false) { status =>
      status shouldBe StatusCodes.BadRequest
      responseAs[String] shouldEqual s"Scenario $processName already exists"
    }
  }

  test("should allow to rename not deployed process") {
    val processId = createEmptyScenario(processName, category = Category1)
    val newName   = ProcessName("ProcessChangedName")

    renameProcess(processName, newName) { status =>
      status shouldEqual StatusCodes.OK
      getProcessId(newName) shouldBe processId
    }
  }

  test("should allow to rename canceled process") {
    val processId = createDeployedCanceledExampleScenario(processName, category = Category1)
    val newName   = ProcessName("ProcessChangedName")

    renameProcess(processName, newName) { status =>
      status shouldEqual StatusCodes.OK
      getProcessId(newName) shouldBe processId
    }
  }

  test("should not allow to rename deployed process") {
    createDeployedExampleScenario(processName, category = Category1)
    MockableDeploymentManager.configureScenarioStatuses(
      Map(processName.value -> SimpleStateStatus.Running)
    )

    val newName = ProcessName("ProcessChangedName")

    renameProcess(processName, newName) { status =>
      status shouldEqual StatusCodes.Conflict
    }
  }

  test("should not allow to rename archived process") {
    createArchivedExampleScenario(processName, category = Category1)
    val newName = ProcessName("ProcessChangedName")

    renameProcess(processName, newName) { status =>
      status shouldEqual StatusCodes.Conflict
    }
  }

  /**
   * FIXME: We don't support situation when process is running on flink but action is not deployed - warning state (isRunning = false).
   * In that case we can change process name.. We should block rename process in that situation.
   */
  ignore("should not allow to rename process with running state") {
    createEmptyScenario(processName, category = Category1)
    MockableDeploymentManager.configureScenarioStatuses(
      Map(processName.value -> SimpleStateStatus.Running)
    )

    val newName = ProcessName("ProcessChangedName")
    renameProcess(processName, newName) { status =>
      status shouldEqual StatusCodes.Conflict
    }
  }

  test("should allow to rename fragment") {
    val processId = createEmptyFragment(processName, category = Category1)
    val newName   = ProcessName("ProcessChangedName")

    renameProcess(processName, newName) { status =>
      status shouldEqual StatusCodes.OK
      getProcessId(newName) shouldBe processId
    }
  }

  test("not allow to save archived process") {
    createArchivedExampleScenario(processName, category = Category1)
    val process = ProcessTestData.validProcess

    updateCanonicalProcess(process) {
      status shouldEqual StatusCodes.Conflict
    }
  }

  test("should return list of all processes and fragments") {
    createEmptyScenario(processName, category = Category1)
    createEmptyFragment(fragmentName, category = Category1)
    createArchivedExampleScenario(archivedProcessName, category = Category1)
    createArchivedExampleFragment(archivedFragmentName, category = Category1)

    verifyListOfProcesses(
      ScenarioQuery.empty,
      List(processName, fragmentName, archivedProcessName, archivedFragmentName)
    )
    verifyListOfProcesses(ScenarioQuery.empty.unarchived(), List(processName, fragmentName))
    verifyListOfProcesses(ScenarioQuery.empty.archived(), List(archivedProcessName, archivedFragmentName))
  }

  test("return list of all fragments") {
    createEmptyScenario(processName, category = Category1)
    createEmptyFragment(fragmentName, category = Category1)
    createArchivedExampleScenario(archivedProcessName, category = Category1)
    createArchivedExampleFragment(archivedFragmentName, category = Category1)

    verifyListOfProcesses(ScenarioQuery.empty.fragment(), List(fragmentName, archivedFragmentName))
    verifyListOfProcesses(ScenarioQuery.empty.fragment().unarchived(), List(fragmentName))
    verifyListOfProcesses(ScenarioQuery.empty.fragment().archived(), List(archivedFragmentName))
  }

  test("should return list of processes") {
    createEmptyScenario(processName, category = Category1)
    createEmptyFragment(fragmentName, category = Category1)
    createArchivedExampleScenario(archivedProcessName, category = Category1)
    createArchivedExampleFragment(archivedFragmentName, category = Category1)

    verifyListOfProcesses(ScenarioQuery.empty.process(), List(processName, archivedProcessName))
    verifyListOfProcesses(ScenarioQuery.empty.process().unarchived(), List(processName))
    verifyListOfProcesses(ScenarioQuery.empty.process().archived(), List(archivedProcessName))
  }

  test("return process if user has category") {
    createEmptyScenario(processName, category = Category1)

    forScenarioReturned(processName) { process =>
      process.processCategory shouldBe Category1.stringify
    }
  }

  test("not return processes not in user categories") {
    createEmptyScenario(processName, category = Category2)

    tryForScenarioReturned(processName) { (status, _) =>
      status shouldEqual StatusCodes.NotFound
    }

    forScenariosReturned(ScenarioQuery.empty) { processes =>
      processes.isEmpty shouldBe true
    }
    forScenariosDetailsReturned(ScenarioQuery.empty) { processes =>
      processes.isEmpty shouldBe true
    }
    forScenariosDetailsReturned(ScenarioQuery.empty.withCategories(List(Category1))) { processes =>
      processes.isEmpty shouldBe true
    }
  }

  test("return all processes for admin user") {
    createEmptyScenario(processName, category = Category1)

    forScenarioReturned(processName, isAdmin = true) { _ => }

    forScenariosReturned(ScenarioQuery.empty, isAdmin = true) { processes =>
      processes.exists(_.name == processName.value) shouldBe true
    }
    forScenariosDetailsReturned(ScenarioQuery.empty, isAdmin = true) { processes =>
      processes.exists(_.name == processName) shouldBe true
    }
  }

  test("search processes by categories") {
    createEmptyScenario(ProcessName("proc1"), category = Category1)
    createEmptyScenario(ProcessName("proc2"), category = Category2)

    forScenariosReturned(ScenarioQuery.empty, isAdmin = true) { processes =>
      processes.size shouldBe 2
    }
    forScenariosDetailsReturned(ScenarioQuery.empty, isAdmin = true) { processes =>
      processes.size shouldBe 2
    }

    forScenariosReturned(ScenarioQuery.empty.withCategories(List(Category1)), isAdmin = true) { processes =>
      processes.loneElement.name shouldBe "proc1"
    }
    forScenariosDetailsReturned(ScenarioQuery.empty.withCategories(List(Category1)), isAdmin = true) { processes =>
      processes.loneElement.name.value shouldBe "proc1"
    }

    forScenariosReturned(ScenarioQuery.empty.withCategories(List(Category2)), isAdmin = true) { processes =>
      processes.loneElement.name shouldBe "proc2"
    }
    forScenariosDetailsReturned(ScenarioQuery.empty.withCategories(List(Category2)), isAdmin = true) { processes =>
      processes.loneElement.name.value shouldBe "proc2"
    }

    forScenariosReturned(ScenarioQuery.empty.withCategories(List(Category1, Category2)), isAdmin = true) { processes =>
      processes.size shouldBe 2
    }
    forScenariosDetailsReturned(ScenarioQuery.empty.withCategories(List(Category1, Category2)), isAdmin = true) {
      processes =>
        processes.size shouldBe 2
    }
  }

  test("search processes by processing types") {
    createEmptyScenario(processName, category = Category1)

    forScenariosReturned(ScenarioQuery.empty.withProcessingTypes(List(Streaming1))) { processes =>
      processes.size shouldBe 1
    }
    forScenariosDetailsReturned(ScenarioQuery.empty.withProcessingTypes(List(Streaming1))) { processes =>
      processes.size shouldBe 1
    }
    forScenariosReturned(ScenarioQuery.empty.withProcessingTypes(List(Streaming2))) { processes =>
      processes.size shouldBe 0
    }
    forScenariosDetailsReturned(ScenarioQuery.empty.withProcessingTypes(List(Streaming2))) { processes =>
      processes.size shouldBe 0
    }
  }

  test("search processes by names") {
    createEmptyScenario(ProcessName("proc1"), category = Category1)
    createEmptyScenario(ProcessName("proc2"), category = Category1)

    forScenariosReturned(ScenarioQuery.empty.withNames(List("proc1"))) { processes =>
      processes.loneElement.name shouldBe "proc1"
    }
    forScenariosDetailsReturned(ScenarioQuery.empty.withNames(List("proc1"))) { processes =>
      processes.loneElement.name.value shouldBe "proc1"
    }
    forScenariosReturned(ScenarioQuery.empty.withNames(List("proc3"))) { processes =>
      processes.size shouldBe 0
    }
    forScenariosDetailsReturned(ScenarioQuery.empty.withNames(List("proc3"))) { processes =>
      processes.size shouldBe 0
    }
  }

  test("search processes with multiple parameters") {
    createEmptyScenario(ProcessName("proc1"), category = Category1)
    createEmptyScenario(ProcessName("proc2"), category = Category2)
    createArchivedExampleScenario(ProcessName("proc3"), category = Category1)

    forScenariosReturned(
      ScenarioQuery.empty
        .withNames(List("proc1", "proc3", "procNotExisting"))
        .withCategories(List(Category1))
        .withProcessingTypes(List(Streaming1))
        .unarchived()
    ) { processes =>
      processes.loneElement.name shouldBe "proc1"
    }
    forScenariosDetailsReturned(
      ScenarioQuery.empty
        .withNames(List("proc1", "proc3", "procNotExisting"))
        .withCategories(List(Category1))
        .withProcessingTypes(List(Streaming1))
        .unarchived()
    ) { processes =>
      processes.loneElement.name.value shouldBe "proc1"
    }
    forScenariosReturned(
      ScenarioQuery.empty
        .withNames(List("proc1", "proc3", "procNotExisting"))
        .withCategories(List(Category1))
        .withProcessingTypes(List(Streaming1))
        .archived()
    ) { processes =>
      processes.loneElement.name shouldBe "proc3"
    }
    forScenariosDetailsReturned(
      ScenarioQuery.empty
        .withNames(List("proc1", "proc3", "procNotExisting"))
        .withCategories(List(Category1))
        .withProcessingTypes(List(Streaming1))
        .archived()
    ) { processes =>
      processes.loneElement.name.value shouldBe "proc3"
    }
    forScenariosReturned(ScenarioQuery.empty.withNames(List("proc1")).withRawCategories(List("unknown"))) { processes =>
      processes.size shouldBe 0
    }
    forScenariosDetailsReturned(ScenarioQuery.empty.withNames(List("proc1")).withRawCategories(List("unknown"))) {
      processes =>
        processes.size shouldBe 0
    }
  }

  test("search processes by isDeployed") {
    val firstProcessor  = ProcessName("Processor1")
    val secondProcessor = ProcessName("Processor2")
    val thirdProcessor  = ProcessName("Processor3")

    createEmptyScenario(firstProcessor, category = Category1)
    createDeployedCanceledExampleScenario(secondProcessor, category = Category1)
    createDeployedExampleScenario(thirdProcessor, category = Category1)

    MockableDeploymentManager.configureScenarioStatuses(
      Map(
        secondProcessor.value -> SimpleStateStatus.Canceled,
        thirdProcessor.value  -> SimpleStateStatus.Running
      )
    )

    forScenariosReturned(ScenarioQuery.empty) { processes =>
      processes.size shouldBe 3
      val status = processes.find(_.name == firstProcessor.value).flatMap(_.state.map(_.name))
      status shouldBe Some(SimpleStateStatus.NotDeployed.name)
    }
    forScenariosDetailsReturned(ScenarioQuery.empty) { processes =>
      processes.size shouldBe 3
    }

    forScenariosReturned(ScenarioQuery.empty.deployed()) { processes =>
      processes.size shouldBe 1
      val status = processes.find(_.name == thirdProcessor.value).flatMap(_.state.map(_.name))
      status shouldBe Some(SimpleStateStatus.Running.name)
    }
    forScenariosDetailsReturned(ScenarioQuery.empty.deployed()) { processes =>
      processes.size shouldBe 1
    }

    forScenariosReturned(ScenarioQuery.empty.notDeployed()) { processes =>
      processes.size shouldBe 2

      val status = processes.find(_.name == thirdProcessor.value).flatMap(_.state.map(_.name))
      status shouldBe None

      val canceledProcess = processes.find(_.name == secondProcessor.value).flatMap(_.state.map(_.name))
      canceledProcess shouldBe Some(SimpleStateStatus.Canceled.name)
    }
    forScenariosDetailsReturned(ScenarioQuery.empty.notDeployed()) { processes =>
      processes.size shouldBe 2
    }
  }

  test("return 404 when no process") {
    tryForScenarioReturned(ProcessName("nont-exists")) { (status, _) =>
      status shouldEqual StatusCodes.NotFound
    }
  }

  test("return sample process details") {
    createEmptyScenario(processName, category = Category1)

    forScenarioReturned(processName) { process =>
      process.name shouldBe processName.value
    }
  }

  test("save correct process json with ok status") {
    val validProcess = ScenarioBuilder
      .streaming("valid")
      .source("startProcess", "real-kafka", "Topic" -> s"'sometopic'".spel)
      .emptySink("end", "kafka-string", "Topic" -> s"'output'".spel, "Value" -> "#input".spel)
    saveCanonicalProcess(validProcess, category = Category1) {
      status shouldEqual StatusCodes.OK
      val fetchedScenario = fetchScenario(validProcess.name)
      fetchedScenario.nodes.head.id shouldEqual validProcess.nodes.head.id
      val validationResult = entityAs[ValidationResult]
      validationResult.errors.invalidNodes shouldBe Map.empty
    }
  }

  test("update process with the same json should not create new version") {
    val command = ProcessTestData.createEmptyUpdateProcessCommand(None)

    createProcessRequest(processName, category = Category1, isFragment = false) { code =>
      code shouldBe StatusCodes.Created

      forScenarioReturned(processName)(_ => ())
      doUpdateProcess(command, processName) {
        forScenarioReturned(processName) { process =>
          process.history.map(_.size) shouldBe Some(1)
        }
        status shouldEqual StatusCodes.OK
      }
    }
  }

  test("update scenario labels when the scenario does not have any") {
    val properties = ProcessProperties(
      ProcessAdditionalFields(
        description = None,
        properties = Map.empty,
        metaDataType = "StreamMetaData"
      )
    )
    val scenarioGraph = ScenarioGraph(
      properties = properties,
      nodes = List.empty,
      edges = List.empty
    )
    val command = UpdateScenarioCommand(scenarioGraph, None, Some(List("tag1", "tag2")), None)

    createProcessRequest(processName, category = Category1, isFragment = false) { code =>
      code shouldBe StatusCodes.Created
      forScenarioReturned(processName) { scenario =>
        scenario.labels shouldBe List.empty[String]
      }
      doUpdateProcess(command, processName) {
        forScenarioReturned(processName) { scenario =>
          scenario.labels shouldBe List("tag1", "tag2")
        }
        status shouldEqual StatusCodes.OK
      }
    }
  }

  test("update scenario labels when the scenario does have some") {
    val properties = ProcessProperties(
      ProcessAdditionalFields(
        description = None,
        properties = Map.empty,
        metaDataType = "StreamMetaData"
      )
    )
    val scenarioGraph = ScenarioGraph(
      properties = properties,
      nodes = List.empty,
      edges = List.empty
    )

    createProcessRequest(processName, category = Category1, isFragment = false) { code =>
      code shouldBe StatusCodes.Created
      forScenarioReturned(processName) { scenario =>
        scenario.labels shouldBe List.empty[String]
      }
      val command1 = UpdateScenarioCommand(
        scenarioGraph = scenarioGraph,
        comment = None,
        scenarioLabels = Some(List("tag2", "tag1")),
        forwardedUserName = None
      )
      doUpdateProcess(command1, processName) {
        forScenarioReturned(processName) { scenario =>
          scenario.labels shouldBe List("tag1", "tag2")
        }
        status shouldEqual StatusCodes.OK
      }
      val command2 = UpdateScenarioCommand(
        scenarioGraph = scenarioGraph,
        comment = None,
        scenarioLabels = Some(List("tag3", "tag1", "tag4")),
        forwardedUserName = None
      )
      doUpdateProcess(command2, processName) {
        forScenarioReturned(processName) { scenario =>
          scenario.labels shouldBe List("tag1", "tag3", "tag4")
        }
        status shouldEqual StatusCodes.OK
      }
      val command3 = UpdateScenarioCommand(
        scenarioGraph = scenarioGraph,
        comment = None,
        scenarioLabels = Some(List("tag3")),
        forwardedUserName = None
      )
      doUpdateProcess(command3, processName) {
        forScenarioReturned(processName) { scenario =>
          scenario.labels shouldBe List("tag3")
        }
        status shouldEqual StatusCodes.OK
      }
    }
  }

  test("update process with the same json should add comment for current version") {
    val process = ProcessTestData.validProcess
    val comment = "Update the same version"

    saveCanonicalProcess(process, category = Category1) {
      forScenarioReturned(processName) { process =>
        process.history.map(_.size) shouldBe Some(2)
      }
      status shouldEqual StatusCodes.OK
    }

    updateCanonicalProcess(process, Some(comment)) {
      forScenarioReturned(processName) { process =>
        process.history.map(_.size) shouldBe Some(2)
      }
      status shouldEqual StatusCodes.OK
    }

    updateCanonicalProcess(process, None) {
      forScenarioReturned(processName) { process =>
        process.history.map(_.size) shouldBe Some(2)
      }
      status shouldEqual StatusCodes.OK
    }

    getDeprecatedActivity(processName) ~> check {
      val comments = responseAs[ProcessActivity].comments
      comments.loneElement.content shouldBe comment
    }

    getScenarioActivities(processName) ~> check {
      val activities = responseAs[ScenarioActivities].activities

      activities.length shouldBe 3
      activities(0) shouldBe ScenarioActivity(
        id = activities(0).id,
        user = "allpermuser",
        date = activities(0).date,
        scenarioVersionId = Some(1L),
        comment = None,
        attachment = None,
        additionalFields = Nil,
        overrideIcon = None,
        overrideDisplayableName = None,
        overrideSupportedActions = None,
        `type` = ScenarioActivityType.ScenarioCreated
      )
      activities(1) shouldBe ScenarioActivity(
        id = activities(1).id,
        user = "allpermuser",
        date = activities(1).date,
        scenarioVersionId = Some(2L),
        comment = Some(
          ScenarioActivityComment(
            content = NotAvailable,
            lastModifiedBy = "allpermuser",
            lastModifiedAt = activities(1).comment.get.lastModifiedAt
          )
        ),
        attachment = None,
        additionalFields = Nil,
        overrideIcon = None,
        overrideDisplayableName = Some("Version 2 saved"),
        overrideSupportedActions = None,
        `type` = ScenarioActivityType.ScenarioModified
      )
      activities(2) shouldBe ScenarioActivity(
        id = activities(2).id,
        user = "allpermuser",
        date = activities(2).date,
        scenarioVersionId = Some(2L),
        comment = Some(
          ScenarioActivityComment(
            content = Available("Update the same version"),
            lastModifiedBy = "allpermuser",
            lastModifiedAt = activities(2).comment.get.lastModifiedAt
          )
        ),
        attachment = None,
        additionalFields = Nil,
        overrideIcon = None,
        overrideDisplayableName = None,
        overrideSupportedActions = None,
        `type` = ScenarioActivityType.ScenarioModified
      )
    }
  }

  test("return details of process with empty expression") {
    saveCanonicalProcess(ProcessTestData.validProcessWithEmptySpelExpr, category = Category1) {
      Get(s"/api/processes/$processName") ~> withAllPermUser() ~> applicationRoute ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] should include(processName.value)
      }
    }
  }

  test("save invalid process json with ok status but with non empty invalid nodes") {
    saveCanonicalProcess(ProcessTestData.invalidProcess, category = Category1) {
      status shouldEqual StatusCodes.OK
      fetchScenario(
        ProcessTestData.invalidProcess.name
      ).nodes.head.id shouldEqual ProcessTestData.invalidProcess.nodes.head.id
      entityAs[ValidationResult].errors.invalidNodes.isEmpty shouldBe false
    }
  }

  test("return one latest version for process") {
    saveCanonicalProcess(ProcessTestData.validProcess, category = Category1) {
      status shouldEqual StatusCodes.OK
    }

    updateCanonicalProcess(ProcessTestData.invalidProcess) {
      status shouldEqual StatusCodes.OK
    }

    forScenariosReturned(ScenarioQuery.empty) { processes =>
      val process = processes.find(_.name == ProcessTestData.sampleScenario.name.value)

      withClue(process) {
        process.isDefined shouldBe true
      }
    }
    forScenariosDetailsReturned(ScenarioQuery.empty) { processes =>
      processes.exists(_.name == ProcessTestData.sampleScenario.name) shouldBe true
    }
  }

  test("save process history") {
    saveCanonicalProcess(ProcessTestData.validProcess, category = Category1) {
      status shouldEqual StatusCodes.OK
    }

    val meta = ProcessTestData.validProcess.metaData
    val changedMeta = meta.copy(additionalFields =
      ProcessAdditionalFields(Some("changed description..."), Map.empty, meta.additionalFields.metaDataType)
    )
    updateCanonicalProcess(ProcessTestData.validProcess.copy(metaData = changedMeta)) {
      status shouldEqual StatusCodes.OK
    }

    getProcess(processName) ~> check {
      val processDetails = responseAs[ScenarioWithDetails]
      processDetails.name shouldBe ProcessTestData.sampleScenario.name
      processDetails.history.value.length shouldBe 3
    }
  }

  test("access process version and mark latest version") {
    saveCanonicalProcess(ProcessTestData.validProcess, category = Category1) {
      status shouldEqual StatusCodes.OK
    }

    updateCanonicalProcess(ProcessTestData.invalidProcess) {
      status shouldEqual StatusCodes.OK
    }

    Get(s"/api/processes/${ProcessTestData.sampleScenario.name}/1") ~> withAllPermUser() ~> applicationRoute ~> check {
      val processDetails = responseAs[ScenarioWithDetails]
      processDetails.processVersionId shouldBe VersionId.initialVersionId
      processDetails.isLatestVersion shouldBe false
    }

    Get(s"/api/processes/${ProcessTestData.sampleScenario.name}/2") ~> withAllPermUser() ~> applicationRoute ~> check {
      val processDetails = responseAs[ScenarioWithDetails]
      processDetails.processVersionId shouldBe VersionId(2)
      processDetails.isLatestVersion shouldBe false
    }

    Get(s"/api/processes/${ProcessTestData.sampleScenario.name}/3") ~> withAllPermUser() ~> applicationRoute ~> check {
      val processDetails = responseAs[ScenarioWithDetails]
      processDetails.processVersionId shouldBe VersionId(3)
      processDetails.isLatestVersion shouldBe true
    }
  }

  test("return non-validated process version") {
    createEmptyScenario(processName, category = Category1)

    Get(
      s"/api/processes/$processName/1?skipValidateAndResolve=true"
    ) ~> withAllPermUser() ~> applicationRoute ~> check {
      val processDetails = responseAs[ScenarioWithDetails]
      processDetails.processVersionId shouldBe VersionId.initialVersionId
      processDetails.validationResult shouldBe empty
    }
  }

  test("perform idempotent process save") {
    saveCanonicalProcessAndAssertSuccess(ProcessTestData.validProcess, category = Category1)
    Get(s"/api/processes/${ProcessTestData.sampleScenario.name}") ~> withAllPermUser() ~> applicationRoute ~> check {
      val processHistoryBeforeDuplicatedWrite = responseAs[ScenarioWithDetails].history.value
      updateCanonicalProcessAndAssertSuccess(ProcessTestData.validProcess)
      Get(s"/api/processes/${ProcessTestData.sampleScenario.name}") ~> withAllPermUser() ~> applicationRoute ~> check {
        val processHistoryAfterDuplicatedWrite = responseAs[ScenarioWithDetails].history.value
        processHistoryAfterDuplicatedWrite shouldBe processHistoryBeforeDuplicatedWrite
      }
    }
  }

  test("not authorize user with read permissions to create scenario") {
    val command = CreateScenarioCommand(
      processName,
      Some(Category1.stringify),
      processingMode = None,
      engineSetupName = None,
      isFragment = false,
      forwardedUserName = None
    )
    Post(s"/api/processes", command.toJsonRequestEntity()) ~> withReaderUser() ~> applicationRoute ~> check {
      status shouldEqual StatusCodes.Unauthorized
    }
  }

  test("authorize impersonated user with write permissions to create scenario") {
    val command = CreateScenarioCommand(
      processName,
      Some(Category1.stringify),
      processingMode = None,
      engineSetupName = None,
      isFragment = false,
      forwardedUserName = None
    )
    Post(s"/api/processes", command.toJsonRequestEntity()) ~>
      withAllPermUser() ~>
      impersonateWriterUser() ~>
      applicationRoute ~>
      check {
        status shouldEqual StatusCodes.Created
      }
  }

  test("not authorize user trying to impersonate without appropriate permission") {
    val command = CreateScenarioCommand(
      processName,
      Some(Category1.stringify),
      processingMode = None,
      engineSetupName = None,
      isFragment = false,
      forwardedUserName = None
    )
    Post(s"/api/processes", command.toJsonRequestEntity()) ~>
      withWriterUser() ~>
      impersonateAllPermUser() ~>
      applicationRoute ~>
      check {
        status shouldEqual StatusCodes.Forbidden
        responseAs[String] shouldEqual ImpersonationMissingPermissionError.errorMessage
      }
  }

  test("not authorize impersonated user with read permissions to create scenario") {
    val command = CreateScenarioCommand(
      processName,
      Some(Category1.stringify),
      processingMode = None,
      engineSetupName = None,
      isFragment = false,
      forwardedUserName = None
    )
    Post(s"/api/processes", command.toJsonRequestEntity()) ~>
      withAllPermUser() ~>
      impersonateReaderUser() ~>
      applicationRoute ~>
      check {
        status shouldEqual StatusCodes.Unauthorized
        responseAs[String] shouldEqual "User doesn't have access to the given category"
      }
  }

  test("archive process") {
    createEmptyScenario(processName, category = Category1)

    archiveProcess(processName) { status =>
      status shouldEqual StatusCodes.OK

      forScenarioReturned(processName) { process =>
        process.lastActionType shouldBe Some(ScenarioActionName.Archive.toString)
        process.state.map(_.name) shouldBe Some(SimpleStateStatus.NotDeployed.name)
        process.isArchived shouldBe true
      }
    }
  }

  test("unarchive process") {
    createArchivedExampleScenario(processName, category = Category1)

    unArchiveProcess(processName) { status =>
      status shouldEqual StatusCodes.OK

      forScenarioReturned(processName) { process =>
        process.lastActionType shouldBe Some(ScenarioActionName.UnArchive.toString)
        process.state.map(_.name) shouldBe Some(SimpleStateStatus.NotDeployed.name)
        process.isArchived shouldBe false
      }
    }
  }

  test("not allow to archive already archived process") {
    createArchivedExampleScenario(processName, category = Category1)

    archiveProcess(processName) { status =>
      status shouldEqual StatusCodes.Conflict
    }
  }

  test("not allow to unarchive not archived process") {
    createEmptyScenario(processName, category = Category1)

    unArchiveProcess(processName) { status =>
      status shouldEqual StatusCodes.Conflict
    }
  }

  test("allow to delete process") {
    val scenarioGraphToSave = ProcessTestData.sampleScenarioGraph

    createArchivedExampleScenario(processName, category = Category1)

    deleteProcess(processName) { status =>
      status shouldEqual StatusCodes.OK

      tryForScenarioReturned(processName) { (status, _) =>
        status shouldEqual StatusCodes.NotFound
      }
    }

    saveProcess(ProcessName("p1"), scenarioGraphToSave, category = Category1) {
      status shouldEqual StatusCodes.OK
    }
  }

  test("not allow to delete not archived process") {
    createEmptyScenario(processName, category = Category1)

    deleteProcess(processName) { status =>
      status shouldEqual StatusCodes.Conflict
    }
  }

  test("allow to delete fragment") {
    val fragmentName = ProcessName("f1")
    createArchivedExampleFragment(fragmentName, category = Category1)

    deleteProcess(fragmentName) { status =>
      status shouldEqual StatusCodes.OK

      tryForScenarioReturned(fragmentName) { (status, _) =>
        status shouldEqual StatusCodes.NotFound
      }
    }
  }

  test("save new process with empty json") {
    val newProcessId = ProcessName("tst1")
    createProcessRequest(newProcessId, category = Category1, isFragment = false) { status =>
      status shouldEqual StatusCodes.Created
    }

    Get(s"/api/processes/$newProcessId") ~> withReaderUser() ~> applicationRoute ~> check {
      status shouldEqual StatusCodes.OK
      val loadedProcess = responseAs[ScenarioWithDetails]
      loadedProcess.processCategory shouldBe Category1.stringify
      loadedProcess.createdAt should not be null
    }
  }

  test("should provide the same proper scenario state when fetching all scenarios and one scenario") {
    createDeployedScenario(processName, category = Category1)

    Get(s"/api/processes") ~> withReaderUser() ~> applicationRoute ~> check {
      status shouldEqual StatusCodes.OK
      val loadedProcess = responseAs[List[ScenarioWithDetails]]

      loadedProcess.head.lastAction should matchPattern {
        case Some(
              ProcessAction(_, _, _, _, _, _, ScenarioActionName("DEPLOY"), ProcessActionState.Finished, _, _, _, _)
            ) =>
      }
      loadedProcess.head.lastStateAction should matchPattern {
        case Some(
              ProcessAction(_, _, _, _, _, _, ScenarioActionName("DEPLOY"), ProcessActionState.Finished, _, _, _, _)
            ) =>
      }
      loadedProcess.head.lastDeployedAction should matchPattern {
        case Some(
              ProcessAction(_, _, _, _, _, _, ScenarioActionName("DEPLOY"), ProcessActionState.Finished, _, _, _, _)
            ) =>
      }
    }

    Get(s"/api/processes/$processName") ~> withReaderUser() ~> applicationRoute ~> check {
      status shouldEqual StatusCodes.OK
      val loadedProcess = responseAs[ScenarioWithDetails]

      loadedProcess.lastAction should matchPattern {
        case Some(
              ProcessAction(_, _, _, _, _, _, ScenarioActionName("DEPLOY"), ProcessActionState.Finished, _, _, _, _)
            ) =>
      }
      loadedProcess.lastStateAction should matchPattern {
        case Some(
              ProcessAction(_, _, _, _, _, _, ScenarioActionName("DEPLOY"), ProcessActionState.Finished, _, _, _, _)
            ) =>
      }
      loadedProcess.lastDeployedAction should matchPattern {
        case Some(
              ProcessAction(_, _, _, _, _, _, ScenarioActionName("DEPLOY"), ProcessActionState.Finished, _, _, _, _)
            ) =>
      }
    }
  }

  test("not allow to save process if already exists") {
    val processName = ProcessName("p1")
    saveProcess(processName, ProcessTestData.sampleScenarioGraph, category = Category1) {
      status shouldEqual StatusCodes.OK
      createProcessRequest(processName, category = Category1, isFragment = false) { status =>
        status shouldEqual StatusCodes.BadRequest
      }
    }
  }

  test(
    "adding scenario with not configured scenario parameters combination should result with bad request response status code"
  ) {
    val processName = ProcessName("p1")
    val command = CreateScenarioCommand(
      processName,
      Some("not existing category"),
      processingMode = None,
      engineSetupName = None,
      isFragment = false,
      forwardedUserName = None
    )
    Post("/api/processes", command.toJsonRequestEntity()) ~> withAllPermUserOrAdmin(isAdmin =
      true
    ) ~> applicationRoute ~> check {
      status shouldEqual StatusCodes.BadRequest
    }
  }

  test(
    "adding scenario with ambiguous scenario parameters combination should result with bad request response status code"
  ) {
    val processName                                    = ProcessName("p1")
    val processingModeUsedInMoreThanOneProcessingTypes = ProcessingMode.UnboundedStream
    val command = CreateScenarioCommand(
      processName,
      None,
      processingMode = Some(processingModeUsedInMoreThanOneProcessingTypes),
      engineSetupName = None,
      isFragment = false,
      forwardedUserName = None
    )
    Post("/api/processes", command.toJsonRequestEntity()) ~> withAllPermUserOrAdmin(isAdmin =
      true
    ) ~> applicationRoute ~> check {
      status shouldEqual StatusCodes.BadRequest
    }
  }

  test("return all non-validated processes with details") {
    val firstProcessName  = ProcessName("firstProcessName")
    val secondProcessName = ProcessName("secondProcessName")

    saveCanonicalProcess(ProcessTestData.validProcessWithName(firstProcessName), category = Category1) {
      saveCanonicalProcess(ProcessTestData.validProcessWithName(secondProcessName), category = Category1) {
        Get("/api/processesDetails?skipValidateAndResolve=true") ~> withAllPermUser() ~> applicationRoute ~> check {
          status shouldEqual StatusCodes.OK
          val processes = responseAs[List[ScenarioWithDetails]]
          processes should have size 2
          processes.map(_.name) should contain only (firstProcessName, secondProcessName)
          every(processes.map(_.validationResult)) shouldBe empty
        }
      }
    }
  }

  test("should return statuses only for not archived scenarios (excluding fragments)") {
    createDeployedExampleScenario(processName, category = Category1)
    createArchivedExampleScenario(archivedProcessName, category = Category1)
    createEmptyFragment(ProcessName("fragment"), category = Category1)

    Get(s"/api/processes/status") ~> withAllPermUser() ~> applicationRoute ~> check {
      status shouldEqual StatusCodes.OK
      val response = responseAs[Map[String, Json]]
      response.toList.size shouldBe 1
      response.keys.head shouldBe processName.value
    }
  }

  test("should return status for single deployed process") {
    createDeployedExampleScenario(processName, category = Category1)
    MockableDeploymentManager.configureScenarioStatuses(
      Map(processName.value -> SimpleStateStatus.Running)
    )

    forScenarioStatus(processName) { (code, state) =>
      code shouldBe StatusCodes.OK
      state.name shouldBe SimpleStateStatus.Running.name
    }
  }

  test("should return status for single archived process") {
    createArchivedExampleScenario(processName, category = Category1)

    forScenarioStatus(processName) { (code, state) =>
      code shouldBe StatusCodes.OK
      state.name shouldBe SimpleStateStatus.NotDeployed.name
    }
  }

  test("should return 404 for not exists process status") {
    tryForScenarioStatus(ProcessName("non-exists-process")) { (code, _) =>
      code shouldEqual StatusCodes.NotFound
    }
  }

  test("should return 400 for single fragment status") {
    createEmptyFragment(processName, category = Category1)

    tryForScenarioStatus(processName) { (code, message) =>
      code shouldEqual StatusCodes.BadRequest
      message shouldBe "Fragment doesn't have state."
    }
  }

  test("fetching scenario toolbar definitions") {
    val toolbarConfig = CategoriesScenarioToolbarsConfigParser.parse(designerConfig).getConfig(Category1.stringify)
    val id            = createEmptyScenario(processName, category = Category1)

    withProcessToolbars(processName) { toolbar =>
      toolbar shouldBe ScenarioToolbarSettings(
        id = s"${toolbarConfig.uuidCode}-not-archived-scenario",
        List(
          ToolbarPanel(SearchPanel, None, None, None),
          ToolbarPanel(TipsPanel, None, None, None),
          ToolbarPanel(CreatorPanel, None, None, None)
        ),
        List(),
        List(
          ToolbarPanel(
            ProcessActionsPanel,
            None,
            None,
            Some(
              List(
                ToolbarButton(ProcessSave, None, None, None, None, disabled = true),
                ToolbarButton(ProcessDeploy, None, None, None, None, disabled = false),
                ToolbarButton(
                  CustomLink,
                  Some("custom"),
                  Some(s"Custom link for $processName"),
                  None,
                  Some(s"/test/${id.value}"),
                  disabled = false
                )
              )
            )
          )
        ),
        List()
      )
    }
  }

  test("fetching toolbar definitions for not exist process should return 404 response") {
    getProcessToolbars(processName) ~> check {
      status shouldEqual StatusCodes.NotFound
    }
  }

  private def verifyProcessWithStateOnList(expectedName: ProcessName, expectedStatus: Option[StateStatus]): Unit = {
    MockableDeploymentManager.configureScenarioStatuses(
      Map(processName.value -> SimpleStateStatus.Running)
    )

    forScenariosReturned(ScenarioQuery.empty) { processes =>
      val process = processes.find(_.name == expectedName.value).value
      process.state.map(_.name) shouldBe expectedStatus.map(_.name)
    }

    forScenariosDetailsReturned(ScenarioQuery.empty) { processes =>
      val process = processes.find(_.name.value == expectedName.value).value
      process.state shouldBe None
    }
  }

  private def verifyListOfProcesses(query: ScenarioQuery, expectedNames: List[ProcessName]): Unit = {
    forScenariosReturned(query) { processes =>
      processes.map(_.name) should contain theSameElementsAs expectedNames.map(_.value)
    }
    forScenariosDetailsReturned(query) { processes =>
      processes.map(_.name) should contain theSameElementsAs expectedNames
    }
  }

  private def fetchScenario(name: ProcessName): CanonicalProcess = {
    implicit val adminUser: LoggedUser = TestFactory.adminUser()
    val detailsOptT = for {
      id      <- OptionT(futureFetchingScenarioRepository.fetchProcessId(name))
      details <- OptionT(futureFetchingScenarioRepository.fetchLatestProcessDetailsForProcessId[CanonicalProcess](id))
    } yield details
    detailsOptT.value.futureValue match {
      case Some(scenarioDetails) => scenarioDetails.json
      case None                  => sys.error(s"Scenario [${processName.value}] is missing")
    }
  }

  private def getProcessId(processName: ProcessName): ProcessId =
    futureFetchingScenarioRepository.fetchProcessId(processName).futureValue.get

  private def renameProcess(processName: ProcessName, newName: ProcessName)(callback: StatusCode => Any): Any =
    Put(s"/api/processes/$processName/rename/$newName") ~> withAllPermUser() ~> applicationRoute ~> check {
      callback(status)
    }

  protected def withProcessToolbars(processName: ProcessName, isAdmin: Boolean = false)(
      callback: ScenarioToolbarSettings => Unit
  ): Unit =
    getProcessToolbars(processName, isAdmin) ~> check {
      status shouldEqual StatusCodes.OK
      val toolbar = decode[ScenarioToolbarSettings](responseAs[String]).toOption.get
      callback(toolbar)
    }

  private def getProcessToolbars(processName: ProcessName, isAdmin: Boolean = false): RouteTestResult =
    Get(s"/api/processes/$processName/toolbars") ~> withAllPermUserOrAdmin(isAdmin) ~> applicationRoute

  private def archiveProcess(processName: ProcessName)(callback: StatusCode => Any): Any =
    Post(s"/api/archive/$processName") ~> withWriterUser() ~> applicationRoute ~> check {
      callback(status)
    }

  private def unArchiveProcess(processName: ProcessName)(callback: StatusCode => Any): Any =
    Post(s"/api/unarchive/$processName") ~> withWriterUser() ~> applicationRoute ~> check {
      callback(status)
    }

  private def deleteProcess(processName: ProcessName)(callback: StatusCode => Any): Any =
    Delete(s"/api/processes/$processName") ~> withWriterUser() ~> applicationRoute ~> check {
      callback(status)
    }

  private def forScenarioStatus(processName: ProcessName, isAdmin: Boolean = false)(
      callback: (StatusCode, StateJson) => Unit
  ): Unit =
    tryForScenarioStatus(processName, isAdmin = isAdmin) { (status, response) =>
      callback(status, StateJson(parser.decode[Json](response).toOption.value))
    }

  private def tryForScenarioStatus(processName: ProcessName, isAdmin: Boolean = false)(
      callback: (StatusCode, String) => Unit
  ): Unit =
    Get(s"/api/processes/$processName/status") ~> withAllPermUserOrAdmin(isAdmin) ~> applicationRoute ~> check {
      callback(status, responseAs[String])
    }

  private def forScenariosReturned(query: ScenarioQuery, isAdmin: Boolean = false)(
      callback: List[ProcessJson] => Assertion
  ): Assertion = {
    implicit val basicProcessesUnmarshaller: FromEntityUnmarshaller[List[ScenarioWithDetails]] =
      FailFastCirceSupport.unmarshaller(implicitly[Decoder[List[ScenarioWithDetails]]])
    val url = query.createQueryParamsUrl("/api/processes")

    Get(url) ~> withAllPermUserOrAdmin(isAdmin) ~> applicationRoute ~> check {
      status shouldEqual StatusCodes.OK
      val processes = parseResponseToListJsonProcess(responseAs[String])
      responseAs[List[ScenarioWithDetails]] // just to test if decoder succeeds
      callback(processes)
    }
  }

  private def withAllPermUserOrAdmin(isAdmin: Boolean) = {
    if (isAdmin) {
      addBasicAuth("admin", "admin")
    } else {
      withAllPermUser()
    }
  }

  private def withAllPermUser() = addBasicAuth("allpermuser", "allpermuser")

  private def withReaderUser() = addBasicAuth("reader", "reader")

  private def withWriterUser() = addBasicAuth("writer", "writer")

  private def addBasicAuth(name: String, secret: String) = addCredentials(BasicHttpCredentials(name, secret))

  private def impersonateAllPermUser() = addImpersonationHeader("allpermuser")

  private def impersonateReaderUser() = addImpersonationHeader("reader")

  private def impersonateWriterUser() = addImpersonationHeader("writer")

  private def addImpersonationHeader(userIdentity: String) =
    addHeader(RawHeader(AuthManager.impersonateHeaderName, userIdentity))

  private def parseResponseToListJsonProcess(response: String): List[ProcessJson] = {
    parser.decode[List[Json]](response).value.map(j => ProcessJson(j))
  }

  private def forScenariosDetailsReturned(query: ScenarioQuery, isAdmin: Boolean = false)(
      callback: List[ScenarioWithDetails] => Assertion
  ): Assertion = {
    val url = query.createQueryParamsUrl("/api/processesDetails")

    Get(url) ~> withAllPermUserOrAdmin(isAdmin) ~> applicationRoute ~> check {
      status shouldEqual StatusCodes.OK
      implicit val basicProcessesUnmarshaller: FromEntityUnmarshaller[List[ScenarioWithDetails]] =
        FailFastCirceSupport.unmarshaller(implicitly[Decoder[List[ScenarioWithDetails]]])
      val processes = responseAs[List[ScenarioWithDetails]]
      callback(processes)
    }
  }

  private def forScenarioReturned(processName: ProcessName, isAdmin: Boolean = false)(
      callback: ProcessJson => Unit
  ): Unit =
    tryForScenarioReturned(processName, isAdmin) { (status, response) =>
      status shouldEqual StatusCodes.OK
      val process = decodeJsonProcess(response)
      callback(process)
    }

  private def tryForScenarioReturned(processName: ProcessName, isAdmin: Boolean = false)(
      callback: (StatusCode, String) => Unit
  ): Unit =
    Get(s"/api/processes/$processName") ~> withAllPermUserOrAdmin(isAdmin) ~> applicationRoute ~> check {
      callback(status, responseAs[String])
    }

  private def decodeJsonProcess(response: String): ProcessJson =
    ProcessJson(parser.decode[Json](response).value)

  private def getProcess(processName: ProcessName): RouteTestResult =
    Get(s"/api/processes/$processName") ~> withReaderUser() ~> applicationRoute

  private def getDeprecatedActivity(processName: ProcessName): RouteTestResult =
    Get(s"/api/processes/$processName/activity") ~> withAllPermUser() ~> applicationRoute

  private def getScenarioActivities(processName: ProcessName): RouteTestResult =
    Get(s"/api/processes/$processName/activity/activities") ~> withAllPermUser() ~> applicationRoute

  private def saveCanonicalProcessAndAssertSuccess(process: CanonicalProcess, category: TestCategory): Assertion =
    saveCanonicalProcess(process, category) {
      status shouldEqual StatusCodes.OK
    }

  private def saveCanonicalProcess(process: CanonicalProcess, category: TestCategory)(
      testCode: => Assertion
  ): Assertion =
    createProcessRequest(process.name, category, isFragment = false) { _ =>
      val json = parser.decode[Json](responseAs[String]).value
      val resp = CreateProcessResponse(json)

      resp.processName shouldBe process.name

      updateCanonicalProcess(process)(testCode)
    }

  private def updateCanonicalProcessAndAssertSuccess(process: CanonicalProcess): Assertion =
    updateCanonicalProcess(process) {
      status shouldEqual StatusCodes.OK
    }

  private def updateCanonicalProcess(process: CanonicalProcess, comment: Option[String] = None)(
      testCode: => Assertion
  ): Assertion =
    doUpdateProcess(
      UpdateScenarioCommand(
        CanonicalProcessConverter.toScenarioGraph(process),
        comment,
        Some(List.empty),
        None
      ),
      process.name
    )(
      testCode
    )

  private def doUpdateProcess(command: UpdateScenarioCommand, name: ProcessName)(
      testCode: => Assertion
  ): Assertion =
    Put(s"/api/processes/$name", command.toJsonRequestEntity()) ~> withAllPermUser() ~> applicationRoute ~> check {
      testCode
    }

  private def saveProcess(scenarioName: ProcessName, scenarioGraph: ScenarioGraph, category: TestCategory)(
      testCode: => Assertion
  ): Assertion = {
    saveProcess(scenarioName, scenarioGraph, category, isFragment = false)(testCode)
  }

  private def saveFragment(scenarioName: ProcessName, scenarioGraph: ScenarioGraph, category: TestCategory)(
      testCode: => Assertion
  ): Assertion = {
    saveProcess(scenarioName, scenarioGraph, category, isFragment = true)(testCode)
  }

  private def saveProcess(
      scenarioName: ProcessName,
      scenarioGraph: ScenarioGraph,
      category: TestCategory,
      isFragment: Boolean
  )(testCode: => Assertion): Assertion = {
    createProcessRequest(scenarioName, category, isFragment) { code =>
      code shouldBe StatusCodes.Created
      updateProcess(scenarioGraph, scenarioName)(testCode)
    }
  }

  private def createProcessRequest(processName: ProcessName, category: TestCategory, isFragment: Boolean)(
      callback: StatusCode => Assertion
  ): Assertion = {
    val command = CreateScenarioCommand(
      processName,
      Some(category.stringify),
      processingMode = None,
      engineSetupName = None,
      isFragment = isFragment,
      forwardedUserName = None
    )
    Post("/api/processes", command.toJsonRequestEntity()) ~> withAllPermUser() ~> applicationRoute ~> check {
      callback(status)
    }
  }

  private def updateProcess(process: ScenarioGraph, name: ProcessName = ProcessTestData.sampleProcessName)(
      testCode: => Assertion
  ): Assertion =
    doUpdateProcess(
      UpdateScenarioCommand(process, comment = None, scenarioLabels = Some(List.empty), forwardedUserName = None),
      name
    )(
      testCode
    )

  private lazy val futureFetchingScenarioRepository: FetchingProcessRepository[Future] =
    TestFactory.newFutureFetchingScenarioRepository(testDbRef)

}

private object ProcessesQueryEnrichments {

  implicit class RichProcessesQuery(query: ScenarioQuery) {

    def process(): ScenarioQuery =
      query.copy(isFragment = Some(false))

    def fragment(): ScenarioQuery =
      query.copy(isFragment = Some(true))

    def unarchived(): ScenarioQuery =
      query.copy(isArchived = Some(false))

    def archived(): ScenarioQuery =
      query.copy(isArchived = Some(true))

    def deployed(): ScenarioQuery =
      query.copy(isDeployed = Some(true))

    def notDeployed(): ScenarioQuery =
      query.copy(isDeployed = Some(false))

    def withNames(names: List[String]): ScenarioQuery =
      query.copy(names = Some(names.map(ProcessName(_))))

    def withCategories(categories: List[TestCategory]): ScenarioQuery =
      withRawCategories(categories.map(_.stringify))

    def withRawCategories(categories: List[String]): ScenarioQuery =
      query.copy(categories = Some(categories))

    def withProcessingTypes(processingTypes: List[TestProcessingType]): ScenarioQuery =
      withRawProcessingTypes(processingTypes.map(_.stringify))

    def withRawProcessingTypes(processingTypes: List[String]): ScenarioQuery =
      query.copy(processingTypes = Some(processingTypes))

    def createQueryParamsUrl(path: String): String = {
      var url = s"$path?fake=true"

      query.isArchived.foreach { isArchived =>
        url += s"&isArchived=$isArchived"
      }

      query.isFragment.foreach { isFragment =>
        url += s"&isFragment=$isFragment"
      }

      query.isDeployed.foreach { isDeployed =>
        url += s"&isDeployed=$isDeployed"
      }

      query.categories.foreach { categories =>
        url += s"&categories=${categories.mkString(",")}"
      }

      query.processingTypes.foreach { processingTypes =>
        url += s"&processingTypes=${processingTypes.mkString(",")}"
      }

      query.names.foreach { names =>
        url += s"&names=${names.mkString(",")}"
      }

      url
    }

  }

}
