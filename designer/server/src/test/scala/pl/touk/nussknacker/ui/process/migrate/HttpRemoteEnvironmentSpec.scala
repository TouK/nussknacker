package pl.touk.nussknacker.ui.process.migrate

import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport
import io.circe.syntax.EncoderOps
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.marshalling.Marshal
import org.apache.pekko.http.scaladsl.model.{
  ContentTypes,
  HttpEntity,
  HttpHeader,
  HttpMethod,
  HttpMethods,
  HttpResponse,
  MessageEntity,
  RequestEntity,
  StatusCode,
  StatusCodes,
  Uri
}
import org.apache.pekko.http.scaladsl.model.Uri.Query
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshaller
import org.apache.pekko.stream.Materializer
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.{ProcessName, VersionId}
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioWithDetailsForMigrations
import pl.touk.nussknacker.test.{EitherValuesDetailedMessage, PatientScalaFutures}
import pl.touk.nussknacker.test.utils.domain.ProcessTestData
import pl.touk.nussknacker.test.utils.domain.TestFactory.{flinkProcessValidator, mapProcessingTypeDataProvider}
import pl.touk.nussknacker.test.utils.domain.TestProcessUtil.wrapGraphWithScenarioDetailsEntity
import pl.touk.nussknacker.ui.api.description.scenarioActivity.Dtos.{ScenarioActivities, ScenarioActivity}
import pl.touk.nussknacker.ui.process.ScenarioWithDetailsConversions
import pl.touk.nussknacker.ui.process.VersionsWithDifferencesService.{VersionsWithDifferences, VersionWithDifference}
import pl.touk.nussknacker.ui.process.migrate.HttpRemoteEnvironmentSpec.MockRemoteEnvironment
import pl.touk.nussknacker.ui.security.api.{ImpersonatedUserData, ImpersonationSupported, LoggedUser, RealLoggedUser}

import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.{ExecutionContext, Future}

