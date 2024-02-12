package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.{ContentTypeRange, StatusCode, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import cats.instances.all._
import cats.syntax.semigroup._
import com.github.dockerjava.zerodep.shaded.org.apache.hc.core5.http.HttpStatus
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.scalatest.LoneElement._
import org.scalatest._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.ProcessAdditionalFields
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessStateDefinitionManager, SimpleStateStatus}
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioWithDetails
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationResult
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.helpers.TestFactory._
import pl.touk.nussknacker.ui.api.helpers.TestProcessingTypes.{Fraud, Streaming}
import pl.touk.nussknacker.ui.api.helpers._
import pl.touk.nussknacker.ui.api.helpers.spel._
import pl.touk.nussknacker.ui.config.scenariotoolbar.CategoriesScenarioToolbarsConfigParser
import pl.touk.nussknacker.ui.config.scenariotoolbar.ToolbarButtonConfigType.{CustomLink, ProcessDeploy, ProcessSave}
import pl.touk.nussknacker.ui.config.scenariotoolbar.ToolbarPanelTypeConfig.{CreatorPanel, ProcessInfoPanel, TipsPanel}
import pl.touk.nussknacker.ui.process.ProcessService.CreateScenarioCommand
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter
import pl.touk.nussknacker.ui.process.repository.DbProcessActivityRepository.ProcessActivity
import pl.touk.nussknacker.ui.process.{ScenarioQuery, ScenarioToolbarSettings, ToolbarButton, ToolbarPanel}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.Future
import scala.language.higherKinds

/**
 * TODO: On resource tests we should verify permissions and encoded response data. All business logic should be tested at ProcessServiceDb.
 */
