package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.{ContentTypeRange, StatusCode, StatusCodes}
import akka.http.scaladsl.server
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshal, Unmarshaller}
import cats.instances.all._
import cats.syntax.semigroup._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.scalatest.{Assertion, BeforeAndAfterAll, BeforeAndAfterEach, Inside, OptionValues}
import org.scalatest.LoneElement._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.{ProcessAdditionalFields, StreamMetaData}
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessStateDefinitionManager, SimpleStateStatus}
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.restmodel.displayedgraph.ProcessProperties
import pl.touk.nussknacker.restmodel.processdetails.{ProcessDetails, ValidatedProcessDetails}
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationResult
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.EspError.XError
import pl.touk.nussknacker.ui.api.ProcessesResources.ProcessesQuery
import pl.touk.nussknacker.ui.api.helpers.TestFactory._
import pl.touk.nussknacker.ui.api.helpers.TestProcessingTypes.{Fraud, Streaming}
import pl.touk.nussknacker.ui.api.helpers._
import pl.touk.nussknacker.ui.api.helpers.spel._
import pl.touk.nussknacker.ui.config.processtoolbar.ProcessToolbarsConfigProvider
import pl.touk.nussknacker.ui.config.processtoolbar.ToolbarButtonConfigType.{CustomLink, ProcessDeploy, ProcessSave}
import pl.touk.nussknacker.ui.config.processtoolbar.ToolbarPanelTypeConfig.{CreatorPanel, ProcessInfoPanel, TipsPanel}
import pl.touk.nussknacker.ui.process.{ProcessToolbarSettings, ToolbarButton, ToolbarPanel}
import pl.touk.nussknacker.ui.process.repository.DbProcessActivityRepository.ProcessActivity
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter

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

  import io.circe._, io.circe.parser._
  import TestCategories._
  import ProcessesQueryEnrichments.RichProcessesQuery

  private implicit final val string: FromEntityUnmarshaller[String] =
    Unmarshaller.stringUnmarshaller.forContentTypes(ContentTypeRange.*)

  private implicit val loggedUser: LoggedUser = LoggedUser("1", "lu", testCategory)

  private val routeWithRead: Route = withPermissions(processesRoute, testPermissionRead)

  private val routeWithWrite: Route = withPermissions(processesRoute, testPermissionWrite)

  private val routeWithAllPermissions: Route = withAllPermissions(processesRoute)

  private val processName: ProcessName = SampleProcess.processName

  private val archivedProcessName = ProcessName("archived")

  private val fragmentName = ProcessName("fragment")

  private val archivedFragmentName = ProcessName("archived-fragment")

  test("should return list of process with state") {
    createDeployedProcess(processName)
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

  test("return single process") {
    val processId = createDeployedProcess(processName)

    deploymentManager.withProcessRunning(processName) {
      forScenarioReturned(processName) { process =>
        process.processId shouldBe processId.value
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
    createDeployedProcessFromProcess(SampleSpelTemplateProcess.process)

    Get(s"/processes/${SampleSpelTemplateProcess.processName.value}") ~> routeWithRead ~> check {
      val newProcessDetails = responseAs[ValidatedProcessDetails]
      newProcessDetails.processVersionId shouldBe VersionId.initialVersionId

      responseAs[String] should include("validationResult")
      responseAs[String] should not include "ExpressionParserCompilationError"
    }
  }

  test("return validated and non-validated process") {
    createDeployedProcess(processName)

    Get(s"/processes/${processName.value}") ~> routeWithRead ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[ValidatedProcessDetails].name shouldBe processName.value
    }

    Get(s"/processes/${processName.value}?skipValidateAndResolve=true") ~> routeWithRead ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[ProcessDetails].name shouldBe processName.value
      responseAs[String] should not include "validationResult"
      Unmarshal(response).to[ValidatedProcessDetails].failed.futureValue shouldBe a[DecodingFailure]
    }
  }

  // FIXME: Implement fragment valiation
  ignore("not allow to archive still used fragment") {
    val processWithFragment = ProcessTestData.validProcessWithFragment(processName)
    val displayableFragment =
      ProcessConverter.toDisplayable(processWithFragment.fragment, TestProcessingTypes.Streaming, TestCat)
    savefragment(displayableFragment)(succeed)
    saveProcess(processName, processWithFragment.process, TestCat)(succeed)

    archiveProcess(ProcessName(displayableFragment.id)) { status =>
      status shouldEqual StatusCodes.Conflict
    }
  }

  test("not allow to archive still running process") {
    createDeployedProcess(processName)

    deploymentManager.withProcessRunning(processName) {
      archiveProcess(processName) { status =>
        status shouldEqual StatusCodes.Conflict
      }
    }
  }

  test("allow to archive fragment used in archived process") {
    val processWithFragment = ProcessTestData.validProcessWithFragment(processName)
    val displayableFragment =
      ProcessConverter.toDisplayable(processWithFragment.fragment, TestProcessingTypes.Streaming, TestCat)
    savefragment(displayableFragment)(succeed)
    saveProcess(processName, processWithFragment.process, TestCat)(succeed)

    archiveProcess(processName) { status =>
      status shouldEqual StatusCodes.OK
    }

    archiveProcess(ProcessName(displayableFragment.id)) { status =>
      status shouldEqual StatusCodes.OK
    }
  }

  test("or not allow to create new scenario named as archived one") {
    val process = ProcessTestData.validProcess
    saveProcess(processName, process, TestCat)(succeed)

    archiveProcess(processName) { status =>
      status shouldEqual StatusCodes.OK
    }

    Post(s"/processes/${processName.value}/$TestCat?isFragment=false") ~> processesRouteWithAllPermissions ~> check {
      status shouldBe StatusCodes.BadRequest
      responseAs[String] shouldEqual s"Scenario ${processName.value} already exists"
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
    val processId = createDeployedCanceledProcess(processName)
    val newName   = ProcessName("ProcessChangedName")

    renameProcess(processName, newName) { status =>
      status shouldEqual StatusCodes.OK
      getProcessId(newName) shouldBe processId
    }
  }

  test("should not allow to rename deployed process") {
    createDeployedProcess(processName)
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

    updateProcess(processName, process) {
      status shouldEqual StatusCodes.Conflict
    }
  }

  test("should return list of all processes and fragments") {
    createEmptyProcess(processName)
    createEmptyProcess(fragmentName, isFragment = true)
    createArchivedProcess(archivedProcessName)
    createArchivedProcess(archivedFragmentName, isFragment = true)

    verifyListOfProcesses(
      ProcessesQuery.empty,
      List(processName, fragmentName, archivedProcessName, archivedFragmentName)
    )
    verifyListOfProcesses(ProcessesQuery.empty.unarchived(), List(processName, fragmentName))
    verifyListOfProcesses(ProcessesQuery.empty.archived(), List(archivedFragmentName, archivedFragmentName))
  }

  test("return list of all fragments") {
    createEmptyProcess(processName)
    createEmptyProcess(fragmentName, isFragment = true)
    createArchivedProcess(archivedProcessName)
    createArchivedProcess(archivedFragmentName, isFragment = true)

    verifyListOfProcesses(ProcessesQuery.empty.fragment(), List(fragmentName, archivedFragmentName))
    verifyListOfProcesses(ProcessesQuery.empty.fragment().unarchived(), List(fragmentName))
    verifyListOfProcesses(ProcessesQuery.empty.fragment().archived(), List(archivedFragmentName))
  }

  test("should return list of processes") {
    createEmptyProcess(processName)
    createEmptyProcess(fragmentName, isFragment = true)
    createArchivedProcess(archivedProcessName)
    createArchivedProcess(archivedFragmentName, isFragment = true)

    verifyListOfProcesses(ProcessesQuery.empty.process(), List(processName, archivedProcessName))
    verifyListOfProcesses(ProcessesQuery.empty.process().unarchived(), List(processName))
    verifyListOfProcesses(ProcessesQuery.empty.process().archived(), List(archivedProcessName))
  }

  test("allow update category for existing process") {
    val processId = createEmptyProcess(processName)

    changeProcessCategory(processName, TestCat2, isAdmin = true) { status =>
      status shouldEqual StatusCodes.OK

      val process = getProcessDetails(processId)
      process.processCategory shouldBe TestCat2
    }
  }

  test("not allow update to not existed category") {
    createEmptyProcess(processName)

    changeProcessCategory(processName, "not-exists-category", isAdmin = true) { status =>
      status shouldEqual StatusCodes.BadRequest
    }
  }

  test("not allow update category archived process") {
    createArchivedProcess(processName)

    changeProcessCategory(processName, TestCat2, isAdmin = true) { status =>
      status shouldEqual StatusCodes.Conflict
    }
  }

  test("return 404 on update process category for non existing process") {
    changeProcessCategory(ProcessName("not-exists-process"), TestCat2, isAdmin = true) { status =>
      status shouldBe StatusCodes.NotFound
    }
  }

  test("return 403 on update process category for normal user") {
    createArchivedProcess(processName)
    // Verification of rejection is done on changeProcessCategory
    changeProcessCategory(processName, TestCat2) { _ => }
  }

  test("return process if user has category") {
    val processId = createEmptyProcess(processName)
    updateCategory(processId, TestCat2)

    forScenarioReturned(processName) { process =>
      process.processCategory shouldBe TestCat2
      process.processId shouldBe processId.value
    }
  }

  test("not return processes not in user categories") {
    val processId = createEmptyProcess(processName)

    updateCategory(processId, Category1)

    tryForScenarioReturned(processName) { (status, _) =>
      status shouldEqual StatusCodes.NotFound
    }

    forScenariosReturned(ProcessesQuery.empty) { processes =>
      processes.isEmpty shouldBe true
    }
    forScenariosDetailsReturned(ProcessesQuery.empty) { processes =>
      processes.isEmpty shouldBe true
    }
  }

  test("return all processes for admin user") {
    val category  = "Category1"
    val processId = createEmptyProcess(processName, category)

    updateCategory(processId, category)

    forScenarioReturned(processName, isAdmin = true) { process =>
      process.processCategory shouldEqual category
    }

    forScenariosReturned(ProcessesQuery.empty, isAdmin = true) { processes =>
      processes.exists(_.processId == processId.value) shouldBe true
    }
    forScenariosDetailsReturned(ProcessesQuery.empty, isAdmin = true) { processes =>
      processes.exists(_.processId.value == processId.value) shouldBe true
    }
  }

  test("search processes by categories") {
    createEmptyProcess(ProcessName("proc1"), TestCat)
    createEmptyProcess(ProcessName("proc2"), TestCat2)

    forScenariosReturned(ProcessesQuery.empty) { processes =>
      processes.size shouldBe 2
    }
    forScenariosDetailsReturned(ProcessesQuery.empty) { processes =>
      processes.size shouldBe 2
    }

    forScenariosReturned(ProcessesQuery.empty.categories(List(TestCat))) { processes =>
      processes.loneElement.name shouldBe "proc1"
    }
    forScenariosDetailsReturned(ProcessesQuery.empty.categories(List(TestCat))) { processes =>
      processes.loneElement.name shouldBe "proc1"
    }

    forScenariosReturned(ProcessesQuery.empty.categories(List(TestCat2))) { processes =>
      processes.loneElement.name shouldBe "proc2"
    }
    forScenariosDetailsReturned(ProcessesQuery.empty.categories(List(TestCat2))) { processes =>
      processes.loneElement.name shouldBe "proc2"
    }

    forScenariosReturned(ProcessesQuery.empty.categories(List(TestCat, TestCat2))) { processes =>
      processes.size shouldBe 2
    }
    forScenariosDetailsReturned(ProcessesQuery.empty.categories(List(TestCat, TestCat2))) { processes =>
      processes.size shouldBe 2
    }
  }

  test("search processes by processing types") {
    createEmptyProcess(processName)

    forScenariosReturned(ProcessesQuery.empty.processingTypes(List(Streaming))) { processes =>
      processes.size shouldBe 1
    }
    forScenariosDetailsReturned(ProcessesQuery.empty.processingTypes(List(Streaming))) { processes =>
      processes.size shouldBe 1
    }
    forScenariosReturned(ProcessesQuery.empty.processingTypes(List(Fraud))) { processes =>
      processes.size shouldBe 0
    }
    forScenariosDetailsReturned(ProcessesQuery.empty.processingTypes(List(Fraud))) { processes =>
      processes.size shouldBe 0
    }
  }

  test("search processes by names") {
    createEmptyProcess(ProcessName("proc1"))
    createEmptyProcess(ProcessName("proc2"))

    forScenariosReturned(ProcessesQuery.empty.names(List("proc1"))) { processes =>
      processes.loneElement.name shouldBe "proc1"
    }
    forScenariosDetailsReturned(ProcessesQuery.empty.names(List("proc1"))) { processes =>
      processes.loneElement.name shouldBe "proc1"
    }
    forScenariosReturned(ProcessesQuery.empty.names(List("proc3"))) { processes =>
      processes.size shouldBe 0
    }
    forScenariosDetailsReturned(ProcessesQuery.empty.names(List("proc3"))) { processes =>
      processes.size shouldBe 0
    }
  }

  test("search processes with multiple parameters") {
    createEmptyProcess(ProcessName("proc1"), TestCat)
    createEmptyProcess(ProcessName("proc2"), TestCat2)
    createArchivedProcess(ProcessName("proc3"))

    forScenariosReturned(
      ProcessesQuery.empty
        .names(List("proc1", "proc3", "procNotExisting"))
        .categories(List(TestCat))
        .processingTypes(List(Streaming))
        .unarchived()
    ) { processes =>
      processes.loneElement.name shouldBe "proc1"
    }
    forScenariosDetailsReturned(
      ProcessesQuery.empty
        .names(List("proc1", "proc3", "procNotExisting"))
        .categories(List(TestCat))
        .processingTypes(List(Streaming))
        .unarchived()
    ) { processes =>
      processes.loneElement.name shouldBe "proc1"
    }
    forScenariosReturned(
      ProcessesQuery.empty
        .names(List("proc1", "proc3", "procNotExisting"))
        .categories(List(TestCat))
        .processingTypes(List(Streaming))
        .archived()
    ) { processes =>
      processes.loneElement.name shouldBe "proc3"
    }
    forScenariosDetailsReturned(
      ProcessesQuery.empty
        .names(List("proc1", "proc3", "procNotExisting"))
        .categories(List(TestCat))
        .processingTypes(List(Streaming))
        .archived()
    ) { processes =>
      processes.loneElement.name shouldBe "proc3"
    }
    forScenariosReturned(ProcessesQuery.empty.names(List("proc1")).categories(List("unknown"))) { processes =>
      processes.size shouldBe 0
    }
    forScenariosDetailsReturned(ProcessesQuery.empty.names(List("proc1")).categories(List("unknown"))) { processes =>
      processes.size shouldBe 0
    }
  }

  test("search processes by isDeployed") {
    val firstProcessor  = ProcessName("Processor1")
    val secondProcessor = ProcessName("Processor2")
    val thirdProcessor  = ProcessName("Processor3")

    createEmptyProcess(firstProcessor)
    createDeployedCanceledProcess(secondProcessor)
    createDeployedProcess(thirdProcessor)

    deploymentManager.withProcessStateStatus(secondProcessor, SimpleStateStatus.Canceled) {
      deploymentManager.withProcessStateStatus(thirdProcessor, SimpleStateStatus.Running) {
        forScenariosReturned(ProcessesQuery.empty) { processes =>
          processes.size shouldBe 3
          val status = processes.find(_.name == firstProcessor.value).flatMap(_.state.map(_.name))
          status shouldBe Some(SimpleStateStatus.NotDeployed.name)
        }
        forScenariosDetailsReturned(ProcessesQuery.empty) { processes =>
          processes.size shouldBe 3
        }

        forScenariosReturned(ProcessesQuery.empty.deployed()) { processes =>
          processes.size shouldBe 1
          val status = processes.find(_.name == thirdProcessor.value).flatMap(_.state.map(_.name))
          status shouldBe Some(SimpleStateStatus.Running.name)
        }
        forScenariosDetailsReturned(ProcessesQuery.empty.deployed()) { processes =>
          processes.size shouldBe 1
        }

        forScenariosReturned(ProcessesQuery.empty.notDeployed()) { processes =>
          processes.size shouldBe 2

          val status = processes.find(_.name == thirdProcessor.value).flatMap(_.state.map(_.name))
          status shouldBe None

          val canceledProcess = processes.find(_.name == secondProcessor.value).flatMap(_.state.map(_.name))
          canceledProcess shouldBe Some(SimpleStateStatus.Canceled.name)
        }
        forScenariosDetailsReturned(ProcessesQuery.empty.notDeployed()) { processes =>
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
    saveProcess(processName, ProcessTestData.validProcess, TestCat) {
      status shouldEqual StatusCodes.OK
      checkSampleProcessRootIdEquals(ProcessTestData.validProcess.nodes.head.id)
      entityAs[ValidationResult].errors.invalidNodes.isEmpty shouldBe true
    }
  }

  test("update process with the same json should not create new version") {
    val command = ProcessTestData.createEmptyUpdateProcessCommand(processName, None)

    createProcessRequest(processName) { code =>
      code shouldBe StatusCodes.Created

      updateProcess(command) {
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

    saveProcess(processName, process, TestCat) {
      forScenarioReturned(processName) { process =>
        process.history.map(_.size) shouldBe Some(2)
      }
      status shouldEqual StatusCodes.OK
    }

    updateProcess(processName, process, comment) {
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
    saveProcess(processName, ProcessTestData.validProcessWithEmptySpelExpr, TestCat) {
      Get(s"/processes/${processName.value}") ~> routeWithAllPermissions ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] should include(processName.value)
      }
    }
  }

  test("save invalid process json with ok status but with non empty invalid nodes") {
    saveProcess(processName, ProcessTestData.invalidProcess, TestCat) {
      status shouldEqual StatusCodes.OK
      checkSampleProcessRootIdEquals(ProcessTestData.invalidProcess.nodes.head.id)
      entityAs[ValidationResult].errors.invalidNodes.isEmpty shouldBe false
    }
  }

  test("return one latest version for process") {
    saveProcess(processName, ProcessTestData.validProcess, TestCat) {
      status shouldEqual StatusCodes.OK
    }

    updateProcess(processName, ProcessTestData.invalidProcess) {
      status shouldEqual StatusCodes.OK
    }

    forScenariosReturned(ProcessesQuery.empty) { processes =>
      val process = processes.find(_.name == SampleProcess.process.id)

      withClue(process) {
        process.isDefined shouldBe true
      }
    }
    forScenariosDetailsReturned(ProcessesQuery.empty) { processes =>
      processes.exists(_.name == SampleProcess.process.id) shouldBe true
    }
  }

  test("save process history") {
    saveProcess(processName, ProcessTestData.validProcess, TestCat) {
      status shouldEqual StatusCodes.OK
    }

    val meta = ProcessTestData.validProcess.metaData
    val changedMeta = meta.copy(additionalFields =
      ProcessAdditionalFields(Some("changed descritption..."), Map.empty, meta.additionalFields.metaDataType)
    )
    updateProcess(processName, ProcessTestData.validProcess.copy(metaData = changedMeta)) {
      status shouldEqual StatusCodes.OK
    }

    getProcess(processName) ~> check {
      val processDetails = responseAs[ProcessDetails]
      processDetails.name shouldBe SampleProcess.process.id
      processDetails.history.map(_.length) shouldBe Some(3)
      // processDetails.history.forall(_.processId == processDetails.id) shouldBe true //TODO: uncomment this when we will support id as Long / ProcessId
    }
  }

  test("access process version and mark latest version") {
    saveProcess(processName, ProcessTestData.validProcess, TestCat) {
      status shouldEqual StatusCodes.OK
    }

    updateProcess(processName, ProcessTestData.invalidProcess) {
      status shouldEqual StatusCodes.OK
    }

    Get(s"/processes/${SampleProcess.process.id}/1") ~> routeWithAllPermissions ~> check {
      val processDetails = responseAs[ValidatedProcessDetails]
      processDetails.processVersionId shouldBe VersionId.initialVersionId
      processDetails.isLatestVersion shouldBe false
    }

    Get(s"/processes/${SampleProcess.process.id}/2") ~> routeWithAllPermissions ~> check {
      val processDetails = responseAs[ValidatedProcessDetails]
      processDetails.processVersionId shouldBe VersionId(2)
      processDetails.isLatestVersion shouldBe false
    }

    Get(s"/processes/${SampleProcess.process.id}/3") ~> routeWithAllPermissions ~> check {
      val processDetails = responseAs[ValidatedProcessDetails]
      processDetails.processVersionId shouldBe VersionId(3)
      processDetails.isLatestVersion shouldBe true
    }
  }

  test("return non-validated process version") {
    saveProcess(processName, ProcessTestData.validProcess, TestCat) {
      status shouldEqual StatusCodes.OK
    }

    Get(s"/processes/${SampleProcess.process.id}/1?skipValidateAndResolve=true") ~> routeWithAllPermissions ~> check {
      val processDetails = responseAs[ProcessDetails]
      processDetails.processVersionId shouldBe VersionId.initialVersionId
      responseAs[String] should not include "validationResult"
      Unmarshal(response).to[ValidatedProcessDetails].failed.futureValue shouldBe a[DecodingFailure]
    }
  }

  test("perform idempotent process save") {
    saveProcessAndAssertSuccess(SampleProcess.process.id, ProcessTestData.validProcess)
    Get(s"/processes/${SampleProcess.process.id}") ~> routeWithAllPermissions ~> check {
      val processHistoryBeforeDuplicatedWrite = responseAs[ProcessDetails].history
      updateProcessAndAssertSuccess(SampleProcess.process.id, ProcessTestData.validProcess)
      Get(s"/processes/${SampleProcess.process.id}") ~> routeWithAllPermissions ~> check {
        val processHistoryAfterDuplicatedWrite = responseAs[ProcessDetails].history
        processHistoryAfterDuplicatedWrite shouldBe processHistoryBeforeDuplicatedWrite
      }
    }
  }

  test("not authorize user with read permissions to modify node") {
    Put(
      s"/processes/$TestCat/${processName.value}",
      posting.toEntityAsProcessToSave(ProcessTestData.validProcess)
    ) ~> routeWithRead ~> check {
      rejection shouldBe server.AuthorizationFailedRejection
    }

    val modifiedParallelism = 123
    val props               = ProcessProperties(StreamMetaData(Some(modifiedParallelism)))
    Put(s"/processes/$TestCat/${processName.value}", posting.toEntity(props)) ~> routeWithRead ~> check {
      rejection shouldBe server.AuthorizationFailedRejection
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
    val processToSave = ProcessTestData.sampleDisplayableProcess.copy(category = TestCat)
    val processName   = ProcessName(processToSave.id)

    createArchivedProcess(processName)

    deleteProcess(processName) { status =>
      status shouldEqual StatusCodes.OK

      tryForScenarioReturned(processName) { (status, _) =>
        status shouldEqual StatusCodes.NotFound
      }
    }

    saveProcess(processToSave) {
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
    val newProcessId = "tst1"
    Post(s"/processes/$newProcessId/$TestCat?isFragment=false") ~> withPermissions(
      processesRoute,
      testPermissionWrite |+| testPermissionRead
    ) ~> check {
      status shouldEqual StatusCodes.Created

      Get(s"/processes/$newProcessId") ~> routeWithRead ~> check {
        status shouldEqual StatusCodes.OK
        val loadedProcess = responseAs[ValidatedProcessDetails]
        loadedProcess.processCategory shouldBe TestCat
        loadedProcess.createdAt should not be null
      }
    }
  }

  test("not allow to save process if already exists") {
    val processToSave = ProcessTestData.sampleDisplayableProcess.copy(category = TestCat)
    saveProcess(processToSave) {
      status shouldEqual StatusCodes.OK
      Post(s"/processes/${processToSave.id}/$TestCat?isFragment=false") ~> routeWithWrite ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }
  }

  test("not allow to save process with category not allowed for user") {
    Post(s"/processes/p11/abcd/${TestProcessingTypes.Streaming}") ~> routeWithWrite ~> check {
      // this one below does not work, but I cannot compose path and authorize directives in a right way
      // rejection shouldBe server.AuthorizationFailedRejection
      handled shouldBe false
    }
  }

  test("return all non-validated processes with details") {
    val firstProcessName  = ProcessName("firstProcessName")
    val secondProcessName = ProcessName("secondProcessName")

    saveProcess(firstProcessName, ProcessTestData.validProcessWithId(firstProcessName.value), TestCat) {
      saveProcess(secondProcessName, ProcessTestData.validProcessWithId(secondProcessName.value), TestCat) {
        Get("/processesDetails?skipValidateAndResolve=true") ~> routeWithAllPermissions ~> check {
          status shouldEqual StatusCodes.OK
          val processes = responseAs[List[ProcessDetails]]
          processes should have size 2
          processes.map(_.name) should contain only (firstProcessName.value, secondProcessName.value)
          responseAs[String] should not include "validationResult"
          Unmarshal(response).to[List[ValidatedProcessDetails]].failed.futureValue shouldBe a[DecodingFailure]
        }
      }
    }
  }

  test("should return statuses only for not archived scenarios (excluding fragments)") {
    createDeployedProcess(processName)
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
    createDeployedProcess(processName)

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
    createEmptyProcess(processName, TestCat, isFragment = true)

    tryForScenarioStatus(processName) { (code, message) =>
      code shouldEqual StatusCodes.BadRequest
      message shouldBe "Fragment doesn't have state."
    }
  }

  test("fetching scenario toolbar definitions") {
    val toolbarConfig = ProcessToolbarsConfigProvider.create(testConfig, Some(TestCat))
    val id            = createEmptyProcess(processName)

    withProcessToolbars(processName) { toolbar =>
      toolbar shouldBe ProcessToolbarSettings(
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
                  Some(s"Custom link for ${processName.value}"),
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
      forScenariosReturned(ProcessesQuery.empty) { processes =>
        val process = processes.find(_.name == expectedName.value).value
        process.state.map(_.name) shouldBe expectedStatus.map(_.name)
      }

      forScenariosDetailsReturned(ProcessesQuery.empty) { processes =>
        val process = processes.find(_.name == expectedName.value).value
        process.state shouldBe None
      }
    }
  }

  private def verifyListOfProcesses(query: ProcessesQuery, expectedNames: List[ProcessName]): Unit = {
    forScenariosReturned(query) { processes =>
      processes.size shouldBe expectedNames.size
      expectedNames.foreach { name =>
        assert(processes.exists(_.name == name.value), s"Missing name: ${name.value} for query: $query.")
      }
    }

    forScenariosDetailsReturned(query) { processes =>
      processes.size shouldBe expectedNames.size
      expectedNames.foreach { name =>
        assert(processes.exists(_.name == name.value), s"Missing name: ${name.value} for query: $query.")
      }
    }
  }

  private def checkSampleProcessRootIdEquals(expected: String): Assertion = {
    fetchSampleProcess()
      .map(_.nodes.head.id)
      .futureValue shouldEqual expected
  }

  private def fetchSampleProcess(): Future[CanonicalProcess] = {
    futureFetchingProcessRepository
      .fetchLatestProcessDetailsForProcessId[CanonicalProcess](getProcessId(processName))
      .map(_.getOrElse(sys.error("Sample process missing")))
      .map(_.json)
  }

  private def getProcessId(processName: ProcessName): ProcessId =
    futureFetchingProcessRepository.fetchProcessId(processName).futureValue.get

  private def renameProcess(processName: ProcessName, newName: ProcessName)(callback: StatusCode => Any): Any =
    Put(s"/processes/${processName.value}/rename/${newName.value}") ~> routeWithAllPermissions ~> check {
      callback(status)
    }

  protected def withProcessToolbars(processName: ProcessName, isAdmin: Boolean = false)(
      callback: ProcessToolbarSettings => Unit
  ): Unit =
    getProcessToolbars(processName, isAdmin) ~> check {
      status shouldEqual StatusCodes.OK
      val toolbar = decode[ProcessToolbarSettings](responseAs[String]).toOption.get
      callback(toolbar)
    }

  private def getProcessToolbars(processName: ProcessName, isAdmin: Boolean = false): RouteTestResult =
    Get(s"/processes/${processName.value}/toolbars") ~> routeWithPermissions(processesRoute, isAdmin)

  private def changeProcessCategory(processName: ProcessName, category: String, isAdmin: Boolean = false)(
      callback: StatusCode => Any
  ): Any =
    Post(s"/processes/category/${processName.value}/$category") ~> routeWithPermissions(
      processesRoute,
      isAdmin
    ) ~> check {
      if (isAdmin) {
        callback(status)
      } else {
        rejection shouldBe server.AuthorizationFailedRejection
      }
    }

  private def archiveProcess(processName: ProcessName)(callback: StatusCode => Any): Any =
    Post(s"/archive/${processName.value}") ~> withPermissions(
      processesRoute,
      testPermissionWrite |+| testPermissionRead
    ) ~> check {
      callback(status)
    }

  private def unArchiveProcess(processName: ProcessName)(callback: StatusCode => Any): Any =
    Post(s"/unarchive/${processName.value}") ~> withPermissions(
      processesRoute,
      testPermissionWrite |+| testPermissionRead
    ) ~> check {
      callback(status)
    }

  private def deleteProcess(processName: ProcessName)(callback: StatusCode => Any): Any =
    Delete(s"/processes/${processName.value}") ~> withPermissions(
      processesRoute,
      testPermissionWrite |+| testPermissionRead
    ) ~> check {
      callback(status)
    }

  private def updateCategory(processId: ProcessId, category: String): XError[Unit] =
    dbioRunner.runInTransaction(writeProcessRepository.updateCategory(processId, category)).futureValue

  private def forScenarioStatus(processName: ProcessName, isAdmin: Boolean = false)(
      callback: (StatusCode, StateJson) => Unit
  ): Unit =
    tryForScenarioStatus(processName, isAdmin = isAdmin) { (status, response) =>
      callback(status, StateJson(parser.decode[Json](response).toOption.value))
    }

  private def tryForScenarioStatus(processName: ProcessName, isAdmin: Boolean = false)(
      callback: (StatusCode, String) => Unit
  ): Unit =
    Get(s"/processes/${processName.value}/status") ~> routeWithPermissions(processesRoute, isAdmin) ~> check {
      callback(status, responseAs[String])
    }

}
