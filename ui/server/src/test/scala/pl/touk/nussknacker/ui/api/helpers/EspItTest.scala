package pl.touk.nussknacker.ui.api.helpers


import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.scalatest._
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.ui.api._
import pl.touk.nussknacker.ui.api.helpers.TestFactory._
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessingType
import pl.touk.nussknacker.ui.process.deployment.ManagementActor
import pl.touk.nussknacker.ui.process.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.ui.process.{JobStatusService, NewProcessPreparer, ProcessToSave, ProcessTypesForCategories}
import pl.touk.nussknacker.ui.processreport.ProcessCounter
import pl.touk.nussknacker.ui.sample.SampleProcess
import pl.touk.nussknacker.ui.security.api.Permission

trait EspItTest extends LazyLogging with WithDbTesting { self: ScalatestRouteTest with Suite with BeforeAndAfterEach with Matchers =>

  val env = "test"
  val attachmentsPath = "/tmp/attachments" + System.currentTimeMillis()

  val processRepository = newProcessRepository(db)
  val writeProcessRepository = newWriteProcessRepository(db)

  val subprocessRepository = newSubprocessRepository(db)
  val deploymentProcessRepository = newDeploymentProcessRepository(db)
  val processActivityRepository = newProcessActivityRepository(db)
  val typesForCategories = new ProcessTypesForCategories(ConfigFactory.load())

  val managementActor = ManagementActor(env,
    Map(ProcessingType.Streaming -> InMemoryMocks.mockProcessManager), processRepository, deploymentProcessRepository, TestFactory.sampleResolver)

  val jobStatusService = new JobStatusService(managementActor)
  val newProcessPreparer = new NewProcessPreparer(Map(ProcessingType.Streaming -> ProcessTestData.processDefinition))
  val processesRoute = new ProcessesResources(processRepository, writeProcessRepository, jobStatusService, processActivityRepository, processValidation, typesForCategories, newProcessPreparer)
  val processesExportResources = new ProcessesExportResources(processRepository, processActivityRepository)

  val processesRouteWithAllPermissions = withAllPermissions(processesRoute)

  val deployRoute = new ManagementResources(
    ProcessTestData.processDefinition.typesInformation, new ProcessCounter(TestFactory.sampleSubprocessRepository), managementActor)
  val attachmentService = new ProcessAttachmentService(attachmentsPath, processActivityRepository)
  val processActivityRoute = new ProcessActivityResource(processActivityRepository, attachmentService)

  def saveProcess(processId: String, process: EspProcess)(testCode: => Assertion): Assertion = {
    Post(s"/processes/$processId/$testCategory?isSubprocess=false") ~> processesRouteWithAllPermissions ~> check {
      status shouldBe StatusCodes.Created
      updateProcess(processId, process)(testCode)
    }
  }

  def saveProcess(process: DisplayableProcess)(testCode: => Assertion): Assertion = {
    val processId = process.id
    Post(s"/processes/$processId/$testCategory?isSubprocess=false") ~> processesRouteWithAllPermissions ~> check {
      status shouldBe StatusCodes.Created
      updateProcess(process)(testCode)
    }
  }

  def saveSubProcess(process: DisplayableProcess)(testCode: => Assertion): Assertion = {
    val processId = process.id
    Post(s"/processes/$processId/$testCategory?isSubprocess=true") ~> processesRouteWithAllPermissions ~> check {
      status shouldBe StatusCodes.Created
      updateProcess(process)(testCode)
    }
  }

  def updateProcess(process: DisplayableProcess)(testCode: => Assertion): Assertion = {
    val processId = process.id
    Put(s"/processes/$processId", TestFactory.posting.toEntityAsProcessToSave(process)) ~> processesRouteWithAllPermissions ~> check {
      testCode
    }
  }

  def updateProcess(process: ProcessToSave)(testCode: => Assertion): Assertion = {
    val processId = process.process.id
    Put(s"/processes/$processId", TestFactory.posting.toEntity(process)) ~> processesRouteWithAllPermissions ~> check {
      testCode
    }
  }

  def updateProcess(processId: String, process: EspProcess)(testCode: => Assertion): Assertion = {
    Put(s"/processes/$processId", TestFactory.posting.toEntityAsProcessToSave(process)) ~> processesRouteWithAllPermissions ~> check {
      testCode
    }
  }

  def saveProcessAndAssertSuccess(processId: String, process: EspProcess): Assertion = {
    saveProcess(processId, process) {
      status shouldEqual StatusCodes.OK
    }
  }

  def updateProcessAndAssertSuccess(processId: String, process: EspProcess): Assertion = {
    updateProcess(processId, process) {
      status shouldEqual StatusCodes.OK
    }
  }


  def deployProcess(id: String = SampleProcess.process.id) = {
    Post(s"/processManagement/deploy/$id") ~> withPermissions(deployRoute, Permission.Deploy)
  }

  def cancelProcess(id: String = SampleProcess.process.id) = {
    Post(s"/processManagement/cancel/$id") ~> withPermissions(deployRoute, Permission.Deploy)
  }

  def getSampleProcess = {
    Get(s"/processes/${SampleProcess.process.id}") ~> withPermissions(processesRoute, Permission.Read)
  }

  def getProcess(processId: String) = {
    Get(s"/processes/$processId") ~> withPermissions(processesRoute, Permission.Read)
  }

  def getProcesses = {
    Get(s"/processes") ~> withPermissions(processesRoute, Permission.Read)
  }

}
