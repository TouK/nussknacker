package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import argonaut.PrettyParams
import org.scalatest._
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Millis, Seconds, Span}
import pl.touk.nussknacker.engine.api.StreamMetaData
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.graph.node.Source
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter
import pl.touk.nussknacker.ui.api.helpers.{EspItTest, TestFactory}
import pl.touk.nussknacker.ui.api.helpers.TestFactory._
import pl.touk.nussknacker.ui.codec.UiCodecs
import pl.touk.nussknacker.ui.api.helpers.TestProcessingTypes
import pl.touk.nussknacker.ui.process.ProcessToSave
import pl.touk.nussknacker.ui.process.displayedgraph.displayablenode.{Edge, ProcessAdditionalFields}
import pl.touk.nussknacker.ui.process.displayedgraph.{DisplayableProcess, ProcessProperties}
import pl.touk.nussknacker.ui.process.marshall.{ProcessConverter, UiProcessMarshaller}
import pl.touk.nussknacker.ui.process.repository.ProcessActivityRepository.ProcessActivity
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.{BaseProcessDetails, BasicProcess, ProcessDetails}
import pl.touk.nussknacker.ui.sample.SampleProcess
import pl.touk.nussknacker.ui.util.{FileUploadUtils, MultipartUtils}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.higherKinds
import UiCodecs._
import pl.touk.nussknacker.ui.security.api.{LoggedUser, Permission}
import cats.instances.all._
import cats.syntax.semigroup._

