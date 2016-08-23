package pl.touk.esp.ui.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import argonaut.Argonaut._
import db.migration.DefaultJdbcDriver
import org.scalatest._
import pl.touk.esp.engine.management.{JobState, ProcessManager}
import pl.touk.esp.ui.db.DatabaseInitializer
import pl.touk.esp.ui.process.displayedgraph.DisplayableProcess
import pl.touk.esp.ui.process.repository.ProcessRepository
import pl.touk.esp.ui.sample.SampleProcess
import pl.touk.esp.ui.process.marshall._
import slick.jdbc.JdbcBackend

import scala.concurrent.Future

class ProcessesResourcesSpec extends FlatSpec with ScalatestRouteTest with Matchers with Inside {

  val db: JdbcBackend.Database = {
    val db = JdbcBackend.Database.forURL(
      url = s"jdbc:hsqldb:mem:esp",
      driver = "org.hsqldb.jdbc.JDBCDriver",
      user = "SA",
      password = ""
    )
    new DatabaseInitializer(db).initDatabase()
    db
  }

  val processRepository = new ProcessRepository(db, DefaultJdbcDriver.driver)
  val mockProcessManager = new ProcessManager {
    override def findJobStatus(name: String): Future[Option[JobState]] = Future.successful(None)
    override def cancel(name: String): Future[Unit] = Future.successful(Unit)
    override def deploy(processId: String, processAsJson: String): Future[Unit] = Future.successful(Unit)
  }

  val route = new ProcessesResources(processRepository, mockProcessManager).route

  it should "return list of process details" in {
    Get("/processes") ~> route ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[String] should include (SampleProcess.process.id)
    }
  }

  it should "return 404 when no process" in {
    Get("/processes/123") ~> route ~> check {
      status shouldEqual StatusCodes.NotFound
    }
  }

  it should "return sample process details" in {
    Get(s"/processes/${SampleProcess.process.id}") ~> route ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[String] should include (SampleProcess.process.id)
    }
  }

  it should "return sample process json" in {
    Get(s"/processes/${SampleProcess.process.id}/json") ~> route ~> check {
      status shouldEqual StatusCodes.OK
      implicit val decoder =  DisplayableProcessCodec.decoder
      inside(responseAs[String].decodeEither[DisplayableProcess]) {
        case Right(_) =>
      }
    }
  }

}