class HttpRemoteEnvironmentSpec
    extends AnyFlatSpec
    with Matchers
    with PatientScalaFutures
    with FailFastCirceSupport
    with EitherValuesDetailedMessage
    with BeforeAndAfterAll {

  implicit val ec: ExecutionContext = ExecutionContext.global
  implicit val system: ActorSystem  = ActorSystem("nussknacker-designer")
  implicit val user: LoggedUser     = RealLoggedUser("1", "test")

  it should "handle spaces in scenario id" in {
    val name          = ProcessName("a b c")
    val scenarioGraph = ProcessTestData.validScenarioGraph

    val remoteEnvironment = new MockRemoteEnvironment {

      override protected def request(
          path: Uri,
          method: HttpMethod,
          request: MessageEntity,
          header: Seq[HttpHeader]
      ): Future[HttpResponse] = {
        val expectedUri = baseUri
          .withPath(baseUri.path + "/processes/a%20b%20c")
          .withQuery(Query(("skipValidateAndResolve", "true")))
        if (path == expectedUri && method == HttpMethods.GET) {
          Marshal(
            ScenarioWithDetailsConversions.fromEntityWithScenarioGraph(
              wrapGraphWithScenarioDetailsEntity(name, scenarioGraph),
              ProcessTestData.sampleScenarioParameters
            )
          )
            .to[RequestEntity]
            .map { entity =>
              HttpResponse(StatusCodes.OK, entity = entity)
            }
        } else {
          throw new AssertionError(s"Not expected ${method.value} $path")
        }
      }
    }

    whenReady(remoteEnvironment.compare(scenarioGraph, name, None)) { result =>
      result shouldBe Symbol("right")
    }
  }

  it should "handle non-ascii signs in scenario id" in {
    val name          = ProcessName("łódź")
    val scenarioGraph = ProcessTestData.validScenarioGraph

    val remoteEnvironment = new MockRemoteEnvironment {

      override protected def request(
          path: Uri,
          method: HttpMethod,
          request: MessageEntity,
          headers: Seq[HttpHeader]
      ): Future[HttpResponse] = {
        val expectedUri = baseUri
          .withPath(baseUri.path + "/processes/%C5%82%C3%B3d%C5%BA")
          .withQuery(Query(("skipValidateAndResolve", "true")))
        if (path == expectedUri && method == HttpMethods.GET) {
          Marshal(
            ScenarioWithDetailsConversions.fromEntityWithScenarioGraph(
              wrapGraphWithScenarioDetailsEntity(name, scenarioGraph),
              ProcessTestData.sampleScenarioParameters
            )
          )
            .to[RequestEntity]
            .map { entity =>
              HttpResponse(StatusCodes.OK, entity = entity)
            }
        } else {
          throw new AssertionError(s"Not expected $path")
        }
      }
    }

    whenReady(remoteEnvironment.compare(scenarioGraph, name, None)) { result =>
      result shouldBe Symbol("right")
    }
  }

  it should "post the scenario graph to the remote environment and decode the differences it computes" in {
    val scenarioGraph = ProcessTestData.validScenarioGraph
    val answer = VersionsWithDifferences(
      versions = List(VersionWithDifference(VersionId(3), List("Node 'filter1' modified"), differencesUnknown = false)),
    )
    val sentBody = new AtomicReference[String]()

    val remoteEnvironment = new MockRemoteEnvironment {
      override protected def request(
          path: Uri,
          method: HttpMethod,
          request: MessageEntity,
          headers: Seq[HttpHeader]
      ): Future[HttpResponse] = {
        // with the limit on the wire: the remote bounds its own work with it, so it has to be sent
        val expectedUri = baseUri
          .withPath(baseUri.path + "/processes/proc1/versions-with-differences")
          .withQuery(Query(("limit", "10")))
        if (path == expectedUri && method == HttpMethods.POST) {
          Unmarshaller.stringUnmarshaller(request).flatMap { body =>
            sentBody.set(body)
            Marshal(answer).to[RequestEntity].map(entity => HttpResponse(StatusCodes.OK, entity = entity))
          }
        } else {
          throw new AssertionError(s"Not expected ${method.value} $path")
        }
      }
    }

    whenReady(remoteEnvironment.versionsWithDifferences(ProcessName("proc1"), scenarioGraph, limit = 10)) { result =>
      result shouldBe Some(answer)
      io.circe.parser.decode[ScenarioGraph](sentBody.get()) shouldBe Right(scenarioGraph)
    }
  }

  // A remote that does not hold the scenario is not a remote that failed to answer, and reporting it as one
  // puts an error in front of the user for an ordinary situation.
  it should "resolve versionsWithDifferences to an empty answer when the scenario is absent from the remote" in {
    val remoteEnvironment = mockRemoteEnvironmentReturning(StatusCodes.NotFound)

    whenReady(
      remoteEnvironment.versionsWithDifferences(ProcessName("proc1"), ProcessTestData.validScenarioGraph, limit = 10)
    ) { result => result shouldBe Some(VersionsWithDifferences(Nil)) }
  }

  // The other reason for a 404: the remote holds the scenario but runs a Nussknacker without this endpoint.
  // Answering "no version differs" there would state something we never established.
  it should "resolve versionsWithDifferences to None when the remote has the scenario but not the endpoint" in {
    val remoteEnvironment = new MockRemoteEnvironment {
      override protected def request(
          path: Uri,
          method: HttpMethod,
          request: MessageEntity,
          headers: Seq[HttpHeader]
      ): Future[HttpResponse] =
        if ((method == HttpMethods.HEAD || method == HttpMethods.GET) &&
          path.path.toString.endsWith("/processes/proc1")) {
          Marshal(
            ScenarioWithDetailsConversions.fromEntityWithScenarioGraph(
              wrapGraphWithScenarioDetailsEntity(ProcessName("proc1"), ProcessTestData.validScenarioGraph),
              ProcessTestData.sampleScenarioParameters
            )
          ).to[RequestEntity].map(entity => HttpResponse(StatusCodes.OK, entity = entity))
        } else {
          Future.successful(HttpResponse(StatusCodes.NotFound, entity = HttpEntity("not found")))
        }
    }

    whenReady(
      remoteEnvironment.versionsWithDifferences(ProcessName("proc1"), ProcessTestData.validScenarioGraph, limit = 10)
    ) { result => result shouldBe None }
  }

  it should "resolve versionsWithDifferences to None when the remote returns a server error" in {
    val remoteEnvironment = mockRemoteEnvironmentReturning(StatusCodes.InternalServerError)

    whenReady(
      remoteEnvironment.versionsWithDifferences(ProcessName("proc1"), ProcessTestData.validScenarioGraph, limit = 10)
    ) { result => result shouldBe None }
  }

  it should "fetch and decode activities" in {
    val activity = ScenarioActivity.forScenarioCreated(
      id = UUID.fromString("80c95497-3b53-4435-b2d9-ae73c5766213"),
      user = "some user",
      date = Instant.parse("2024-01-17T14:21:17Z"),
      scenarioVersionId = Some(1),
    )

    val remoteEnvironment = new MockRemoteEnvironment {
      override protected def request(
          path: Uri,
          method: HttpMethod,
          request: MessageEntity,
          headers: Seq[HttpHeader]
      ): Future[HttpResponse] = {
        val expectedUri = baseUri.withPath(baseUri.path + "/processes/proc1/activity/activities")
        if (path == expectedUri && method == HttpMethods.GET) {
          Marshal(ScenarioActivities(List(activity)))
            .to[RequestEntity]
            .map(entity => HttpResponse(StatusCodes.OK, entity = entity))
        } else {
          throw new AssertionError(s"Not expected ${method.value} $path")
        }
      }
    }

    whenReady(remoteEnvironment.activities(ProcessName("proc1"))) { result =>
      result shouldBe List(activity)
    }
  }

  it should "resolve activities to an empty list when the remote returns 404 (unsupported endpoint)" in {
    val remoteEnvironment = mockRemoteEnvironmentReturning(StatusCodes.NotFound)

    whenReady(remoteEnvironment.activities(ProcessName("proc1"))) { result =>
      result shouldBe List.empty
    }
  }

  it should "resolve activities to an empty list when the remote returns a server error" in {
    val remoteEnvironment = mockRemoteEnvironmentReturning(StatusCodes.InternalServerError)

    whenReady(remoteEnvironment.activities(ProcessName("proc1"))) { result =>
      result shouldBe List.empty
    }
  }

  // The must-not-fail contract of RemoteEnvironment covers more than error status codes: a connection
  // failure and a 200 whose body we can't decode both have to resolve to "no data", or the whole
  // versions-with-differences call would blow up with a 500.
  it should "resolve to no data when the remote environment is unreachable" in {
    val remoteEnvironment = mockRemoteEnvironmentFailingWith(new java.net.ConnectException("Connection refused"))

    whenReady(remoteEnvironment.processVersions(ProcessName("proc1"))) { result =>
      result shouldBe RemoteScenarioVersions(List.empty, remoteUnavailable = true)
    }
    whenReady(
      remoteEnvironment.versionsWithDifferences(ProcessName("proc1"), ProcessTestData.validScenarioGraph, limit = 10)
    ) { result => result shouldBe None }
    whenReady(remoteEnvironment.activities(ProcessName("proc1"))) { result =>
      result shouldBe List.empty
    }
  }

  it should "resolve to no data when the remote environment returns an undecodable 200 body" in {
    val remoteEnvironment = new MockRemoteEnvironment {
      override protected def request(
          path: Uri,
          method: HttpMethod,
          request: MessageEntity,
          headers: Seq[HttpHeader]
      ): Future[HttpResponse] =
        Future.successful(
          HttpResponse(StatusCodes.OK, entity = HttpEntity(ContentTypes.`application/json`, """{"unexpected":true}"""))
        )
    }

    whenReady(remoteEnvironment.processVersions(ProcessName("proc1"))) { result =>
      result shouldBe RemoteScenarioVersions(List.empty, remoteUnavailable = true)
    }
    whenReady(
      remoteEnvironment.versionsWithDifferences(ProcessName("proc1"), ProcessTestData.validScenarioGraph, limit = 10)
    ) { result => result shouldBe None }
    whenReady(remoteEnvironment.activities(ProcessName("proc1"))) { result =>
      result shouldBe List.empty
    }
  }

  it should "report the remote environment as available when the scenario is simply absent there (404)" in {
    val remoteEnvironment = mockRemoteEnvironmentReturning(StatusCodes.NotFound)

    whenReady(remoteEnvironment.processVersions(ProcessName("proc1"))) { result =>
      result shouldBe RemoteScenarioVersions(List.empty, remoteUnavailable = false)
    }
  }

  it should "report the remote environment as unavailable when even its health check does not answer" in {
    val remoteEnvironment = mockRemoteEnvironmentReturning(StatusCodes.NotFound, healthy = false)

    whenReady(remoteEnvironment.processVersions(ProcessName("proc1"))) { result =>
      result shouldBe RemoteScenarioVersions(List.empty, remoteUnavailable = true)
    }
  }

  it should "report the remote environment as unavailable when it rejects our credentials" in {
    val remoteEnvironment = mockRemoteEnvironmentReturning(StatusCodes.Unauthorized)

    whenReady(remoteEnvironment.processVersions(ProcessName("proc1"))) { result =>
      result shouldBe RemoteScenarioVersions(List.empty, remoteUnavailable = true)
    }
  }

  private def mockRemoteEnvironmentReturning(statusCode: StatusCode, healthy: Boolean = true) =
    new MockRemoteEnvironment {
      override protected def request(
          path: Uri,
          method: HttpMethod,
          request: MessageEntity,
          headers: Seq[HttpHeader]
      ): Future[HttpResponse] =
        if (path.path.toString.endsWith("/app/healthCheck")) {
          Future.successful(
            if (healthy) HttpResponse(StatusCodes.OK, entity = HttpEntity("""{"status":"OK"}"""))
            else HttpResponse(StatusCodes.ServiceUnavailable, entity = HttpEntity("down"))
          )
        } else {
          Future.successful(HttpResponse(statusCode, entity = HttpEntity("error")))
        }
    }

  private def mockRemoteEnvironmentFailingWith(exception: Throwable) = new MockRemoteEnvironment {
    override protected def request(
        path: Uri,
        method: HttpMethod,
        request: MessageEntity,
        headers: Seq[HttpHeader]
    ): Future[HttpResponse] = Future.failed(exception)
  }

  it should "handle request without labels in decoder fallback to migrate the scenario from/to older versions of NU" in {
    val scenarioDetails = ProcessTestData.validScenarioDetailsForMigrations
    val scenarioDetailEncodedAsOldVersionWithoutLabels =
      scenarioDetails.copy(scenarioGraph = None).asJson.mapObject(_.remove("labels")).noSpaces
    val decodedAsLatestVersion =
      io.circe.parser.decode[ScenarioWithDetailsForMigrations](scenarioDetailEncodedAsOldVersionWithoutLabels)
    decodedAsLatestVersion shouldBe Symbol("right")
    decodedAsLatestVersion.rightValue.labels shouldBe List()
  }

}

