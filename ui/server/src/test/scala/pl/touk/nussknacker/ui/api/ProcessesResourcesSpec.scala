package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.{ContentTypeRange, StatusCodes}
import akka.http.scaladsl.server
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import org.scalatest._
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Millis, Seconds, Span}
import pl.touk.nussknacker.engine.api.StreamMetaData
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.graph.node.Source
import pl.touk.nussknacker.ui.api.helpers._
import pl.touk.nussknacker.ui.api.helpers.TestFactory._
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ProcessProperties}
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.restmodel.processdetails.{BasicProcess, ProcessDetails}

import scala.concurrent.Future
import scala.language.higherKinds
import pl.touk.nussknacker.ui.security.api.LoggedUser
import cats.instances.all._
import cats.syntax.semigroup._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.restmodel.process.ProcessId
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationResult

class ProcessesResourcesSpec extends FunSuite with ScalatestRouteTest with Matchers with Inside with FailFastCirceSupport
  with ScalaFutures with OptionValues with Eventually with BeforeAndAfterEach with BeforeAndAfterAll with EspItTest {

  implicit override val patienceConfig = PatienceConfig(timeout = scaled(Span(1, Seconds)), interval = scaled(Span(100, Millis)))
  private implicit final val string: FromEntityUnmarshaller[String] = Unmarshaller.stringUnmarshaller.forContentTypes(ContentTypeRange.*)

  val routeWithRead = withPermissions(processesRoute, testPermissionRead)
  val routeWithWrite = withPermissions(processesRoute, testPermissionWrite)
  val routeWithAllPermissions = withAllPermissions(processesRoute)
  val routeWithAdminPermissions = withAdminPermissions(processesRoute)
  val processActivityRouteWithAllPermission = withAllPermissions(processActivityRoute)
  implicit val loggedUser = LoggedUser("lu", testCategory)

  private val processName = ProcessName(SampleProcess.process.id)

  test("return list of process") {
    saveProcess(processName, ProcessTestData.validProcess) {
      Get("/processes") ~> routeWithAllPermissions ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] should include(processName.value)
      }
    }
  }

  ignore("provie more information about excisting process" ) {
    fail()
  }
  ignore("not allow to archive still used subprocess") {
    val processWithSubreocess = ProcessTestData.validProcessWithSubprocess(processName)
    val displayableSubprocess = ProcessConverter.toDisplayable(processWithSubreocess.subprocess, TestProcessingTypes.Streaming)
    saveSubProcess(displayableSubprocess)(succeed)
    saveProcess(processName, processWithSubreocess.process)(succeed)
    archiveProcess(ProcessName(displayableSubprocess.id)) ~> routeWithAllPermissions ~> check {
      status shouldEqual StatusCodes.Conflict
      responseAs[List[String]] shouldEqual List(processName) // returns list of porcesses using subprocess
    }
  }
  test("allow to archive subprocess used in archived process") {
    val processWithSubreocess = ProcessTestData.validProcessWithSubprocess(processName)
    val displayableSubprocess = ProcessConverter.toDisplayable(processWithSubreocess.subprocess, TestProcessingTypes.Streaming)
    saveSubProcess(displayableSubprocess)(succeed)
    saveProcess(processName, processWithSubreocess.process)(succeed)
    archiveProcess(processName)~> routeWithAllPermissions ~> check {
      status shouldEqual StatusCodes.OK
    }
    archiveProcess(ProcessName(displayableSubprocess.id))~> routeWithAllPermissions ~> check {
      status shouldEqual StatusCodes.OK
    }
  }
  test("or not allow to create new process named as archived one") {
    val process = ProcessTestData.validProcess
    saveProcess(processName, process)(succeed)

    archiveProcess(processName)~> routeWithAllPermissions ~> check {
      status shouldEqual StatusCodes.OK
    }
    Post(s"/processes/${processName.value}/$testCategoryName?isSubprocess=false") ~> processesRouteWithAllPermissions ~> check {
      status shouldBe StatusCodes.BadRequest
      responseAs[String] shouldEqual s"Process ${processName.value} already exists"
    }
  }
  test("return list of subprocess without archived process") {
    val sampleSubprocess = ProcessConverter.toDisplayable(ProcessTestData.sampleSubprocess, TestProcessingTypes.Streaming)
    saveSubProcess(sampleSubprocess) {
      status shouldEqual StatusCodes.OK
    }

    archiveProcess(ProcessName(sampleSubprocess.id))~> routeWithAllPermissions ~> check {
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
    val process = ProcessTestData.validProcess
    saveProcess(processName, process)(succeed)

    archiveProcess(processName) ~> routeWithAllPermissions ~> check {
      status shouldEqual StatusCodes.OK
    }
    updateProcess(processName, process)  {
      status shouldEqual StatusCodes.Forbidden
    }
  }
  test("return list of process without archived process") {
    val process = ProcessTestData.validProcess
    saveProcess(processName, process) {
      status shouldEqual StatusCodes.OK
    }

    archiveProcess(processName) ~> routeWithAllPermissions ~> check {
      status shouldEqual StatusCodes.OK
    }
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

    archiveProcess(processName) ~> routeWithAllPermissions ~> check {
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
  test("update process category for existing process") {
    saveProcess(processName, ProcessTestData.validProcess) {
      val newCategory = "expectedCategory"
      Post(s"/processes/category/${processName.value}/$newCategory") ~> routeWithAdminPermissions ~> check {
        status shouldEqual StatusCodes.OK
        Get(s"/processes/${processName.value}") ~> routeWithAdminPermissions ~> check {
          status shouldEqual StatusCodes.OK
          val loadedProcess = responseAs[ProcessDetails]
          loadedProcess.processCategory shouldBe newCategory
        }
      }
    }
  }

  test("search processes by categories") {
    val firstProcessor = ProcessName("Processor1")
    val secondProcessor = ProcessName("Processor2")

    createProcess(firstProcessor, testCategoryName, false)
    createProcess(secondProcessor, secondTestCategoryName, false)

    Get(s"/processes") ~> routeWithAllPermissions ~> check {
      status shouldEqual StatusCodes.OK
      val data = responseAs[List[BasicProcess]]
      data.size shouldBe 2
    }

    Get(s"/processes?categories=$testCategoryName") ~> routeWithAllPermissions ~> check {
      status shouldEqual StatusCodes.OK
      val data =responseAs[List[BasicProcess]]
      data.size shouldBe 1
    }

    Get(s"/processes?categories=$secondTestCategoryName") ~> routeWithAllPermissions ~> check {
      status shouldEqual StatusCodes.OK
      val data =responseAs[List[BasicProcess]]
      data.size shouldBe 1
    }

    Get(s"/processes?categories=$secondTestCategoryName,$testCategoryName") ~> routeWithAllPermissions ~> check {
      status shouldEqual StatusCodes.OK
      val data =responseAs[List[BasicProcess]]
      data.size shouldBe 2
    }
  }

  test("search processes by isDeployed") {
    val firstProcessor = ProcessName("Processor1")
    val secondProcessor = ProcessName("Processor2")
    val thirdProcessor = ProcessName("Processor3")

    createProcess(firstProcessor, testCategoryName, false)
    createProcess(secondProcessor, testCategoryName, false)
    createDeployedProcess(thirdProcessor, testCategoryName, false)

    Get(s"/processes") ~> routeWithAllPermissions ~> check {
      status shouldEqual StatusCodes.OK
      val data =responseAs[List[BasicProcess]]
      data.size shouldBe 3
    }

    Get(s"/processes?isDeployed=true") ~> routeWithAllPermissions ~> check {
      status shouldEqual StatusCodes.OK
      val data =responseAs[List[BasicProcess]]
      data.map{proc => proc.name}.contains(thirdProcessor.value) shouldBe true
      data.size shouldBe 1
    }

    Get(s"/processes?isDeployed=false") ~> routeWithAllPermissions ~> check {
      status shouldEqual StatusCodes.OK
      val data =responseAs[List[BasicProcess]]
      data.map{proc => proc.name}.contains(thirdProcessor.value) shouldBe false
      data.size shouldBe 2
    }
  }

  test("return 404 on update process category for non existing process") {
    Post("/processes/category/unexcistingProcess/newCategory") ~> routeWithAllPermissions ~> check {
      status shouldBe StatusCodes.NotFound
    }
  }

  test("return 404 when no process") {
    Get("/processes/123") ~> routeWithAllPermissions ~> check {
      status shouldEqual StatusCodes.NotFound
    }
  }

  test("return sample process details") {
    saveProcess(processName, ProcessTestData.validProcess) {
      Get(s"/processes/${processName.value}") ~> routeWithAllPermissions ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] should include(processName.value)
      }
    }
  }
  //FIXME: test gets rejection. Permission verification doesn't work for custom processes
  ignore("return 400 when trying to update json of custom process") {
    whenReady(writeProcessRepository.saveNewProcess(processName, testCategoryName, CustomProcess(""), TestProcessingTypes.Streaming, false)) { res =>
      updateProcess(processName, SampleProcess.process) {
        status shouldEqual StatusCodes.BadRequest
      }
    }
  }
  test("save correct process json with ok status") {
    saveProcess(processName, ProcessTestData.validProcess) {
      status shouldEqual StatusCodes.OK
      checkSampleProcessRootIdEquals(ProcessTestData.validProcess.roots.head.id)
      entityAs[ValidationResult].errors.invalidNodes.isEmpty shouldBe true

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
      val resp =responseAs[List[BasicProcess]]
      withClue(resp) {
        resp.count(_.name == SampleProcess.process.id) shouldBe 1
      }
    }
  }

  test("return process if user has category") {
    saveProcess(processName, ProcessTestData.validProcess) {
      status shouldEqual StatusCodes.OK
    }
    writeProcessRepository.updateCategory(getProcessId(processName), testCategoryName)

    Get(s"/processes/${SampleProcess.process.id}") ~> routeWithAllPermissions ~> check {
      val processDetails = responseAs[ProcessDetails]
      processDetails.processCategory shouldBe testCategoryName
    }

    Get(s"/processes") ~> routeWithRead ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[String] should include(SampleProcess.process.id)
    }

  }

  test("not return processes not in user categories") {
    saveProcess(processName, ProcessTestData.validProcess) {
      status shouldEqual StatusCodes.OK
    }
    writeProcessRepository.updateCategory(getProcessId(processName), "newCategory")
    Get(s"/processes/${SampleProcess.process.id}") ~> routeWithRead ~> check {
      status shouldEqual StatusCodes.NotFound
    }

    Get(s"/processes") ~> routeWithRead ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[String] shouldBe "[]"
    }
  }

  //@TODO: Tests for checking validity bad category name
  test("return all processes for admin user") {
    saveProcess(processName, ProcessTestData.validProcess) {
      status shouldEqual StatusCodes.OK
    }
    writeProcessRepository.updateCategory(getProcessId(processName), "newCategory")

    Get(s"/processes/${SampleProcess.process.id}") ~> routeWithAdminPermissions ~> check {
      val processDetails = responseAs[ProcessDetails]
      processDetails.processCategory shouldBe "newCategory"
    }

    Get(s"/processes") ~> routeWithAdminPermissions ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[String] should include(SampleProcess.process.id)
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
      processDetails.history.forall(_.processName == SampleProcess.process.id) shouldBe true
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
    val processToSave = ProcessTestData.sampleDisplayableProcess
    val id = processToSave.id

    saveProcess(processToSave) {
      status shouldEqual StatusCodes.OK
    }
    archiveProcess(ProcessName(id)) ~> routeWithAllPermissions ~> check {
      status shouldEqual StatusCodes.OK
    }
    Get(s"/processes/$id") ~> routeWithAllPermissions ~> check {
      status shouldEqual StatusCodes.OK
      val loadedProcess = responseAs[ProcessDetails]
      loadedProcess.isArchived shouldEqual true
    }
  }

  test("unarchive process") {
    val processToSave = ProcessTestData.sampleDisplayableProcess
    val id = processToSave.id

    saveProcess(processToSave) {
      status shouldEqual StatusCodes.OK
    }
    archiveProcess(ProcessName(id)) ~> routeWithAllPermissions ~> check {
      status shouldEqual StatusCodes.OK
    }
    Post(s"/unarchive/$id") ~> routeWithAllPermissions ~> check {
      status shouldEqual StatusCodes.OK
    }
    Get(s"/processes/$id") ~> routeWithAllPermissions ~> check {
      status shouldEqual StatusCodes.OK
      val loadedProcess = responseAs[ProcessDetails]
      loadedProcess.isArchived shouldEqual false
    }
  }

  private def archiveProcess(processName: ProcessName) = {
    Post(s"/archive/${processName.value}")
  }

  test("delete process") {
    val processToSave = ProcessTestData.sampleDisplayableProcess
    val id = processToSave.id
    saveProcess(processToSave) {
      status shouldEqual StatusCodes.OK
    }

    Delete(s"/processes/$id") ~> routeWithAllPermissions ~> check {
      Get(s"/processes/$id") ~> routeWithAllPermissions ~> check {
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
        val loadedProcess = responseAs[ProcessDetails]
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
          println(responseAs[String])
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

  private def checkSampleProcessRootIdEquals(expected: String): Assertion = {
    fetchSampleProcess()
      .map(_.nodes.head.id)
      .futureValue shouldEqual expected
  }

  private def fetchSampleProcess(): Future[CanonicalProcess] = {
    processRepository
      .fetchLatestProcessVersion[DisplayableProcess](getProcessId(processName))
      .map(_.getOrElse(sys.error("Sample process missing")))
      .map { version =>
        val parsed = ProcessMarshaller.fromJson(version.json.get)
        parsed.valueOr(_ => sys.error("Invalid process json"))
      }
  }

  private def getProcessId(processName: ProcessName): ProcessId =
    processRepository.fetchProcessId(processName).futureValue.get
}