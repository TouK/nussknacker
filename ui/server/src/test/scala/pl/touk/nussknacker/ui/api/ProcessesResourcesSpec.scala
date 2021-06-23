package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.{ContentTypeRange, StatusCode, StatusCodes}
import akka.http.scaladsl.server
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import cats.instances.all._
import cats.syntax.semigroup._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.scalatest._
import org.scalatest.LoneElement._
import pl.touk.nussknacker.engine.api.StreamMetaData
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.{SimpleProcessStateDefinitionManager, SimpleStateStatus}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.graph.node.Source
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ProcessProperties}
import pl.touk.nussknacker.restmodel.process.{ProcessId, UpdateProcessResponse}
import pl.touk.nussknacker.restmodel.processdetails.ProcessDetails
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationResult
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.EspError.XError
import pl.touk.nussknacker.ui.api.helpers.TestFactory._
import pl.touk.nussknacker.ui.api.helpers._
import pl.touk.nussknacker.ui.config.processtoolbar.ProcessToolbarsConfigProvider
import pl.touk.nussknacker.ui.config.processtoolbar.ToolbarButtonConfigType.{CustomLink, ProcessDeploy, ProcessSave}
import pl.touk.nussknacker.ui.config.processtoolbar.ToolbarPanelTypeConfig.{AttachmentsPanel, CommentsPanel, CreatorPanel, ProcessInfoPanel, TipsPanel}
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.repository.ProcessActivityRepository.ProcessActivity
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.service.{ProcessToolbarSettings, ToolbarButton, ToolbarPanel}

import java.util.UUID
import scala.concurrent.Future
import scala.language.higherKinds

/**
  * TODO: On resource tests we should verify permissions and encoded response data. All business logic should be tested at ProcessServiceDb. 
  */
