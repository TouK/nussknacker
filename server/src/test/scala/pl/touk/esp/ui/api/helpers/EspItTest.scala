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
import pl.touk.esp.ui.api.{ManagementResources, ProcessesResources}
import pl.touk.esp.ui.db.EspTables
import pl.touk.esp.ui.db.migration.SampleData
import pl.touk.esp.ui.process.marshall.{DisplayableProcessCodec, ProcessTypeCodec}
import pl.touk.esp.ui.process.repository.ProcessRepository.ProcessDetails

import scala.concurrent.Await
import scala.concurrent.duration.Duration

trait EspItTest extends LazyLogging { self: ScalatestRouteTest with Suite with BeforeAndAfterEach with BeforeAndAfterAll with Matchers =>

  import argonaut.ArgonautShapeless._
  implicit val decoder = DisplayableProcessCodec.codec
  implicit val processTypeCodec = ProcessTypeCodec.codec
  implicit val localDateTimeEncode = EncodeJson.of[String].contramap[LocalDateTime](_.toString)
  implicit val localDateTimeDecode = DecodeJson.of[String].map[LocalDateTime](s => LocalDateTime.parse(s))
  implicit val processListEncode = DecodeJson.of[ProcessDetails]

  val env = "test"
  val db = DbTesting.db

  val processRepository = newProcessRepository(db)
  val deploymentProcessRepository = newDeploymentProcessRepository(db)

  val processesRoute = new ProcessesResources(processRepository, InMemoryMocks.mockProcessManager, processConverter, processValidation).route
  val processesRouteWithAllPermissions = withAllPermissions(processesRoute)
  val deployRoute = new ManagementResources(processRepository, deploymentProcessRepository, InMemoryMocks.mockProcessManager, env).route

  def saveProcess(processId: String, process: EspProcess)(testCode: => Assertion): Assertion = {
    Put(s"/processes/${processId}/json", TestFactory.posting.toEntity(process)) ~> processesRouteWithAllPermissions ~> check { testCode }
  }

  def saveProcessAndAssertSuccess(processId: String, process: EspProcess): Assertion = {
    saveProcess(processId, process) {
      status shouldEqual StatusCodes.OK
    }
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
