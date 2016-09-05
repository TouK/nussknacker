package pl.touk.esp.ui.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import argonaut.Argonaut._
import db.migration.DefaultJdbcProfile
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import pl.touk.esp.engine.management.{JobState, ProcessManager}
import pl.touk.esp.engine.marshall.ProcessMarshaller
import pl.touk.esp.ui.db.DatabaseInitializer
import pl.touk.esp.ui.process.displayedgraph.DisplayableProcess
import pl.touk.esp.ui.process.marshall._
import pl.touk.esp.ui.process.repository.ProcessRepository
import pl.touk.esp.ui.sample.SampleProcess
import slick.jdbc.JdbcBackend

import scala.concurrent.Future
import scala.language.higherKinds

class ProcessesResourcesSpec extends FlatSpec with ScalatestRouteTest with Matchers with Inside
  with ScalaFutures with OptionValues with ProcessPosting {

  implicit override val patienceConfig = PatienceConfig(timeout = scaled(Span(1, Seconds)), interval = scaled(Span(100, Millis)))

  implicit val decoder =  DisplayableProcessCodec.decoder

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

  val processRepository = new ProcessRepository(db, DefaultJdbcProfile.profile)
  val mockProcessManager = new ProcessManager {
    override def findJobStatus(name: String): Future[Option[JobState]] = Future.successful(None)
    override def cancel(name: String): Future[Unit] = Future.successful(Unit)
    override def deploy(processId: String, processAsJson: String): Future[Unit] = Future.successful(Unit)
  }


  val route = new ProcessesResources(processRepository, mockProcessManager, ValidationTestData.validator).route

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
      inside(responseAs[String].decodeEither[DisplayableProcess]) {
        case Right(_) =>
      }
    }
  }

  it should "return 404 when trying to update json of non existing process" in {
    Put(s"/processes/missing_id/json", toEntity(SampleProcess.process)) ~> route ~> check {
      status shouldEqual StatusCodes.NotFound
    }
  }

  it should "save correct process json with ok status" in {
    Put(s"/processes/${SampleProcess.process.id}/json", toEntity(ValidationTestData.validProcess)) ~> route ~> check {
      status shouldEqual StatusCodes.OK
      checkSampleProcessRootIdEquals(ValidationTestData.validProcess.root.id)
      val json = entityAs[String].parseOption.value
      json.field("invalidNodes").flatMap(_.obj).value.isEmpty shouldBe true
    }
  }

  it should "save invalid process json with ok status but with non empty invalid nodes" in {
    Put(s"/processes/${SampleProcess.process.id}/json", toEntity(ValidationTestData.invalidProcess)) ~> route ~> check {
      status shouldEqual StatusCodes.OK
      checkSampleProcessRootIdEquals(ValidationTestData.invalidProcess.root.id)
      val json = entityAs[String].parseOption.value
      json.field("invalidNodes").flatMap(_.obj).value.isEmpty shouldBe false
    }
  }

  def checkSampleProcessRootIdEquals(expected: String) = {
    processRepository
      .fetchProcessJsonById(SampleProcess.process.id)
      .map(_.getOrElse(sys.error("Sample process missing")))
      .map(ProcessMarshaller.fromJson(_).valueOr(_ => sys.error("Invalid process json")))
      .map(_.nodes.head.id)
      .futureValue shouldEqual expected
  }

}