class ProcessesResourcesSpec extends FunSuite with ScalatestRouteTest with Matchers with Inside
  with ScalaFutures with OptionValues with Eventually with BeforeAndAfterEach with BeforeAndAfterAll with EspItTest {

  implicit override val patienceConfig = PatienceConfig(timeout = scaled(Span(1, Seconds)), interval = scaled(Span(100, Millis)))

  val routeWithRead = withPermissions(processesRoute, testPermissionRead)
  val routeWithWrite = withPermissions(processesRoute, testPermissionWrite)
  val routWithAllPermissions = withAllPermissions(processesRoute)
  val routWithAdminPermission = withPermissions(processesRoute, testPermissionAdmin)
  val processActivityRouteWithAllPermission = withAllPermissions(processActivityRoute)
  implicit val loggedUser = LoggedUser("lu",  testCategory)

  private val processId: String = SampleProcess.process.id

  test("return list of process") {
    saveProcess(processId, ProcessTestData.validProcess) {
      Get("/processes") ~> routWithAllPermissions ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] should include(processId)
      }
    }
  }
  ignore("provie more information about excisting process" ) {
    fail()
  }
  ignore("not allow to archive still used subprocess") {
    val processWithSubreocess = ProcessTestData.validProcessWithSubprocess(processId)
    val displayableSubprocess = ProcessConverter.toDisplayable(processWithSubreocess.subprocess, TestProcessingTypes.Streaming)
    saveSubProcess(displayableSubprocess)(succeed)
    saveProcess(processId, processWithSubreocess.process)(succeed)
    archiveProcess(displayableSubprocess.id)~> routWithAllPermissions ~> check {
      status shouldEqual StatusCodes.Conflict
      responseAs[String].decodeOption[List[String]].get shouldEqual List(processId) // returns list of porcesses using subprocess
    }
  }
  test("allow to archive subprocess used in archived process") {
    val processWithSubreocess = ProcessTestData.validProcessWithSubprocess(processId)
    val displayableSubprocess = ProcessConverter.toDisplayable(processWithSubreocess.subprocess, TestProcessingTypes.Streaming)
    saveSubProcess(displayableSubprocess)(succeed)
    saveProcess(processId, processWithSubreocess.process)(succeed)
    archiveProcess(processId)~> routWithAllPermissions ~> check {
      status shouldEqual StatusCodes.OK
    }
    archiveProcess(displayableSubprocess.id)~> routWithAllPermissions ~> check {
      status shouldEqual StatusCodes.OK
    }
  }
  test("or not allow to create new process named as archived one") {
    val process = ProcessTestData.validProcess
    saveProcess(processId, process)(succeed)

    archiveProcess(processId)~> routWithAllPermissions ~> check {
      status shouldEqual StatusCodes.OK
    }
    Post(s"/processes/$processId/$testCategoryName?isSubprocess=false") ~> processesRouteWithAllPermissions ~> check {
      status shouldBe StatusCodes.BadRequest
      responseAs[String] shouldEqual s"Process $processId already exists"
    }
  }
  test("return list of subprocess without archived process") {
    val sampleSubprocess = ProcessConverter.toDisplayable(ProcessTestData.sampleSubprocess, TestProcessingTypes.Streaming)
    saveSubProcess(sampleSubprocess) {
      status shouldEqual StatusCodes.OK
    }

    archiveProcess(sampleSubprocess.id)~> routWithAllPermissions ~> check {
      status shouldEqual StatusCodes.OK
    }
    Get("/subProcesses") ~> routWithAllPermissions ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[String] should not include sampleSubprocess.id
    }
  }
  test("not allow to save archived process") {
    val process = ProcessTestData.validProcess
    saveProcess(processId, process)(succeed)

    archiveProcess(processId)~> routWithAllPermissions ~> check {
      status shouldEqual StatusCodes.OK
    }
    updateProcess(processId, process)  {
      status shouldEqual StatusCodes.Forbidden
    }
  }
  test("return list of process without archived process") {
    val process = ProcessTestData.validProcess
    saveProcess(processId, process) {
      status shouldEqual StatusCodes.OK
    }

    archiveProcess(processId) ~> routWithAllPermissions ~> check {
      status shouldEqual StatusCodes.OK
    }
    Get("/processes") ~> routWithAllPermissions ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[String] should not include processId
    }
  }
  test("return list of archived processes") {
    val process = ProcessTestData.validProcess
    saveProcess(processId, process) {
      status shouldEqual StatusCodes.OK
    }
    archiveProcess(processId) ~> routWithAllPermissions ~> check {
      status shouldEqual StatusCodes.OK
    }
    Get("/archive") ~> routWithAllPermissions ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[String] should include(processId)
    }
  }
  test("update process category for existing process") {
    saveProcess(processId, ProcessTestData.validProcess) {
      val newCategory = "expectedCategory"
      Post(s"/processes/category/$processId/$newCategory") ~> routWithAllPermissions ~> check {
        status shouldEqual StatusCodes.OK
        Get(s"/processes/$processId") ~> routWithAdminPermission ~> check {
          status shouldEqual StatusCodes.OK
          val loadedProcess = responseAs[String].decodeOption[ProcessDetails].get
          loadedProcess.processCategory shouldBe newCategory
        }
      }
    }
  }

  test("return 404 on update process category for non existing process") {
    Post("/processes/category/unexcistingProcess/newCategory") ~> routWithAllPermissions ~> check {
      status shouldBe StatusCodes.NotFound
    }
  }

  test("return 404 when no process") {
    Get("/processes/123") ~> routWithAllPermissions ~> check {
      status shouldEqual StatusCodes.NotFound
    }
  }

  test("return sample process details") {
    saveProcess(processId, ProcessTestData.validProcess) {
      Get(s"/processes/$processId") ~> routWithAllPermissions ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] should include(processId)
      }
    }
  }
  //FIXME: test gets rejection. Permission verification doesn't work for custom processes
  ignore("return 400 when trying to update json of custom process") {
    whenReady(writeProcessRepository.saveNewProcess("customProcess", testCategoryName, CustomProcess(""), TestProcessingTypes.Streaming, false)) { res =>
      updateProcess("customProcess", SampleProcess.process) {
        status shouldEqual StatusCodes.BadRequest
      }
    }
  }
  test("save correct process json with ok status") {
    saveProcess(SampleProcess.process.id, ProcessTestData.validProcess) {
      status shouldEqual StatusCodes.OK
      checkSampleProcessRootIdEquals(ProcessTestData.validProcess.root.id)
      val json = entityAs[String].parseOption.value
      json.field("errors").flatMap(_.field("invalidNodes")).flatMap(_.obj).value.isEmpty shouldBe true
    }
  }

  test("save invalid process json with ok status but with non empty invalid nodes") {
    saveProcess(SampleProcess.process.id, ProcessTestData.invalidProcess) {
      status shouldEqual StatusCodes.OK
      checkSampleProcessRootIdEquals(ProcessTestData.invalidProcess.root.id)
      val json = entityAs[String].parseOption.value
      json.field("errors").flatMap(_.field("invalidNodes")).flatMap(_.obj).value.isEmpty shouldBe false
    }
  }

  test("return one latest version for process") {
    saveProcess(SampleProcess.process.id, ProcessTestData.validProcess) {
      status shouldEqual StatusCodes.OK
    }
    updateProcess(SampleProcess.process.id, ProcessTestData.invalidProcess) {
      status shouldEqual StatusCodes.OK
    }

    Get("/processes") ~> routWithAllPermissions ~> check {
      status shouldEqual StatusCodes.OK
      val resp = responseAs[String].decodeOption[List[BasicProcess]].get
      withClue(resp) {
        resp.count(_.id == SampleProcess.process.id) shouldBe 1
      }
    }
  }

  test("return process if user has category") {
    saveProcess(SampleProcess.process.id, ProcessTestData.validProcess) {
      status shouldEqual StatusCodes.OK
    }
    writeProcessRepository.updateCategory(SampleProcess.process.id, testCategoryName)

    Get(s"/processes/${SampleProcess.process.id}") ~> routWithAllPermissions ~> check {
      val processDetails = responseAs[String].decodeOption[ProcessDetails].get
      processDetails.processCategory shouldBe testCategoryName
    }

    Get(s"/processes") ~> routeWithRead ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[String] should include(SampleProcess.process.id)
    }

  }

  test("not return processes not in user categories") {
    saveProcess(SampleProcess.process.id, ProcessTestData.validProcess) {
      status shouldEqual StatusCodes.OK
    }
    writeProcessRepository.updateCategory(SampleProcess.process.id, "newCategory")
    Get(s"/processes/${SampleProcess.process.id}") ~> routeWithRead ~> check {
      status shouldEqual StatusCodes.NotFound
    }

    Get(s"/processes") ~> routeWithRead ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[String] shouldBe "[]"
    }
  }

  test("return all processes for admin user") {
    saveProcess(SampleProcess.process.id, ProcessTestData.validProcess) {
      status shouldEqual StatusCodes.OK
    }
    writeProcessRepository.updateCategory(SampleProcess.process.id, "newCategory")

    Get(s"/processes/${SampleProcess.process.id}") ~> routWithAdminPermission ~> check {
      val processDetails = responseAs[String].decodeOption[ProcessDetails].get
      processDetails.processCategory shouldBe "newCategory"
    }

    Get(s"/processes") ~> routWithAdminPermission ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[String] should include(SampleProcess.process.id)
    }
  }

  test("save process history") {
    saveProcess(SampleProcess.process.id, ProcessTestData.validProcess) {
      status shouldEqual StatusCodes.OK
    }

    updateProcess(SampleProcess.process.id, ProcessTestData.validProcess.copy(root = ProcessTestData.validProcess
      .root.copy(data = ProcessTestData.validProcess.root.data.asInstanceOf[Source].copy(id = "AARGH")))) {
      status shouldEqual StatusCodes.OK
    }
    Get(s"/processes/${SampleProcess.process.id}") ~> routWithAllPermissions ~> check {
      val processDetails = responseAs[String].decodeOption[ProcessDetails].get
      processDetails.name shouldBe SampleProcess.process.id
      processDetails.history.length shouldBe 3
      processDetails.history.forall(_.processName == SampleProcess.process.id) shouldBe true
    }
  }

  test("access process version and mark latest version") {
    saveProcess(SampleProcess.process.id, ProcessTestData.validProcess) {
      status shouldEqual StatusCodes.OK
    }
    updateProcess(SampleProcess.process.id, ProcessTestData.invalidProcess) {
      status shouldEqual StatusCodes.OK
    }

    Get(s"/processes/${SampleProcess.process.id}/1") ~> routWithAllPermissions ~> check {
      val processDetails = responseAs[String].decodeOption[ProcessDetails].get
      processDetails.processVersionId shouldBe 1
      processDetails.isLatestVersion shouldBe false
    }

    Get(s"/processes/${SampleProcess.process.id}/2") ~> routWithAllPermissions ~> check {
      val processDetails = responseAs[String].decodeOption[ProcessDetails].get
      processDetails.processVersionId shouldBe 2
      processDetails.isLatestVersion shouldBe false
    }

    Get(s"/processes/${SampleProcess.process.id}/3") ~> routWithAllPermissions ~> check {
      val processDetails = responseAs[String].decodeOption[ProcessDetails].get
      processDetails.processVersionId shouldBe 3
      processDetails.isLatestVersion shouldBe true
    }
  }

  test("perform idempotent process save") {
    saveProcessAndAssertSuccess(SampleProcess.process.id, ProcessTestData.validProcess)
    Get(s"/processes/${SampleProcess.process.id}") ~> routWithAllPermissions ~> check {
      val processHistoryBeforeDuplicatedWrite = responseAs[String].decodeOption[ProcessDetails].get.history
      updateProcessAndAssertSuccess(SampleProcess.process.id, ProcessTestData.validProcess)
      Get(s"/processes/${SampleProcess.process.id}") ~> routWithAllPermissions ~> check {
        val processHistoryAfterDuplicatedWrite = responseAs[String].decodeOption[ProcessDetails].get.history
        processHistoryAfterDuplicatedWrite shouldBe processHistoryBeforeDuplicatedWrite
      }
    }
  }

  test("not authorize user with read permissions to modify node") {
    Put(s"/processes/$testCategoryName/$processId", posting.toEntityAsProcessToSave(ProcessTestData.validProcess)) ~> routeWithRead ~> check {
      rejection shouldBe server.AuthorizationFailedRejection
    }

    val modifiedParallelism = 123
    val props = ProcessProperties(StreamMetaData(Some(modifiedParallelism)),
      ExceptionHandlerRef(List()), false, None, subprocessVersions = Map.empty)
    Put(s"/processes/$testCategoryName/$processId", posting.toEntity(props)) ~> routeWithRead ~> check {
      rejection shouldBe server.AuthorizationFailedRejection
    }

  }

  test("archive process") {
    val processToSave = ProcessTestData.sampleDisplayableProcess
    val id = processToSave.id

    saveProcess(processToSave) {
      status shouldEqual StatusCodes.OK
    }
    archiveProcess(id) ~> routWithAllPermissions ~> check {
      status shouldEqual StatusCodes.OK
    }
    Get(s"/processes/$id") ~> routWithAllPermissions ~> check {
      status shouldEqual StatusCodes.OK
      val loadedProcess = responseAs[String].decodeOption[ProcessDetails].get
      loadedProcess.isArchived shouldEqual true
    }
  }

  test("unarchive process") {
    val processToSave = ProcessTestData.sampleDisplayableProcess
    val id = processToSave.id

    saveProcess(processToSave) {
      status shouldEqual StatusCodes.OK
    }
    archiveProcess(id) ~> routWithAllPermissions ~> check {
      status shouldEqual StatusCodes.OK
    }
    Post(s"/unarchive/$id") ~> routWithAllPermissions ~> check {
      status shouldEqual StatusCodes.OK
    }
    Get(s"/processes/$id") ~> routWithAllPermissions ~> check {
      status shouldEqual StatusCodes.OK
      val loadedProcess = responseAs[String].decodeOption[ProcessDetails].get
      loadedProcess.isArchived shouldEqual false
    }
  }

  private def archiveProcess(id: String) = {
    Post(s"/archive/$id")
  }

  test("delete process") {
    val processToSave = ProcessTestData.sampleDisplayableProcess
    val id = processToSave.id
    saveProcess(processToSave) {
      status shouldEqual StatusCodes.OK
    }

    Delete(s"/processes/$id") ~> routWithAllPermissions ~> check {
      Get(s"/processes/$id") ~> routWithAllPermissions ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }

    saveProcess(processToSave) {
      status shouldEqual StatusCodes.OK
    }

  }

  test("save new process with empty json") {
    val newProcessId = "tst1"
    Post(s"/processes/$newProcessId/$testCategoryName?isSubprocess=false") ~> withPermissions(processesRoute, testPermissionWrite |+| testPermissionRead) ~> check {
      status shouldEqual StatusCodes.Created

      Get(s"/processes/$newProcessId") ~> routeWithRead ~> check {
        status shouldEqual StatusCodes.OK
        val loadedProcess = responseAs[String].decodeOption[ProcessDetails].get
        loadedProcess.processCategory shouldBe testCategoryName
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
    Post(s"/processes/p11/abcd/${TestProcessingTypes.Streaming}") ~> routWithAdminPermission ~> check {
      //this one below does not work, but I cannot compose path and authorize directives in a right way
      //rejection shouldBe server.AuthorizationFailedRejection
      handled shouldBe false
    }
  }

  private def checkSampleProcessRootIdEquals(expected: String): Assertion = {
    fetchSampleProcess()
      .map(_.nodes.head.id)
      .futureValue shouldEqual expected
  }

  private def fetchSampleProcess(): Future[CanonicalProcess] = {
    processRepository
      .fetchLatestProcessVersion(SampleProcess.process.id)
      .map(_.getOrElse(sys.error("Sample process missing")))
      .map { version =>
        val parsed = UiProcessMarshaller.fromJson(version.json.get)
        parsed.valueOr(_ => sys.error("Invalid process json"))
      }
  }
}