package pl.touk.esp.ui.api

import java.time.LocalDateTime

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import argonaut.Argonaut._
import argonaut.{DecodeJson, EncodeJson, Json}
import org.scalatest._
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Millis, Seconds, Span}
import pl.touk.esp.engine.api.deployment._
import pl.touk.esp.engine.canonicalgraph.CanonicalProcess
import pl.touk.esp.engine.graph.EspProcess
import pl.touk.esp.engine.canonicalgraph.CanonicalProcess
import pl.touk.esp.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.esp.engine.graph.node.Sink
import pl.touk.esp.engine.graph.param.Parameter
import pl.touk.esp.engine.marshall.ProcessMarshaller
import pl.touk.esp.ui.api.helpers.DbTesting
import pl.touk.esp.ui.api.helpers.TestFactory._
import pl.touk.esp.ui.process.displayedgraph.{DisplayableProcess, ProcessProperties}
import pl.touk.esp.ui.process.marshall._
import pl.touk.esp.ui.process.repository.ProcessRepository.ProcessDetails
import pl.touk.esp.ui.sample.SampleProcess
import pl.touk.esp.ui.security.{LoggedUser, Permission}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.higherKinds

class ProcessesResourcesSpec extends FlatSpec with ScalatestRouteTest with Matchers with Inside
  with ScalaFutures with OptionValues with Eventually {

  val db = DbTesting.db
  implicit override val patienceConfig = PatienceConfig(timeout = scaled(Span(1, Seconds)), interval = scaled(Span(100, Millis)))

  import argonaut.ArgonautShapeless._
  implicit val decoder = DisplayableProcessCodec.codec
  implicit val processTypeCodec = ProcessTypeCodec.codec

  implicit val localDateTimeEncode = EncodeJson.of[String].contramap[LocalDateTime](_.toString)
  implicit val localDateTimeDecode = DecodeJson.of[String].map[LocalDateTime](s => LocalDateTime.parse(s))

  implicit val processListEncode = DecodeJson.of[ProcessDetails]
  implicit val testtimeout = RouteTestTimeout(2.seconds)


  import pl.touk.esp.engine.spel.Implicits._

  val processRepository = newProcessRepository(db)

  val route = new ProcessesResources(processRepository, InMemoryMocks.mockProcessManager,
    processConverter, processValidation).route

  val routeWithRead = withPermissions(route, Permission.Read)
  val routeWithWrite = withPermissions(route, Permission.Write)
  val routWithAllPermissions = withAllPermissions(route)

  private val processId: String = SampleProcess.process.id

  it should "return list of process details" in {
    Get("/processes") ~> routWithAllPermissions ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[String] should include (processId)
    }
  }

  it should "return 404 when no process" in {
    Get("/processes/123") ~> routWithAllPermissions ~> check {
      status shouldEqual StatusCodes.NotFound
    }
  }

  it should "return sample process details" in {
    Get(s"/processes/$processId") ~> routWithAllPermissions ~> check {
      status shouldEqual StatusCodes.OK
      responseAs[String] should include (processId)
    }
  }

  it should "return 400 when trying to update json of custom process" in {
    whenReady(processRepository.saveProcess("customProcess", CustomProcess(""), "")) { _ =>
      saveProcess("customProcess", SampleProcess.process) {
        status shouldEqual StatusCodes.BadRequest
      }
    }
  }

  it should "save correct process json with ok status" in {
    saveProcess(SampleProcess.process.id, ValidationTestData.validProcess) {
      status shouldEqual StatusCodes.OK
      checkSampleProcessRootIdEquals(ValidationTestData.validProcess.root.id)
      val json = entityAs[String].parseOption.value
      json.field("invalidNodes").flatMap(_.obj).value.isEmpty shouldBe true
    }
  }

  it should "save invalid process json with ok status but with non empty invalid nodes" in {
    saveProcess(SampleProcess.process.id, ValidationTestData.invalidProcess) {
      status shouldEqual StatusCodes.OK
      checkSampleProcessRootIdEquals(ValidationTestData.invalidProcess.root.id)
      val json = entityAs[String].parseOption.value
      json.field("invalidNodes").flatMap(_.obj).value.isEmpty shouldBe false
    }
  }

  it should "return one latest version for process" in {
    saveProcess(SampleProcess.process.id, ValidationTestData.validProcess) { status shouldEqual StatusCodes.OK}
    saveProcess(SampleProcess.process.id, ValidationTestData.invalidProcess) { status shouldEqual StatusCodes.OK}

    Get("/processes") ~> routWithAllPermissions ~> check {
      status shouldEqual StatusCodes.OK
      val resp = responseAs[String].decodeOption[List[ProcessDetails]].get
      withClue(resp) {
        resp.count(_.id == SampleProcess.process.id) shouldBe 1
      }
    }
  }

  it should "save process history" in {
    saveProcess(SampleProcess.process.id, ValidationTestData.validProcess) {
      status shouldEqual StatusCodes.OK
    }
    saveProcess(SampleProcess.process.id, ValidationTestData.validProcess.copy(root = ValidationTestData.validProcess.root.copy(data = ValidationTestData.validProcess.root.data.copy(id = "AARGH")))) {
      status shouldEqual StatusCodes.OK
    }
    Get(s"/processes/${SampleProcess.process.id}") ~> routWithAllPermissions ~> check {
      val processDetails = responseAs[String].decodeOption[ProcessDetails].get
      processDetails.name shouldBe SampleProcess.process.id
      processDetails.history.length should be >= 2 //fixme a da sie czyscic baze co kazdy test?
      processDetails.history.forall(_.processName == SampleProcess.process.id) shouldBe true
    }
  }

  it should "perform idempotent process save" in {
    saveSampleProcess()
    Get(s"/processes/${SampleProcess.process.id}") ~> routWithAllPermissions ~> check {
      val processHistoryBeforeDuplicatedWrite = responseAs[String].decodeOption[ProcessDetails].get.history
      saveSampleProcess()
      Get(s"/processes/${SampleProcess.process.id}") ~> routWithAllPermissions ~> check {
        val processHistoryAfterDuplicatedWrite = responseAs[String].decodeOption[ProcessDetails].get.history
        processHistoryAfterDuplicatedWrite shouldBe processHistoryBeforeDuplicatedWrite
      }
    }
  }

  it should "not authorize user with read permissions to modify node" in {
    Put(s"/processes/$processId/json", posting.toEntity(ValidationTestData.validProcess)) ~> routeWithRead ~> check {
      rejection shouldBe server.AuthorizationFailedRejection
    }

    val modifiedParallelism = 123
    val modifiedName = "fooBarName"
    val props = ProcessProperties(Some(modifiedParallelism), ExceptionHandlerRef(List(Parameter(modifiedName, modifiedName))))
    Put(s"/processes/$processId/json/properties", posting.toEntity(props)) ~> routeWithRead ~> check {
      rejection shouldBe server.AuthorizationFailedRejection
    }

  }

  def saveSampleProcess() = {
    saveProcess(SampleProcess.process.id, ValidationTestData.validProcess) { status shouldEqual StatusCodes.OK}
  }

  def saveProcess(processId: String, process: EspProcess)(testCode: => Assertion) = {
    Put(s"/processes/${processId}/json", posting.toEntity(process)) ~> routWithAllPermissions ~> check { testCode }
  }

  def checkSampleProcessRootIdEquals(expected: String) = {
    fetchSampleProcess()
      .map(_.nodes.head.id)
      .futureValue shouldEqual expected
  }

  def fetchSampleProcess(): Future[CanonicalProcess] = {
    processRepository
      .fetchLatestProcessVersion(SampleProcess.process.id)
      .map(_.getOrElse(sys.error("Sample process missing")))
      .map(_.deploymentData)
      .mapTo[GraphProcess]
      .map(p => ProcessMarshaller.fromJson(p.processAsJson).valueOr(_ => sys.error("Invalid process json")))
  }
}