object HttpRemoteEnvironmentSpec {

  private val httpConfig = HttpRemoteEnvironmentConfig(
    uri = "http://localhost:8087/api",
    user = "dummy",
    password = "dummy",
    targetEnvironmentId = "remote",
    remoteConfig = StandardRemoteEnvironmentConfig()
  )

  private val testModelMigrations: TestModelMigrations = new TestModelMigrations(
    mapProcessingTypeDataProvider(
      "streaming" -> new ProcessModelMigrator(new TestMigrations(1, 2))
    ),
    mapProcessingTypeDataProvider("streaming" -> flinkProcessValidator("streaming" :: Nil))
  )

  private val impersonationSupport = new ImpersonationSupported {
    override def getImpersonatedUserData(impersonatedUserIdentity: String): Option[ImpersonatedUserData] =
      Some(ImpersonatedUserData("dummy", "dummy", Set("dummy")))
    override def toImpersonatedUserIdentity(userData: ImpersonatedUserData): String = userData.id
  }

  private class MockRemoteEnvironment(
      implicit override val ec: ExecutionContext,
      as: ActorSystem,
      override val materializer: Materializer
  ) extends HttpRemoteEnvironment(
        httpConfig = httpConfig,
        testModelMigrations = testModelMigrations,
        localEnvironmentId = "local",
        remoteEnvironmentId = "remote",
        impersonationSupport = impersonationSupport
      ) {}

}
