package pl.touk.esp.ui.api.helpers

import java.time.LocalDateTime

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import argonaut.{DecodeJson, EncodeJson}
import com.typesafe.scalalogging.LazyLogging
import db.migration.DefaultJdbcProfile
import org.scalatest._
import pl.touk.esp.engine.graph.EspProcess
import pl.touk.esp.ui.api.helpers.TestFactory._
import pl.touk.esp.ui.api._
import pl.touk.esp.ui.codec.{ProcessTypeCodec, UiCodecs}
import pl.touk.esp.ui.db.EspTables
import pl.touk.esp.ui.db.migration.SampleData
import pl.touk.esp.ui.process.deployment.ManagementActor
import pl.touk.esp.ui.process.displayedgraph.DisplayableProcess
import pl.touk.esp.ui.process.repository.ProcessRepository.ProcessDetails
import pl.touk.esp.ui.sample.SampleProcess
import pl.touk.esp.ui.security.{LoggedUser, Permission}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

trait EspItTest extends LazyLogging { self: ScalatestRouteTest with Suite with BeforeAndAfterEach with Matchers =>

  import argonaut.ArgonautShapeless._
  implicit val decoder = UiCodecs.codec
  implicit val processTypeCodec = ProcessTypeCodec.codec
  implicit val localDateTimeEncode = EncodeJson.of[String].contramap[LocalDateTime](_.toString)
  implicit val localDateTimeDecode = DecodeJson.of[String].map[LocalDateTime](s => LocalDateTime.parse(s))
  implicit val processListEncode = DecodeJson.of[ProcessDetails]

  val env = "test"
  val db = DbTesting.db
  val attachmentsPath = "/tmp/attachments" + System.currentTimeMillis()

  val processRepository = newProcessRepository(db)
  val deploymentProcessRepository = newDeploymentProcessRepository(db)
  val processActivityRepository = newProcessActivityRepository(db)

  val managementActor = ManagementActor(env, InMemoryMocks.mockProcessManager, processRepository, deploymentProcessRepository)
  val processesRoute = (u:LoggedUser) => new ProcessesResources(processRepository, managementActor, processConverter, processValidation).route(u)

  val processesRouteWithAllPermissions = withAllPermissions(processesRoute)
  val deployRoute = (u:LoggedUser) =>  new ManagementResources(InMemoryMocks.mockProcessManager.getProcessDefinition.typesInformation, managementActor).route(u)
  val attachmentService = new ProcessAttachmentService(attachmentsPath, processActivityRepository)
  val processActivityRoute = (u:LoggedUser) =>  new ProcessActivityResource(processActivityRepository, attachmentService).route(u)

  def saveProcess(processId: String, process: EspProcess)(testCode: => Assertion): Assertion = {
    Post(s"/processes/$processId/$testCategory") ~> processesRouteWithAllPermissions ~> check {
      status shouldBe StatusCodes.Created
      updateProcess(processId, process)(testCode)
    }
  }

  def saveProcess(process: DisplayableProcess)(testCode: => Assertion): Assertion = {
    val processId = process.id
    Post(s"/processes/$processId/$testCategory") ~> processesRouteWithAllPermissions ~> check {
      status shouldBe StatusCodes.Created
      updateProcess(process)(testCode)
    }
  }

  def updateProcess(process: DisplayableProcess)(testCode: => Assertion): Assertion = {
    val processId = process.id
    Put(s"/processes/$processId/json", TestFactory.posting.toEntity(process)) ~> processesRouteWithAllPermissions ~> check {
      testCode
    }
  }

  def updateProcess(processId: String, process: EspProcess)(testCode: => Assertion): Assertion = {
    Put(s"/processes/$processId/json", TestFactory.posting.toEntity(process)) ~> processesRouteWithAllPermissions ~> check {
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

  override protected def beforeEach(): Unit = {
    import DefaultJdbcProfile.profile.api._
    Await.ready(db.run(EspTables.environmentsTable += SampleData.environment), Duration.Inf)
  }

  override protected def afterEach(): Unit = {
    DbTesting.cleanDB().failed.foreach { e =>
      throw new InternalError("Error during cleaning test resources", e) //InternalError bo scalatest polyka inne wyjatki w afterEach
    }
  }
}