class ProcessesResourcesSpec
    extends AnyFunSuite
    with ScalatestRouteTest
    with Matchers
    with Inside
    with FailFastCirceSupport
    with PatientScalaFutures
    with OptionValues
    with BeforeAndAfterEach
    with BeforeAndAfterAll
    with NuResourcesTest {

  import ProcessesQueryEnrichments.RichProcessesQuery
  import TestCategories._
  import io.circe._
  import io.circe.parser._
  import io.circe.generic.auto._

  private implicit final val string: FromEntityUnmarshaller[String] =
    Unmarshaller.stringUnmarshaller.forContentTypes(ContentTypeRange.*)

  private implicit val loggedUser: LoggedUser = LoggedUser("1", "lu", testCategory)

  private val routeWithRead: Route = withPermissions(processesRoute, testPermissionRead)

  private val routeWithWrite: Route = withPermissions(processesRoute, testPermissionWrite)

  private val routeWithAllPermissions: Route = withAllPermissions(processesRoute)

  private val processName: ProcessName = ProcessTestData.sampleProcessName

  private val archivedProcessName = ProcessName("archived")

  private val fragmentName = ProcessName("fragment")

  private val archivedFragmentName = ProcessName("archived-fragment")

  test("should return list of process with state") {
    createDeployedExampleScenario(processName)
    verifyProcessWithStateOnList(processName, Some(SimpleStateStatus.Running))
  }

  test("should return list of fragment with no state") {
    createEmptyProcess(processName, isFragment = true)
    verifyProcessWithStateOnList(processName, None)
  }

  test("should return list of archived process with no state") {
    createArchivedProcess(processName)
    verifyProcessWithStateOnList(processName, Some(SimpleStateStatus.NotDeployed))
  }

  test("/processes and /processesDetails should return lighter details without history versions") {
    saveCanonicalProcess(ProcessTestData.validProcess) {
      forScenariosReturned(ScenarioQuery.empty) { processes =>
        every(processes.map(_.history)) shouldBe empty
      }
      forScenariosDetailsReturned(ScenarioQuery.empty) { processes =>
        every(processes.map(_.history)) shouldBe empty
      }
    }
  }

  test("return single process") {
    val processId = createDeployedExampleScenario(processName)

    deploymentManager.withProcessRunning(processName) {
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
  }

  test("spel template expression is validated properly") {
    createDeployedScenario(SampleSpelTemplateProcess.process)

    Get(s"/processes/${SampleSpelTemplateProcess.processName}") ~> routeWithRead ~> check {
      val newProcessDetails = responseAs[ScenarioWithDetails]
      newProcessDetails.processVersionId shouldBe VersionId.initialVersionId

      responseAs[String] should include("validationResult")
      responseAs[String] should not include "ExpressionParserCompilationError"
    }
  }

  test("return validated and non-validated process") {
    createEmptyProcess(processName)

    Get(s"/processes/$processName") ~> routeWithRead ~> check {
      status shouldEqual StatusCodes.OK
      val validated = responseAs[ScenarioWithDetails]
      validated.name shouldBe processName
      validated.validationResult.value.errors should not be empty
    }

    Get(s"/processes/$processName?skipValidateAndResolve=true") ~> routeWithRead ~> check {
      status shouldEqual StatusCodes.OK
      val validated = responseAs[ScenarioWithDetails]
      validated.name shouldBe processName
      validated.validationResult shouldBe empty
    }
  }

  // FIXME: Implement fragment validation
  ignore("not allow to archive still used fragment") {
    val processWithFragment = ProcessTestData.validProcessWithFragment(processName)
    val scenarioGraph =
      CanonicalProcessConverter.toScenarioGraph(processWithFragment.fragment)
    saveFragment(scenarioGraph)(succeed)
    saveCanonicalProcess(processWithFragment.process)(succeed)

    archiveProcess(processName) { status =>
      status shouldEqual StatusCodes.Conflict
    }
  }

  test("not allow to archive still running process") {
    createDeployedExampleScenario(processName)

    deploymentManager.withProcessRunning(processName) {
      archiveProcess(processName) { status =>
        status shouldEqual StatusCodes.Conflict
      }
    }
  }

  test("allow to archive fragment used in archived process") {
    val processWithFragment = ProcessTestData.validProcessWithFragment(processName)
    val fragmentGraph =
      CanonicalProcessConverter.toScenarioGraph(processWithFragment.fragment)
    saveFragment(fragmentGraph)(succeed)
    saveCanonicalProcess(processWithFragment.process)(succeed)

    archiveProcess(processName) { status =>
      status shouldEqual StatusCodes.OK
    }

    archiveProcess(processWithFragment.fragment.name) { status =>
      status shouldEqual StatusCodes.OK
    }
  }

  test("or not allow to create new scenario named as archived one") {
    val process = ProcessTestData.validProcess
    saveCanonicalProcess(process)(succeed)

    archiveProcess(processName) { status =>
      status shouldEqual StatusCodes.OK
    }

    createProcessRequest(processName) { status =>
      status shouldBe StatusCodes.BadRequest
      responseAs[String] shouldEqual s"Scenario $processName already exists"
    }
  }

  test("should allow to rename not deployed process") {
    val processId = createEmptyProcess(processName)
    val newName   = ProcessName("ProcessChangedName")

    renameProcess(processName, newName) { status =>
      status shouldEqual StatusCodes.OK
      getProcessId(newName) shouldBe processId
    }
  }

  test("should allow to rename canceled process") {
    val processId = createDeployedCanceledExampleScenario(processName)
    val newName   = ProcessName("ProcessChangedName")

    renameProcess(processName, newName) { status =>
      status shouldEqual StatusCodes.OK
      getProcessId(newName) shouldBe processId
    }
  }

  test("should not allow to rename deployed process") {
    createDeployedExampleScenario(processName)
    deploymentManager.withProcessRunning(processName) {
      val newName = ProcessName("ProcessChangedName")

      renameProcess(processName, newName) { status =>
        status shouldEqual StatusCodes.Conflict
      }
    }
  }

  test("should not allow to rename archived process") {
    createArchivedProcess(processName)
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
    createEmptyProcess(processName)
    val newName = ProcessName("ProcessChangedName")

    deploymentManager.withProcessRunning(processName) {
      renameProcess(processName, newName) { status =>
        status shouldEqual StatusCodes.Conflict
      }
    }
  }

  test("should allow to rename fragment") {
    val processId = createEmptyProcess(processName, isFragment = true)
    val newName   = ProcessName("ProcessChangedName")

    renameProcess(processName, newName) { status =>
      status shouldEqual StatusCodes.OK
      getProcessId(newName) shouldBe processId
    }
  }

  test("not allow to save archived process") {
    createArchivedProcess(processName)
    val process = ProcessTestData.validProcess

    updateCanonicalProcess(process) {
      status shouldEqual StatusCodes.Conflict
    }
  }

  test("should return list of all processes and fragments") {
    createEmptyProcess(processName)
    createEmptyProcess(fragmentName, isFragment = true)
    createArchivedProcess(archivedProcessName)
    createArchivedProcess(archivedFragmentName, isFragment = true)

    verifyListOfProcesses(
      ScenarioQuery.empty,
      List(processName, fragmentName, archivedProcessName, archivedFragmentName)
    )
    verifyListOfProcesses(ScenarioQuery.empty.unarchived(), List(processName, fragmentName))
    verifyListOfProcesses(ScenarioQuery.empty.archived(), List(archivedProcessName, archivedFragmentName))
  }

  test("return list of all fragments") {
    createEmptyProcess(processName)
    createEmptyProcess(fragmentName, isFragment = true)
    createArchivedProcess(archivedProcessName)
    createArchivedProcess(archivedFragmentName, isFragment = true)

    verifyListOfProcesses(ScenarioQuery.empty.fragment(), List(fragmentName, archivedFragmentName))
    verifyListOfProcesses(ScenarioQuery.empty.fragment().unarchived(), List(fragmentName))
    verifyListOfProcesses(ScenarioQuery.empty.fragment().archived(), List(archivedFragmentName))
  }

  test("should return list of processes") {
    createEmptyProcess(processName)
    createEmptyProcess(fragmentName, isFragment = true)
    createArchivedProcess(archivedProcessName)
    createArchivedProcess(archivedFragmentName, isFragment = true)

    verifyListOfProcesses(ScenarioQuery.empty.process(), List(processName, archivedProcessName))
    verifyListOfProcesses(ScenarioQuery.empty.process().unarchived(), List(processName))
    verifyListOfProcesses(ScenarioQuery.empty.process().archived(), List(archivedProcessName))
  }

  test("return process if user has category") {
    val processId = createEmptyProcess(processName)

    forScenarioReturned(processName) { process =>
      process.processCategory shouldBe Category1
    }
  }

  test("not return processes not in user categories") {
    createEmptyProcess(processName, category = Category2)

    tryForScenarioReturned(processName) { (status, _) =>
      status shouldEqual StatusCodes.NotFound
    }

    forScenariosReturned(ScenarioQuery.empty) { processes =>
      processes.isEmpty shouldBe true
    }
    forScenariosDetailsReturned(ScenarioQuery.empty) { processes =>
      processes.isEmpty shouldBe true
    }
    forScenariosDetailsReturned(ScenarioQuery.empty.copy(categories = Some(List(Category1)))) { processes =>
      processes.isEmpty shouldBe true
    }
  }

  test("return all processes for admin user") {
    createEmptyProcess(processName)

    forScenarioReturned(processName, isAdmin = true) { _ => }

    forScenariosReturned(ScenarioQuery.empty, isAdmin = true) { processes =>
      processes.exists(_.name == processName.value) shouldBe true
    }
    forScenariosDetailsReturned(ScenarioQuery.empty, isAdmin = true) { processes =>
      processes.exists(_.name == processName) shouldBe true
    }
  }

  test("search processes by categories") {
    createEmptyProcess(ProcessName("proc1"), category = Category1)
    createEmptyProcess(ProcessName("proc2"), category = Category2)

    forScenariosReturned(ScenarioQuery.empty, isAdmin = true) { processes =>
      processes.size shouldBe 2
    }
    forScenariosDetailsReturned(ScenarioQuery.empty, isAdmin = true) { processes =>
      processes.size shouldBe 2
    }

    forScenariosReturned(ScenarioQuery.empty.categories(List(Category1)), isAdmin = true) { processes =>
      processes.loneElement.name shouldBe "proc1"
    }
    forScenariosDetailsReturned(ScenarioQuery.empty.categories(List(Category1)), isAdmin = true) { processes =>
      processes.loneElement.name.value shouldBe "proc1"
    }

    forScenariosReturned(ScenarioQuery.empty.categories(List(Category2)), isAdmin = true) { processes =>
      processes.loneElement.name shouldBe "proc2"
    }
    forScenariosDetailsReturned(ScenarioQuery.empty.categories(List(Category2)), isAdmin = true) { processes =>
      processes.loneElement.name.value shouldBe "proc2"
    }

    forScenariosReturned(ScenarioQuery.empty.categories(List(Category1, Category2)), isAdmin = true) { processes =>
      processes.size shouldBe 2
    }
    forScenariosDetailsReturned(ScenarioQuery.empty.categories(List(Category1, Category2)), isAdmin = true) {
      processes =>
        processes.size shouldBe 2
    }
  }

  test("search processes by processing types") {
    createEmptyProcess(processName)

    forScenariosReturned(ScenarioQuery.empty.processingTypes(List(Streaming))) { processes =>
      processes.size shouldBe 1
    }
    forScenariosDetailsReturned(ScenarioQuery.empty.processingTypes(List(Streaming))) { processes =>
      processes.size shouldBe 1
    }
    forScenariosReturned(ScenarioQuery.empty.processingTypes(List(Fraud))) { processes =>
      processes.size shouldBe 0
    }
    forScenariosDetailsReturned(ScenarioQuery.empty.processingTypes(List(Fraud))) { processes =>
      processes.size shouldBe 0
    }
  }

  test("search processes by names") {
    createEmptyProcess(ProcessName("proc1"))
    createEmptyProcess(ProcessName("proc2"))

    forScenariosReturned(ScenarioQuery.empty.names(List("proc1"))) { processes =>
      processes.loneElement.name shouldBe "proc1"
    }
    forScenariosDetailsReturned(ScenarioQuery.empty.names(List("proc1"))) { processes =>
      processes.loneElement.name.value shouldBe "proc1"
    }
    forScenariosReturned(ScenarioQuery.empty.names(List("proc3"))) { processes =>
      processes.size shouldBe 0
    }
    forScenariosDetailsReturned(ScenarioQuery.empty.names(List("proc3"))) { processes =>
      processes.size shouldBe 0
    }
  }

  test("search processes with multiple parameters") {
    createEmptyProcess(ProcessName("proc1"), category = Category1)
    createEmptyProcess(ProcessName("proc2"), category = Category2)
    createArchivedProcess(ProcessName("proc3"))

    forScenariosReturned(
      ScenarioQuery.empty
        .names(List("proc1", "proc3", "procNotExisting"))
        .categories(List(Category1))
        .processingTypes(List(Streaming))
        .unarchived()
    ) { processes =>
      processes.loneElement.name shouldBe "proc1"
    }
    forScenariosDetailsReturned(
      ScenarioQuery.empty
        .names(List("proc1", "proc3", "procNotExisting"))
        .categories(List(Category1))
        .processingTypes(List(Streaming))
        .unarchived()
    ) { processes =>
      processes.loneElement.name.value shouldBe "proc1"
    }
    forScenariosReturned(
      ScenarioQuery.empty
        .names(List("proc1", "proc3", "procNotExisting"))
        .categories(List(Category1))
        .processingTypes(List(Streaming))
        .archived()
    ) { processes =>
      processes.loneElement.name shouldBe "proc3"
    }
    forScenariosDetailsReturned(
      ScenarioQuery.empty
        .names(List("proc1", "proc3", "procNotExisting"))
        .categories(List(Category1))
        .processingTypes(List(Streaming))
        .archived()
    ) { processes =>
      processes.loneElement.name.value shouldBe "proc3"
    }
    forScenariosReturned(ScenarioQuery.empty.names(List("proc1")).categories(List("unknown"))) { processes =>
      processes.size shouldBe 0
    }
    forScenariosDetailsReturned(ScenarioQuery.empty.names(List("proc1")).categories(List("unknown"))) { processes =>
      processes.size shouldBe 0
    }
  }

  test("search processes by isDeployed") {
    val firstProcessor  = ProcessName("Processor1")
    val secondProcessor = ProcessName("Processor2")
    val thirdProcessor  = ProcessName("Processor3")

    createEmptyProcess(firstProcessor)
    createDeployedCanceledExampleScenario(secondProcessor)
    createDeployedExampleScenario(thirdProcessor)

    deploymentManager.withProcessStateStatus(secondProcessor, SimpleStateStatus.Canceled) {
      deploymentManager.withProcessStateStatus(thirdProcessor, SimpleStateStatus.Running) {
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
    }
  }

  test("return 404 when no process") {
    tryForScenarioReturned(ProcessName("nont-exists")) { (status, _) =>
      status shouldEqual StatusCodes.NotFound
    }
  }

  test("return sample process details") {
    createEmptyProcess(processName)

    forScenarioReturned(processName) { process =>
      process.name shouldBe processName.value
    }
  }

  test("save correct process json with ok status") {
    saveCanonicalProcess(ProcessTestData.validProcess) {
      status shouldEqual StatusCodes.OK
      checkSampleProcessRootIdEquals(ProcessTestData.validProcess.nodes.head.id)
      entityAs[ValidationResult].errors.invalidNodes.isEmpty shouldBe true
    }
  }

  test("update process with the same json should not create new version") {
    val command = ProcessTestData.createEmptyUpdateProcessCommand(None)

    createProcessRequest(processName) { code =>
      code shouldBe StatusCodes.Created

      doUpdateProcess(command) {
        forScenarioReturned(processName) { process =>
          process.history.map(_.size) shouldBe Some(1)
        }
        status shouldEqual StatusCodes.OK
      }
    }
  }

  test("update process with the same json should add comment for current version") {
    val process = ProcessTestData.validProcess
    val comment = "Update the same version"

    saveCanonicalProcess(process) {
      forScenarioReturned(processName) { process =>
        process.history.map(_.size) shouldBe Some(2)
      }
      status shouldEqual StatusCodes.OK
    }

    updateCanonicalProcess(process, comment) {
      forScenarioReturned(processName) { process =>
        process.history.map(_.size) shouldBe Some(2)
      }
      status shouldEqual StatusCodes.OK
    }

    getActivity(processName) ~> check {
      val comments = responseAs[ProcessActivity].comments
      comments.loneElement.content shouldBe comment
    }
  }

  test("return details of process with empty expression") {
    saveCanonicalProcess(ProcessTestData.validProcessWithEmptySpelExpr) {
      Get(s"/processes/$processName") ~> routeWithAllPermissions ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] should include(processName.value)
      }
    }
  }

  test("save invalid process json with ok status but with non empty invalid nodes") {
    saveCanonicalProcess(ProcessTestData.invalidProcess) {
      status shouldEqual StatusCodes.OK
      checkSampleProcessRootIdEquals(ProcessTestData.invalidProcess.nodes.head.id)
      entityAs[ValidationResult].errors.invalidNodes.isEmpty shouldBe false
    }
  }

  test("return one latest version for process") {
    saveCanonicalProcess(ProcessTestData.validProcess) {
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
    saveCanonicalProcess(ProcessTestData.validProcess) {
      status shouldEqual StatusCodes.OK
    }

    val meta = ProcessTestData.validProcess.metaData
    val changedMeta = meta.copy(additionalFields =
      ProcessAdditionalFields(Some("changed descritption..."), Map.empty, meta.additionalFields.metaDataType)
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
    saveCanonicalProcess(ProcessTestData.validProcess) {
      status shouldEqual StatusCodes.OK
    }

    updateCanonicalProcess(ProcessTestData.invalidProcess) {
      status shouldEqual StatusCodes.OK
    }

    Get(s"/processes/${ProcessTestData.sampleScenario.name}/1") ~> routeWithAllPermissions ~> check {
      val processDetails = responseAs[ScenarioWithDetails]
      processDetails.processVersionId shouldBe VersionId.initialVersionId
      processDetails.isLatestVersion shouldBe false
    }

    Get(s"/processes/${ProcessTestData.sampleScenario.name}/2") ~> routeWithAllPermissions ~> check {
      val processDetails = responseAs[ScenarioWithDetails]
      processDetails.processVersionId shouldBe VersionId(2)
      processDetails.isLatestVersion shouldBe false
    }

    Get(s"/processes/${ProcessTestData.sampleScenario.name}/3") ~> routeWithAllPermissions ~> check {
      val processDetails = responseAs[ScenarioWithDetails]
      processDetails.processVersionId shouldBe VersionId(3)
      processDetails.isLatestVersion shouldBe true
    }
  }

  test("return non-validated process version") {
    createEmptyProcess(processName)

    Get(s"/processes/$processName/1?skipValidateAndResolve=true") ~> routeWithAllPermissions ~> check {
      val processDetails = responseAs[ScenarioWithDetails]
      processDetails.processVersionId shouldBe VersionId.initialVersionId
      processDetails.validationResult shouldBe empty
    }
  }

  test("perform idempotent process save") {
    saveCanonicalProcessAndAssertSuccess(ProcessTestData.validProcess)
    Get(s"/processes/${ProcessTestData.sampleScenario.name}") ~> routeWithAllPermissions ~> check {
      val processHistoryBeforeDuplicatedWrite = responseAs[ScenarioWithDetails].history.value
      updateCanonicalProcessAndAssertSuccess(ProcessTestData.validProcess)
      Get(s"/processes/${ProcessTestData.sampleScenario.name}") ~> routeWithAllPermissions ~> check {
        val processHistoryAfterDuplicatedWrite = responseAs[ScenarioWithDetails].history.value
        processHistoryAfterDuplicatedWrite shouldBe processHistoryBeforeDuplicatedWrite
      }
    }
  }

  test("not authorize user with read permissions to create scenario") {
    val command = CreateScenarioCommand(
      processName,
      Some(Category1),
      processingMode = None,
      engineSetupName = None,
      isFragment = false,
      forwardedUserName = None
    )
    Post(s"/processes", posting.toRequestEntity(command)) ~> routeWithRead ~> check {
      status shouldEqual StatusCodes.Unauthorized
    }
  }

  test("archive process") {
    createEmptyProcess(processName)

    archiveProcess(processName) { status =>
      status shouldEqual StatusCodes.OK

      forScenarioReturned(processName) { process =>
        process.lastActionType shouldBe Some(ProcessActionType.Archive.toString)
        process.state.map(_.name) shouldBe Some(SimpleStateStatus.NotDeployed.name)
        process.isArchived shouldBe true
      }
    }
  }

  test("unarchive process") {
    createArchivedProcess(processName)

    unArchiveProcess(processName) { status =>
      status shouldEqual StatusCodes.OK

      forScenarioReturned(processName) { process =>
        process.lastActionType shouldBe Some(ProcessActionType.UnArchive.toString)
        process.state.map(_.name) shouldBe Some(SimpleStateStatus.NotDeployed.name)
        process.isArchived shouldBe false
      }
    }
  }

  test("not allow to archive already archived process") {
    createArchivedProcess(processName)

    archiveProcess(processName) { status =>
      status shouldEqual StatusCodes.Conflict
    }
  }

  test("not allow to unarchive not archived process") {
    createEmptyProcess(processName)

    unArchiveProcess(processName) { status =>
      status shouldEqual StatusCodes.Conflict
    }
  }

  test("allow to delete process") {
    val scenarioGraphToSave = ProcessTestData.sampleScenarioGraph

    createArchivedProcess(processName)

    deleteProcess(processName) { status =>
      status shouldEqual StatusCodes.OK

      tryForScenarioReturned(processName) { (status, _) =>
        status shouldEqual StatusCodes.NotFound
      }
    }

    saveProcess(scenarioGraphToSave) {
      status shouldEqual StatusCodes.OK
    }
  }

  test("not allow to delete not archived process") {
    createEmptyProcess(processName)

    deleteProcess(processName) { status =>
      status shouldEqual StatusCodes.Conflict
    }
  }

  test("allow to delete fragment") {
    createArchivedProcess(processName, isFragment = true)

    deleteProcess(processName) { status =>
      status shouldEqual StatusCodes.OK

      tryForScenarioReturned(processName) { (status, _) =>
        status shouldEqual StatusCodes.NotFound
      }
    }
  }

  test("save new process with empty json") {
    val newProcessId = ProcessName("tst1")
    createProcessRequest(newProcessId) { status =>
      status shouldEqual StatusCodes.Created
    }
    Get(s"/processes/$newProcessId") ~> routeWithRead ~> check {
      status shouldEqual StatusCodes.OK
      val loadedProcess = responseAs[ScenarioWithDetails]
      loadedProcess.processCategory shouldBe Category1
      loadedProcess.createdAt should not be null
    }
  }

  test("not allow to save process if already exists") {
    val scenarioGraphToSave = ProcessTestData.sampleScenarioGraph
    saveProcess(scenarioGraphToSave) {
      status shouldEqual StatusCodes.OK
      createProcessRequest(processName) { status =>
        status shouldEqual StatusCodes.BadRequest
      }
    }
  }

  test("return all non-validated processes with details") {
    val firstProcessName  = ProcessName("firstProcessName")
    val secondProcessName = ProcessName("secondProcessName")

    saveCanonicalProcess(ProcessTestData.validProcessWithName(firstProcessName)) {
      saveCanonicalProcess(ProcessTestData.validProcessWithName(secondProcessName)) {
        Get("/processesDetails?skipValidateAndResolve=true") ~> routeWithAllPermissions ~> check {
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
    createDeployedExampleScenario(processName)
    createArchivedProcess(archivedProcessName)
    createEmptyProcess(ProcessName("fragment"), isFragment = true)

    Get(s"/processes/status") ~> routeWithAllPermissions ~> check {
      status shouldEqual StatusCodes.OK
      val response = responseAs[Map[String, Json]]
      response.toList.size shouldBe 1
      response.keys.head shouldBe processName.value
    }
  }

  test("should return status for single deployed process") {
    createDeployedExampleScenario(processName)

    deploymentManager.withProcessRunning(processName) {
      forScenarioStatus(processName) { (code, state) =>
        code shouldBe StatusCodes.OK
        state.name shouldBe SimpleStateStatus.Running.name
      }
    }
  }

  test("should return status for single archived process") {
    createArchivedProcess(processName)

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
    createEmptyProcess(processName, isFragment = true)

    tryForScenarioStatus(processName) { (code, message) =>
      code shouldEqual StatusCodes.BadRequest
      message shouldBe "Fragment doesn't have state."
    }
  }

  test("fetching scenario toolbar definitions") {
    val toolbarConfig = CategoriesScenarioToolbarsConfigParser.parse(testConfig).getConfig(Category1)
    val id            = createEmptyProcess(processName)

    withProcessToolbars(processName) { toolbar =>
      toolbar shouldBe ScenarioToolbarSettings(
        id = s"${toolbarConfig.uuidCode}-not-archived-scenario",
        List(
          ToolbarPanel(TipsPanel, None, None, None),
          ToolbarPanel(CreatorPanel, None, None, None)
        ),
        List(),
        List(
          ToolbarPanel(
            ProcessInfoPanel,
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
    deploymentManager.withProcessRunning(processName) {
      forScenariosReturned(ScenarioQuery.empty) { processes =>
        val process = processes.find(_.name == expectedName.value).value
        process.state.map(_.name) shouldBe expectedStatus.map(_.name)
      }

      forScenariosDetailsReturned(ScenarioQuery.empty) { processes =>
        val process = processes.find(_.name.value == expectedName.value).value
        process.state shouldBe None
      }
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

  private def checkSampleProcessRootIdEquals(expected: String): Assertion = {
    fetchSampleProcess()
      .map(_.nodes.head.id)
      .futureValue shouldEqual expected
  }

  private def fetchSampleProcess(): Future[CanonicalProcess] = {
    futureFetchingScenarioRepository
      .fetchLatestProcessDetailsForProcessId[CanonicalProcess](getProcessId(processName))
      .map(_.getOrElse(sys.error("Sample process missing")))
      .map(_.json)
  }

  private def getProcessId(processName: ProcessName): ProcessId =
    futureFetchingScenarioRepository.fetchProcessId(processName).futureValue.get

  private def renameProcess(processName: ProcessName, newName: ProcessName)(callback: StatusCode => Any): Any =
    Put(s"/processes/$processName/rename/$newName") ~> routeWithAllPermissions ~> check {
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
    Get(s"/processes/$processName/toolbars") ~> routeWithPermissions(processesRoute, isAdmin)

  private def archiveProcess(processName: ProcessName)(callback: StatusCode => Any): Any =
    Post(s"/archive/$processName") ~> withPermissions(
      processesRoute,
      testPermissionWrite |+| testPermissionRead
    ) ~> check {
      callback(status)
    }

  private def unArchiveProcess(processName: ProcessName)(callback: StatusCode => Any): Any =
    Post(s"/unarchive/$processName") ~> withPermissions(
      processesRoute,
      testPermissionWrite |+| testPermissionRead
    ) ~> check {
      callback(status)
    }

  private def deleteProcess(processName: ProcessName)(callback: StatusCode => Any): Any =
    Delete(s"/processes/$processName") ~> withPermissions(
      processesRoute,
      testPermissionWrite |+| testPermissionRead
    ) ~> check {
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
    Get(s"/processes/$processName/status") ~> routeWithPermissions(processesRoute, isAdmin) ~> check {
      callback(status, responseAs[String])
    }

}
