package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.StatusCodes
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
import pl.touk.nussknacker.ui.api.helpers.EspItTest
import pl.touk.nussknacker.ui.api.helpers.TestFactory._
import pl.touk.nussknacker.ui.codec.UiCodecs
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessingType
import pl.touk.nussknacker.ui.process.ProcessToSave
import pl.touk.nussknacker.ui.process.displayedgraph.displayablenode.{Edge, ProcessAdditionalFields}
import pl.touk.nussknacker.ui.process.displayedgraph.{DisplayableProcess, ProcessProperties}
import pl.touk.nussknacker.ui.process.marshall.UiProcessMarshaller
import pl.touk.nussknacker.ui.process.repository.ProcessActivityRepository.ProcessActivity
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.ProcessDetails
import pl.touk.nussknacker.ui.sample.SampleProcess
import pl.touk.nussknacker.ui.util.{FileUploadUtils, MultipartUtils}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.higherKinds
import UiCodecs._
import pl.touk.nussknacker.ui.security.api.{LoggedUser, Permission}

class ProcessesResourcesSpec extends FlatSpec with ScalatestRouteTest with Matchers with Inside
  with ScalaFutures with OptionValues with Eventually with BeforeAndAfterEach with BeforeAndAfterAll with EspItTest {

  implicit override val patienceConfig = PatienceConfig(timeout = scaled(Span(1, Seconds)), interval = scaled(Span(100, Millis)))

  val routeWithRead = withPermissions(processesRoute, Permission.Read)
  val routeWithWrite = withPermissions(processesRoute, Permission.Write)
  val routWithAllPermissions = withAllPermissions(processesRoute)
  val routWithAdminPermission = withPermissions(processesRoute, Permission.Admin)
  val processActivityRouteWithAllPermission = withAllPermissions(processActivityRoute)
  implicit val loggedUser = LoggedUser("lu", List(), List(testCategory))

  private val processId: String = SampleProcess.process.id

  it should "return list of process details" in {
    saveProcess(processId, ProcessTestData.validProcess) {
      Get("/processes") ~> routWithAllPermissions ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] should include(processId)
      }
    }
  }

  it should "update process category for existing process" in {
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

  it should "throw exception on update process category for non existing process" in {
      Post("/processes/category/unexcistingProcess/newCategory") ~> routWithAllPermissions ~> check {
        status shouldEqual StatusCodes.NotFound
      }
  }

  it should "return 404 when no process" in {
    Get("/processes/123") ~> routWithAllPermissions ~> check {
      status shouldEqual StatusCodes.NotFound
    }
  }

  it should "return sample process details" in {
    saveProcess(processId, ProcessTestData.validProcess) {
      Get(s"/processes/$processId") ~> routWithAllPermissions ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] should include(processId)
      }
    }
  }

  it should "return 400 when trying to update json of custom process" in {
    whenReady(writeProcessRepository.saveNewProcess("customProcess", testCategory, CustomProcess(""), ProcessingType.Streaming, false)) { res =>
      updateProcess("customProcess", SampleProcess.process) {
        status shouldEqual StatusCodes.BadRequest
      }
    }
  }
  it should "save correct process json with ok status" in {
    saveProcess(SampleProcess.process.id, ProcessTestData.validProcess) {
      status shouldEqual StatusCodes.OK
      checkSampleProcessRootIdEquals(ProcessTestData.validProcess.root.id)
      val json = entityAs[String].parseOption.value
      json.field("errors").flatMap(_.field("invalidNodes")).flatMap(_.obj).value.isEmpty shouldBe true
    }
  }

  it should "save invalid process json with ok status but with non empty invalid nodes" in {
    saveProcess(SampleProcess.process.id, ProcessTestData.invalidProcess) {
      status shouldEqual StatusCodes.OK
      checkSampleProcessRootIdEquals(ProcessTestData.invalidProcess.root.id)
      val json = entityAs[String].parseOption.value
      json.field("errors").flatMap(_.field("invalidNodes")).flatMap(_.obj).value.isEmpty shouldBe false
    }
  }

  it should "return one latest version for process" in {
    saveProcess(SampleProcess.process.id, ProcessTestData.validProcess) {
      status shouldEqual StatusCodes.OK
    }
    updateProcess(SampleProcess.process.id, ProcessTestData.invalidProcess) {
      status shouldEqual StatusCodes.OK
    }

    Get("/processes") ~> routWithAllPermissions ~> check {
      status shouldEqual StatusCodes.OK
      val resp = responseAs[String].decodeOption[List[ProcessDetails]].get
      withClue(resp) {
        resp.count(_.id == SampleProcess.process.id) shouldBe 1
      }
    }
  }

  it should "return process if user has category" in {
    saveProcess(SampleProcess.process.id, ProcessTestData.validProcess) {
      status shouldEqual StatusCodes.OK
    }
    writeProcessRepository.updateCategory(SampleProcess.process.id, testCategory)

    Get(s"/processes/${SampleProcess.process.id}") ~> routWithAllPermissions ~> check {
      val processDetails = responseAs[String].decodeOption[ProcessDetails].get
      processDetails.processCategory shouldBe testCategory
    }

    Get(s"/processes") ~> routeWithRead ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[String] should include(SampleProcess.process.id)
    }

  }

  it should "not return processes not in user categories" in {
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

  it should "return all processes for admin user" in {
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

  it should "save process history" in {
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

  it should "access process version and mark latest version" in {
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

  it should "perform idempotent process save" in {
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

  it should "not authorize user with read permissions to modify node" in {
    Put(s"/processes/$testCategory/$processId", posting.toEntityAsProcessToSave(ProcessTestData.validProcess)) ~> routeWithRead ~> check {
      rejection shouldBe server.AuthorizationFailedRejection
    }

    val modifiedParallelism = 123
    val modifiedName = "fooBarName"
    val props = ProcessProperties(StreamMetaData(Some(modifiedParallelism)),
      ExceptionHandlerRef(List()), false, None, subprocessVersions = Map.empty)
    Put(s"/processes/$testCategory/$processId/json/properties", posting.toEntity(props)) ~> routeWithRead ~> check {
      rejection shouldBe server.AuthorizationFailedRejection
    }

  }

  it should "save displayable process" in {
    val processToSave = ProcessTestData.sampleDisplayableProcess
    saveProcess(processToSave) {
      status shouldEqual StatusCodes.OK
    }

    Get(s"/processes/${processToSave.id}") ~> routWithAllPermissions ~> check {
      val processDetails = responseAs[String].decodeOption[ProcessDetails].get
      processDetails.json.get shouldBe processToSave
    }
  }

  it should "delete process" in {
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

  it should "save new process with empty json" in {
    val newProcessId = "tst1"
    Post(s"/processes/$newProcessId/$testCategory?isSubprocess=false") ~> routWithAdminPermission ~> check {
      status shouldEqual StatusCodes.Created

      Get(s"/processes/$newProcessId") ~> routWithAdminPermission ~> check {
        status shouldEqual StatusCodes.OK
        val loadedProcess = responseAs[String].decodeOption[ProcessDetails].get
        loadedProcess.processCategory shouldBe testCategory
      }
    }
  }

  it should "not allow to save process if already exists" in {
    val processToSave = ProcessTestData.sampleDisplayableProcess
    saveProcess(processToSave) {
      status shouldEqual StatusCodes.OK
      Post(s"/processes/${processToSave.id}/$testCategory?isSubprocess=false") ~> routWithAdminPermission ~> check {
        status shouldEqual StatusCodes.BadRequest

      }
    }
  }

  it should "not allow to save process with category not allowed for user" in {
    Post(s"/processes/p11/abcd/${ProcessingType.Streaming}") ~> routWithAdminPermission ~> check {
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