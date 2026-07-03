package pl.touk.nussknacker.ui.process.migrate

import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport
import io.circe.syntax.EncoderOps
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.marshalling.Marshal
import org.apache.pekko.http.scaladsl.model.{
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
import org.apache.pekko.stream.Materializer
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.process.{ProcessName, VersionId}
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioWithDetailsForMigrations
import pl.touk.nussknacker.test.{EitherValuesDetailedMessage, PatientScalaFutures}
import pl.touk.nussknacker.test.utils.domain.ProcessTestData
import pl.touk.nussknacker.test.utils.domain.TestFactory.{flinkProcessValidator, mapProcessingTypeDataProvider}
import pl.touk.nussknacker.test.utils.domain.TestProcessUtil.wrapGraphWithScenarioDetailsEntity
import pl.touk.nussknacker.ui.process.ScenarioWithDetailsConversions
import pl.touk.nussknacker.ui.process.migrate.HttpRemoteEnvironmentSpec.MockRemoteEnvironment
import pl.touk.nussknacker.ui.security.api.{ImpersonatedUserData, ImpersonationSupported, LoggedUser, RealLoggedUser}

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
        if (path == baseUri.withPath(baseUri.path + "/processes/a%20b%20c") && method == HttpMethods.GET) {
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
        if (path == baseUri.withPath(baseUri.path + "/processes/%C5%82%C3%B3d%C5%BA") && method == HttpMethods.GET) {
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

  it should "resolve scenarioGraphsForVersions to an empty map when the remote returns 404 (unsupported endpoint)" in {
    val remoteEnvironment = mockRemoteEnvironmentReturning(StatusCodes.NotFound)

    whenReady(remoteEnvironment.scenarioGraphsForVersions(ProcessName("proc1"), List(VersionId(1)))) { result =>
      result shouldBe Map.empty
    }
  }

  it should "resolve scenarioGraphsForVersions to an empty map when the remote returns a server error" in {
    val remoteEnvironment = mockRemoteEnvironmentReturning(StatusCodes.InternalServerError)

    whenReady(remoteEnvironment.scenarioGraphsForVersions(ProcessName("proc1"), List(VersionId(1)))) { result =>
      result shouldBe Map.empty
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

  private def mockRemoteEnvironmentReturning(statusCode: StatusCode) = new MockRemoteEnvironment {
    override protected def request(
        path: Uri,
        method: HttpMethod,
        request: MessageEntity,
        headers: Seq[HttpHeader]
    ): Future[HttpResponse] = Future.successful(HttpResponse(statusCode, entity = HttpEntity("error")))
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