class ProcessesResourcesSpec extends FunSuite with ScalatestRouteTest with Matchers with Inside with FailFastCirceSupport
  with PatientScalaFutures with OptionValues with BeforeAndAfterEach with BeforeAndAfterAll with EspItTest {

  import io.circe._, io.circe.parser._

  private implicit final val string: FromEntityUnmarshaller[String] = Unmarshaller.stringUnmarshaller.forContentTypes(ContentTypeRange.*)

  override protected def createProcessManager(): MockProcessManager = new MockProcessManager(SimpleStateStatus.NotDeployed)

  val routeWithRead: Route = withPermissions(processesRoute, testPermissionRead)
  val routeWithWrite: Route = withPermissions(processesRoute, testPermissionWrite)
  val routeWithAllPermissions: Route = withAllPermissions(processesRoute)
  val routeWithAdminPermissions: Route = withAdminPermissions(processesRoute)
  implicit val loggedUser: LoggedUser = LoggedUser("1", "lu", testCategory)

  private val processName: ProcessName = ProcessName(SampleProcess.process.id)

  test("return list of process") {
    val processId = createProcess(processName)

    withProcesses(ProcessesQuery.empty) { processes =>
      processes.exists(_.processId == processId.value) shouldBe true
    }
  }

  test("return single process") {
    val processId = createDeployedProcess(processName)

    withProcess(processName) { process =>
      process.processId shouldBe processId.value
      process.name shouldBe processName.value
      process.stateStatus shouldBe Some(SimpleStateStatus.Running.name)
      process.stateTooltip shouldBe SimpleProcessStateDefinitionManager.statusTooltip(SimpleStateStatus.Running)
      process.stateDescription shouldBe SimpleProcessStateDefinitionManager.statusDescription(SimpleStateStatus.Running)
      process.stateIcon shouldBe SimpleProcessStateDefinitionManager.statusIcon(SimpleStateStatus.Running)
    }
  }

  //FIXME: Implement subprocess valiation
  ignore("not allow to archive still used subprocess") {
    val processWithSubprocess = ProcessTestData.validProcessWithSubprocess(processName)
    val displayableSubprocess = ProcessConverter.toDisplayable(processWithSubprocess.subprocess, TestProcessingTypes.Streaming)
    saveSubProcess(displayableSubprocess)(succeed)
    saveProcess(processName, processWithSubprocess.process)(succeed)

    archiveProcess(ProcessName(displayableSubprocess.id)) { status =>
      status shouldEqual StatusCodes.Conflict
    }
  }

  test("not allow to archive still running process") {
    createDeployedProcess(processName)

    processManager.withProcessStateStatus(SimpleStateStatus.Running) {
      archiveProcess(processName) { status =>
        status shouldEqual StatusCodes.Conflict
      }
    }
  }

  test("allow to archive subprocess used in archived process") {
    val processWithSubprocess = ProcessTestData.validProcessWithSubprocess(processName)
    val displayableSubprocess = ProcessConverter.toDisplayable(processWithSubprocess.subprocess, TestProcessingTypes.Streaming)
    saveSubProcess(displayableSubprocess)(succeed)
    saveProcess(processName, processWithSubprocess.process)(succeed)

    archiveProcess(processName) { status =>
      status shouldEqual StatusCodes.OK
    }

    archiveProcess(ProcessName(displayableSubprocess.id)) { status =>
      status shouldEqual StatusCodes.OK
    }
  }

  test("or not allow to create new process named as archived one") {
    val process = ProcessTestData.validProcess
    saveProcess(processName, process)(succeed)

    archiveProcess(processName) { status =>
      status shouldEqual StatusCodes.OK
    }

    Post(s"/processes/${processName.value}/$testCategoryName?isSubprocess=false") ~> processesRouteWithAllPermissions ~> check {
      status shouldBe StatusCodes.BadRequest
      responseAs[String] shouldEqual s"Process ${processName.value} already exists"
    }
  }

  test("should allow rename not deployed process") {
    val processId = createProcess(processName)
    val newName = ProcessName("ProcessChangedName")

    renameProcess(processName, newName) { status =>
      status shouldEqual StatusCodes.OK
      getProcessId(newName) shouldBe processId
    }
  }

  test("should allow rename canceled process") {
    val processId = createDeployedCanceledProcess(processName)
    val newName = ProcessName("ProcessChangedName")

    renameProcess(processName, newName) { status =>
      status shouldEqual StatusCodes.OK
      getProcessId(newName) shouldBe processId
    }
  }

  test("should not allow rename deployed process") {
    createDeployedProcess(processName)
    val newName = ProcessName("ProcessChangedName")

    renameProcess(processName, newName) { status =>
      status shouldEqual StatusCodes.Conflict
    }
  }

  test("should not allow rename archived process") {
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
  ignore("should not allow rename process with running state") {
    createProcess(processName)
    val newName = ProcessName("ProcessChangedName")

    processManager.withProcessStateStatus(SimpleStateStatus.Running) {
      renameProcess(processName, newName) { status =>
        status shouldEqual StatusCodes.Conflict
      }
    }
  }

  test("return list of subprocess without archived process") {
    val sampleSubprocess = ProcessConverter.toDisplayable(ProcessTestData.sampleSubprocess, TestProcessingTypes.Streaming)
    saveSubProcess(sampleSubprocess) {
      status shouldEqual StatusCodes.OK
    }

    archiveProcess(ProcessName(sampleSubprocess.id)) { status =>
      status shouldEqual StatusCodes.OK
    }

    Get("/subProcesses") ~> routeWithAllPermissions ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[String] should not include sampleSubprocess.id
    }

    Get("/processes?isSubprocess=true&isArchived=false") ~> routeWithAllPermissions ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[String] should not include sampleSubprocess.id
    }
  }

  test("not allow to save archived process") {
    createArchivedProcess(processName)
    val process = ProcessTestData.validProcess

    updateProcess(processName, process)  {
      status shouldEqual StatusCodes.Conflict
    }
  }

  test("return list of process without archived process") {
    createArchivedProcess(processName)

    Get("/processes") ~> routeWithAllPermissions ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[String] should not include processName.value
    }
  }
  test("return list of archived processes") {
    val process = ProcessTestData.validProcess
    saveProcess(processName, process) {
      status shouldEqual StatusCodes.OK
    }

    archiveProcess(processName) { status =>
      status shouldEqual StatusCodes.OK
    }

    Get("/archive") ~> routeWithAllPermissions ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[String] should include(processName.value)
    }

    Get("/processes?isArchived=true") ~> routeWithAllPermissions ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[String] should include(processName.value)
    }
  }

  test("allow update category for existing process") {
    val processId = createProcess(processName)

    changeProcessCategory(processName, secondTestCategoryName, isAdmin = true) { status =>
      status shouldEqual StatusCodes.OK

      val process = getProcessDetails(processId)
      process.processCategory shouldBe secondTestCategoryName
    }
  }

  test("not allow update to not existed category") {
    createProcess(processName)

    changeProcessCategory(processName, "not-exists-category", isAdmin = true) { status =>
      status shouldEqual StatusCodes.BadRequest
    }
  }

  test("not allow update category archived process") {
    createArchivedProcess(processName)

    changeProcessCategory(processName, secondTestCategoryName, isAdmin = true) { status =>
      status shouldEqual StatusCodes.Conflict
    }
  }

  test("return 404 on update process category for non existing process") {
    changeProcessCategory(ProcessName("not-exists-process"), secondTestCategoryName, isAdmin = true) { status =>
      status shouldBe StatusCodes.NotFound
    }
  }

  test("return 403 on update process category for normal user") {
    createArchivedProcess(processName)
    //Verification of rejection is done on changeProcessCategory
    changeProcessCategory(processName, secondTestCategoryName) {_ => }
  }

  test("return process if user has category") {
    val processId = createProcess(processName)
    updateCategory(processId, secondTestCategoryName)

    withProcess(processName) { process =>
      process.processCategory shouldBe secondTestCategoryName
      process.processId shouldBe processId.value
    }
  }

  test("not return processes not in user categories") {
    val processId = createProcess(processName)
    val category = "Category1"

    updateCategory(processId, category)

    tryProcess(processName) { (status, _) =>
      status shouldEqual StatusCodes.NotFound
    }

    withProcesses(ProcessesQuery.empty) { processes =>
      processes.isEmpty shouldBe true
    }
  }

  test("return all processes for admin user") {
    val processId = createProcess(processName)
    val category = "Category1"

    updateCategory(processId, category)

    withProcess(processName, isAdmin = true) { process =>
      process.processCategory shouldEqual category
    }

    withProcesses(ProcessesQuery.empty, isAdmin = true) { processes =>
      processes.exists(_.processId == processId.value) shouldBe true
    }
  }

  test("search processes by categories") {
    createProcess(ProcessName("Processor1"), testCategoryName, false)
    createProcess(ProcessName("Processor2"), secondTestCategoryName, false)

    withProcesses(ProcessesQuery.empty) { processes =>
      processes.size shouldBe 2
    }

    withProcesses(ProcessesQuery.categories(List(testCategoryName))) { processes =>
      processes.size shouldBe 1
    }

    withProcesses(ProcessesQuery.categories(List(secondTestCategoryName))) { processes =>
      processes.size shouldBe 1
    }

    withProcesses(ProcessesQuery.categories(List(testCategoryName, secondTestCategoryName))) { processes =>
      processes.size shouldBe 2
    }
  }

  test("search processes by isDeployed") {
    val firstProcessor = ProcessName("Processor1")
    val secondProcessor = ProcessName("Processor2")
    val thirdProcessor = ProcessName("Processor3")

    createProcess(firstProcessor, testCategoryName, false)
    createDeployedCanceledProcess(secondProcessor, testCategoryName, false)
    createDeployedProcess(thirdProcessor, testCategoryName, false)

    Get(s"/processes") ~> routeWithAllPermissions ~> check {
      status shouldEqual StatusCodes.OK
      val data = responseAs[List[Json]]
      data.size shouldBe 3

      val process = findJsonProcess(responseAs[String], firstProcessor.value)
      process.value.stateStatus shouldBe Some(SimpleStateStatus.NotDeployed.name)
    }

    Get(s"/processes?isDeployed=true") ~> routeWithAllPermissions ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[List[Json]].size shouldBe 1

      val process = findJsonProcess(responseAs[String], thirdProcessor.value)
      process.value.name shouldBe thirdProcessor.value
      process.value.stateStatus shouldBe Some(SimpleStateStatus.Running.name)
    }

    Get(s"/processes?isDeployed=false") ~> routeWithAllPermissions ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[List[Json]].size shouldBe 2

      findJsonProcess(responseAs[String], thirdProcessor.value) shouldBe Option.empty

      val canceledProcess = findJsonProcess(responseAs[String], secondProcessor.value)
      canceledProcess.value.stateStatus shouldBe Some(SimpleStateStatus.Canceled.name)
    }
  }

  test("return 404 when no process") {
    tryProcess(ProcessName("nont-exists")) { (status, _) =>
      status shouldEqual StatusCodes.NotFound
    }
  }

  test("return sample process details") {
    createProcess(processName)

    withProcess(processName) { process =>
      process.name shouldBe processName.value
    }
  }

  test("return 409 when trying to update json of custom process") {
    createCustomProcess(processName, testCategoryName)

    updateProcess(processName, SampleProcess.process) {
      status shouldEqual StatusCodes.Conflict
    }
  }

  test("save correct process json with ok status") {
    saveProcess(processName, ProcessTestData.validProcess) {
      status shouldEqual StatusCodes.OK
      checkSampleProcessRootIdEquals(ProcessTestData.validProcess.roots.head.id)
      entityAs[ValidationResult].errors.invalidNodes.isEmpty shouldBe true
    }
  }

  test("update process with the same json should not create new version") {
    val command = ProcessTestData.createEmptyUpdateProcessCommand(processName, None)

    createProcessRequest(processName) { code =>
      code shouldBe StatusCodes.Created

      updateProcess(command) {
        withProcess(processName) { process =>
          process.history.map(_.size) shouldBe Some(1)
        }
        status shouldEqual StatusCodes.OK
      }
    }
  }

  test("update process with the same json should add comment for current version") {
    val process = ProcessTestData.validProcess
    val comment = "Update the same version"

    saveProcess(processName, process) {
      withProcess(processName) { process =>
        process.history.map(_.size) shouldBe Some(2)
      }
      status shouldEqual StatusCodes.OK
    }

    updateProcess(processName, process, comment) {
      withProcess(processName) { process =>
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
    saveProcess(processName, ProcessTestData.validProcessWithEmptyExpr) {
      Get(s"/processes/${processName.value}") ~> routeWithAllPermissions ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] should include(processName.value)
      }
    }
  }

  test("save invalid process json with ok status but with non empty invalid nodes") {
    saveProcess(processName, ProcessTestData.invalidProcess) {
      status shouldEqual StatusCodes.OK
      checkSampleProcessRootIdEquals(ProcessTestData.invalidProcess.roots.head.id)
      entityAs[ValidationResult].errors.invalidNodes.isEmpty shouldBe false
    }
  }

  test("return one latest version for process") {
    saveProcess(processName, ProcessTestData.validProcess) {
      status shouldEqual StatusCodes.OK
    }

    updateProcess(processName, ProcessTestData.invalidProcess) {
      status shouldEqual StatusCodes.OK
    }

    Get("/processes") ~> routeWithAllPermissions ~> check {
      status shouldEqual StatusCodes.OK
      val process = findJsonProcess(responseAs[String])

      withClue(process) {
        process.isDefined shouldBe true
      }
    }
  }

  test("save process history") {
    saveProcess(processName, ProcessTestData.validProcess) {
      status shouldEqual StatusCodes.OK
    }

    updateProcess(processName, ProcessTestData.validProcess.copy(roots = ProcessTestData.validProcess
      .roots.map(r => r.copy(data = r.data.asInstanceOf[Source].copy(id = "AARGH"))))) {
      status shouldEqual StatusCodes.OK
    }

    Get(s"/processes/${SampleProcess.process.id}") ~> routeWithAllPermissions ~> check {
      val processDetails = responseAs[ProcessDetails]
      processDetails.name shouldBe SampleProcess.process.id
      processDetails.history.length shouldBe 3
      //processDetails.history.forall(_.processId == processDetails.id) shouldBe true //TODO: uncomment this when we will support id as Long / ProcessId
    }
  }

  test("access process version and mark latest version") {
    saveProcess(processName, ProcessTestData.validProcess) {
      status shouldEqual StatusCodes.OK
    }

    updateProcess(processName, ProcessTestData.invalidProcess) {
      status shouldEqual StatusCodes.OK
    }

    Get(s"/processes/${SampleProcess.process.id}/1") ~> routeWithAllPermissions ~> check {
      val processDetails = responseAs[ProcessDetails]
      processDetails.processVersionId shouldBe 1
      processDetails.isLatestVersion shouldBe false
    }

    Get(s"/processes/${SampleProcess.process.id}/2") ~> routeWithAllPermissions ~> check {
      val processDetails = responseAs[ProcessDetails]
      processDetails.processVersionId shouldBe 2
      processDetails.isLatestVersion shouldBe false
    }

    Get(s"/processes/${SampleProcess.process.id}/3") ~> routeWithAllPermissions ~> check {
      val processDetails = responseAs[ProcessDetails]
      processDetails.processVersionId shouldBe 3
      processDetails.isLatestVersion shouldBe true
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
    Put(s"/processes/$testCategoryName/${processName.value}", posting.toEntityAsProcessToSave(ProcessTestData.validProcess)) ~> routeWithRead ~> check {
      rejection shouldBe server.AuthorizationFailedRejection
    }

    val modifiedParallelism = 123
    val props = ProcessProperties(StreamMetaData(Some(modifiedParallelism)),
      ExceptionHandlerRef(List()), false, None, subprocessVersions = Map.empty)
    Put(s"/processes/$testCategoryName/${processName.value}", posting.toEntity(props)) ~> routeWithRead ~> check {
      rejection shouldBe server.AuthorizationFailedRejection
    }
  }

  test("archive process") {
    createProcess(processName)

    archiveProcess(processName) { status =>
      status shouldEqual StatusCodes.OK

      withProcess(processName) { process =>
        process.lastActionType shouldBe Some(ProcessActionType.Archive.toString)
        process.stateStatus shouldBe Some(SimpleStateStatus.NotDeployed.name)
        process.isArchived shouldBe true
      }
    }
  }

  test("unarchive process") {
    createArchivedProcess(processName)

    unArchiveProcess(processName) { status =>
      status shouldEqual StatusCodes.OK

      withProcess(processName) { process =>
        process.lastActionType shouldBe Some(ProcessActionType.UnArchive.toString)
        process.stateStatus shouldBe Some(SimpleStateStatus.NotDeployed.name)
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
    createProcess(processName)

    unArchiveProcess(processName) { status =>
      status shouldEqual StatusCodes.Conflict
    }
  }

  test("allow to delete process") {
    val processToSave = ProcessTestData.sampleDisplayableProcess
    val processName = ProcessName(processToSave.id)

    saveProcess(processToSave) {
      status shouldEqual StatusCodes.OK
    }

    deleteProcess(processName) { status =>
      status shouldEqual StatusCodes.OK

      tryProcess(processName) { (status, _) =>
        status shouldEqual StatusCodes.NotFound
      }
    }

    saveProcess(processToSave) {
      status shouldEqual StatusCodes.OK
    }
  }

  test("not allow to delete still running process") {
    createDeployedProcess(processName)

    deleteProcess(processName) { status =>
      status shouldEqual StatusCodes.Conflict

      withProcess(processName) { process =>
        process.isDeployed shouldBe true
      }
    }
  }

  test("save new process with empty json") {
    val newProcessId = "tst1"
    Post(s"/processes/$newProcessId/$testCategoryName?isSubprocess=false") ~> withPermissions(processesRoute, testPermissionWrite |+| testPermissionRead) ~> check {
      status shouldEqual StatusCodes.Created

      Get(s"/processes/$newProcessId") ~> routeWithRead ~> check {
        status shouldEqual StatusCodes.OK
        val loadedProcess = responseAs[ProcessDetails]
        loadedProcess.processCategory shouldBe testCategoryName
        loadedProcess.createdAt should not be null
      }
    }
  }

  test("not allow to save process if already exists") {
    val processToSave = ProcessTestData.sampleDisplayableProcess
    saveProcess(processToSave) {
      status shouldEqual StatusCodes.OK
      Post(s"/processes/${processToSave.id}/$testCategoryName?isSubprocess=false") ~> routeWithWrite ~> check {
        status shouldEqual StatusCodes.BadRequest
      }
    }
  }

  test("not allow to save process with category not allowed for user") {
    Post(s"/processes/p11/abcd/${TestProcessingTypes.Streaming}") ~> routeWithWrite ~> check {
      //this one below does not work, but I cannot compose path and authorize directives in a right way
      //rejection shouldBe server.AuthorizationFailedRejection
      handled shouldBe false
    }
  }

  test("return all processes with details") {
    val firstProcessName = ProcessName("firstProcessName")
    val secondProcessName = ProcessName("secondProcessName")

    saveProcess(firstProcessName, ProcessTestData.validProcessWithId(firstProcessName.value)) {
      saveProcess(secondProcessName, ProcessTestData.validProcessWithId(secondProcessName.value)) {
        Get("/processesDetails") ~> routeWithAllPermissions ~> check {
          status shouldEqual StatusCodes.OK
          responseAs[String] should include ("firstProcessName")
          responseAs[String] should include ("secondProcessName")
        }
      }
    }
  }

  test("return filtered processes details list (just matching)") {
    val firstProcessName = ProcessName("firstProcessName")
    val secondProcessName = ProcessName("secondProcessName")

    saveProcess(firstProcessName, ProcessTestData.validProcessWithId(firstProcessName.value)) {
      saveProcess(secondProcessName, ProcessTestData.validProcessWithId(secondProcessName.value)) {
        Get(s"/processesDetails?names=${firstProcessName.value}") ~> routeWithAllPermissions ~> check {
          status shouldEqual StatusCodes.OK
          responseAs[String] should include ("firstProcessName")
          responseAs[String] should not include "secondProcessName"
        }
      }
    }
  }

  test("return filtered processes details list (multiple)") {
    val firstProcessName = ProcessName("firstProcessName")
    val secondProcessName = ProcessName("secondProcessName")

    saveProcess(firstProcessName, ProcessTestData.validProcessWithId(firstProcessName.value)) {
      saveProcess(secondProcessName, ProcessTestData.validProcessWithId(secondProcessName.value)) {
        Get(s"/processesDetails?names=${firstProcessName.value},${secondProcessName.value}") ~> routeWithAllPermissions ~> check {
          status shouldEqual StatusCodes.OK
          responseAs[String] should include ("firstProcessName")
          responseAs[String] should include ("secondProcessName")
        }
      }
    }
  }

  test("return filtered processes details list (empty)") {
    val firstProcessName = ProcessName("firstProcessName")
    val secondProcessName = ProcessName("secondProcessName")

    saveProcess(firstProcessName, ProcessTestData.validProcessWithId(firstProcessName.value)) {
      saveProcess(secondProcessName, ProcessTestData.validProcessWithId(secondProcessName.value)) {
        Get(s"/processesDetails?names=non-existing-name") ~> routeWithAllPermissions ~> check {
          status shouldEqual StatusCodes.OK
          responseAs[String] should not include "firstProcessName"
          responseAs[String] should not include "secondProcessName"
        }
      }
    }
  }

  test("fetching status for non exists process should return 404 ") {
    Get(s"/processes/non-exists-process/status") ~> routeWithAllPermissions ~> check {
      status shouldEqual StatusCodes.NotFound
    }
  }

  test("fetching status for deployed process should properly return status") {
    createDeployedProcess(processName)

    processManager.withProcessStateStatus(SimpleStateStatus.Running) {
      Get(s"/processes/${processName.value}/status") ~> routeWithAllPermissions ~> check {
        status shouldEqual StatusCodes.OK
        val stateStatusResponse = parseStateResponse(responseAs[Json])
        stateStatusResponse.name shouldBe SimpleStateStatus.Running.name
        stateStatusResponse.`type` shouldBe SimpleStateStatus.Running.getClass.getSimpleName
      }
    }
  }

  test("fetching process toolbar definitions") {
    val toolbarConfig = ProcessToolbarsConfigProvider.create(testConfig, Some(TestPermissions.testCategoryName))
    val id = createProcess(processName)

    withProcessToolbars(processName) { toolbar =>
      toolbar shouldBe ProcessToolbarSettings(
        uuid = UUID.nameUUIDFromBytes(s"${toolbarConfig.hashCode()}-false-false".getBytes()),
        List(
          ToolbarPanel(TipsPanel, None, None, None),
          ToolbarPanel(CreatorPanel, None, None, None)
        ),
        List(),
        List(ToolbarPanel(ProcessInfoPanel, None, None, Some(List(
          ToolbarButton(ProcessSave, None, None, None, disabled = true),
          ToolbarButton(ProcessDeploy, None, None, None, disabled = false),
          ToolbarButton(CustomLink, Some(s"Custom link for ${processName.value}"), None, Some(s"/test/${id.value}"), disabled = false)
        )))),
        List()
      )
    }
  }

  test("fetching toolbar definitions for not exist process should return 404 response") {
    getProcessToolbars(processName) ~> check {
      status shouldEqual StatusCodes.NotFound
    }
  }

  case class StateStatusResponse(name: String, `type`: String)

  private def parseStateResponse(stateResponse: Json): StateStatusResponse = {
    val name = stateResponse.hcursor
      .downField("status")
      .downField("name")
      .as[String].right.get

    val statusType = stateResponse.hcursor
      .downField("status")
      .downField("type")
      .as[String].right.get

    StateStatusResponse(name, statusType)
  }

  private def checkSampleProcessRootIdEquals(expected: String): Assertion = {
    fetchSampleProcess()
      .map(_.nodes.head.id)
      .futureValue shouldEqual expected
  }

  private def fetchSampleProcess(): Future[CanonicalProcess] = {
    fetchingProcessRepository
      .fetchLatestProcessVersion[DisplayableProcess](getProcessId(processName))
      .map(_.getOrElse(sys.error("Sample process missing")))
      .map { version =>
        val parsed = ProcessMarshaller.fromJson(version.json.get)
        parsed.valueOr(_ => sys.error("Invalid process json"))
      }
  }

  private def getProcessId(processName: ProcessName): ProcessId =
    fetchingProcessRepository.fetchProcessId(processName).futureValue.get

  private def renameProcess(processName: ProcessName, newName: ProcessName)(callback: StatusCode => Any): Any =
    Put(s"/processes/${processName.value}/rename/${newName.value}") ~> routeWithAllPermissions ~> check {
      callback(status)
    }

  protected def withProcessToolbars(processName: ProcessName, isAdmin: Boolean = false)(callback: ProcessToolbarSettings => Unit): Unit =
    getProcessToolbars(processName, isAdmin) ~> check {
      status shouldEqual StatusCodes.OK
      val toolbar = decode[ProcessToolbarSettings](responseAs[String]).getOrElse(throw new IllegalArgumentException("Error at parsing response."))
      callback(toolbar)
    }

  private def getProcessToolbars(processName: ProcessName, isAdmin: Boolean = false): RouteTestResult =
    Get(s"/processes/${processName.value}/toolbars") ~> routeWithPermissions(processesRoute, isAdmin)

  private def changeProcessCategory(processName: ProcessName, category: String, isAdmin: Boolean = false)(callback: StatusCode => Any): Any =
    Post(s"/processes/category/${processName.value}/$category") ~> routeWithPermissions(processesRoute, isAdmin) ~> check {
      if (isAdmin) {
        callback(status)
      } else {
        rejection shouldBe server.AuthorizationFailedRejection
      }
    }

  private def archiveProcess(processName: ProcessName)(callback: StatusCode => Any): Any =
    Post(s"/archive/${processName.value}") ~> withPermissions(processesRoute, testPermissionWrite |+| testPermissionRead) ~> check {
      callback(status)
    }

  private def unArchiveProcess(processName: ProcessName)(callback: StatusCode => Any): Any =
    Post(s"/unarchive/${processName.value}") ~> withPermissions(processesRoute, testPermissionWrite |+| testPermissionRead) ~> check {
      callback(status)
    }

  private def deleteProcess(processName: ProcessName)(callback: StatusCode => Any): Any =
    Delete(s"/processes/${processName.value}") ~> withPermissions(processesRoute, testPermissionWrite |+| testPermissionRead) ~> check {
      callback(status)
    }

  private def updateCategory(processId: ProcessId, category: String): XError[Unit] =
    repositoryManager.runInTransaction(writeProcessRepository.updateCategory(processId, category)).futureValue
}
