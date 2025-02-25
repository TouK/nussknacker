package pl.touk.nussknacker.ui.process.migrate

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.marshalling.Marshal
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.stream.Materializer
import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport
import io.circe.parser
import io.circe.syntax.EncoderOps
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioWithDetailsForMigrations
import pl.touk.nussknacker.test.utils.domain.TestFactory.{flinkProcessValidator, mapProcessingTypeDataProvider}
import pl.touk.nussknacker.test.utils.domain.TestProcessUtil.wrapGraphWithScenarioDetailsEntity
import pl.touk.nussknacker.test.utils.domain.{ProcessTestData, TestProcessUtil}
import pl.touk.nussknacker.test.{EitherValuesDetailedMessage, PatientScalaFutures}
import pl.touk.nussknacker.ui.NuDesignerError
import pl.touk.nussknacker.ui.api.description.MigrationApiEndpoints.Dtos.{
  ApiVersion,
  MigrateScenarioRequestDtoV1,
  MigrateScenarioRequestDtoV2
}
import pl.touk.nussknacker.ui.migrations.{MigrateScenarioData, MigrationApiAdapterService}
import pl.touk.nussknacker.ui.process.ScenarioWithDetailsConversions
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter
import pl.touk.nussknacker.ui.security.api.{LoggedUser, RealLoggedUser}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class StandardRemoteEnvironmentSpec
    extends AnyFlatSpec
    with Matchers
    with PatientScalaFutures
    with FailFastCirceSupport
    with EitherValuesDetailedMessage
    with BeforeAndAfterAll {

  implicit val system: ActorSystem = ActorSystem("nussknacker-designer")
  implicit val user: LoggedUser    = RealLoggedUser("1", "test")

  val migrationApiAdapterService = new MigrationApiAdapterService()

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

  it should "request to migrate valid scenario when remote scenario description version is lower than local scenario description version" in {
    val localScenarioDescriptionVersion  = migrationApiAdapterService.getCurrentApiVersion
    val remoteScenarioDescriptionVersion = localScenarioDescriptionVersion - 1
    val remoteEnvironment: MockRemoteEnvironment with LastSentMigrateScenarioRequest =
      remoteEnvironmentMock(scenarioDescriptionVersion = remoteScenarioDescriptionVersion)

    whenReady(
      remoteEnvironment.migrate(
        ProcessTestData.sampleScenarioParameters.processingMode,
        ProcessTestData.sampleScenarioParameters.engineSetupName,
        ProcessTestData.sampleScenarioParameters.category,
        ProcessTestData.sampleScenarioLabels,
        ProcessTestData.validScenarioGraph,
        ProcessTestData.versionId,
        ProcessTestData.sampleProcessName,
        false
      )
    ) { res =>
      res shouldBe Right(())
      remoteEnvironment.lastlySentMigrateScenarioRequest match {
        case Some(migrateScenarioRequest) =>
          migrateScenarioRequest.currentVersion shouldBe remoteScenarioDescriptionVersion
        case _ => fail("lastly sent migrate scenario request should be non empty")
      }
    }
  }

  it should "request to migrate valid scenario when remote scenario description version is the same as local scenario description version" in {
    val localScenarioDescriptionVersion = migrationApiAdapterService.getCurrentApiVersion
    val remoteEnvironment: MockRemoteEnvironment with LastSentMigrateScenarioRequest =
      remoteEnvironmentMock(scenarioDescriptionVersion = localScenarioDescriptionVersion)

    whenReady(
      remoteEnvironment.migrate(
        ProcessTestData.sampleScenarioParameters.processingMode,
        ProcessTestData.sampleScenarioParameters.engineSetupName,
        ProcessTestData.sampleScenarioParameters.category,
        ProcessTestData.sampleScenarioLabels,
        ProcessTestData.validScenarioGraph,
        ProcessTestData.versionId,
        ProcessTestData.sampleProcessName,
        false
      )
    ) { res =>
      res shouldBe Right(())
      remoteEnvironment.lastlySentMigrateScenarioRequest match {
        case Some(migrateScenarioRequest) =>
          migrateScenarioRequest.currentVersion shouldBe localScenarioDescriptionVersion
        case _ => fail("lastly sent migrate scenario request should be non empty")
      }
    }
  }

  it should "request to migrate valid scenario when remote scenario description version is higher than local scenario description version" in {
    val localScenarioDescriptionVersion  = migrationApiAdapterService.getCurrentApiVersion
    val remoteScenarioDescriptionVersion = localScenarioDescriptionVersion + 1
    val remoteEnvironment: MockRemoteEnvironment with LastSentMigrateScenarioRequest =
      remoteEnvironmentMock(scenarioDescriptionVersion = remoteScenarioDescriptionVersion)

    whenReady(
      remoteEnvironment.migrate(
        ProcessTestData.sampleScenarioParameters.processingMode,
        ProcessTestData.sampleScenarioParameters.engineSetupName,
        ProcessTestData.sampleScenarioParameters.category,
        ProcessTestData.sampleScenarioLabels,
        ProcessTestData.validScenarioGraph,
        ProcessTestData.versionId,
        ProcessTestData.sampleProcessName,
        false
      )
    ) { res =>
      res shouldBe Right(())
      remoteEnvironment.lastlySentMigrateScenarioRequest match {
        case Some(migrateScenarioRequest) =>
          migrateScenarioRequest.currentVersion shouldBe localScenarioDescriptionVersion
        case _ => fail("lastly sent migrate scenario request should be non empty")
      }
    }
  }

  it should "test migration" in {
    val remoteEnvironment = environmentForTestMigration(
      processes = ProcessTestData.validScenarioDetailsForMigrations :: Nil,
      fragments = TestProcessUtil.wrapWithDetailsForMigration(
        CanonicalProcessConverter.toScenarioGraph(ProcessTestData.sampleFragment),
        ProcessTestData.sampleFragment.name
      ) :: Nil
    )

    val migrationResult = remoteEnvironment
      .testMigration(
        batchingExecutionContext = ExecutionContext.global
      )
      .futureValueEnsuringInnerException(10 seconds)
      .rightValue

    migrationResult should have size 2
    migrationResult.map(
      _.processName
    ) should contain only (ProcessTestData.validScenarioDetailsForMigrations.name, ProcessTestData.sampleFragment.name)
  }

  override protected def afterAll(): Unit = {
    system.terminate().futureValue
    super.afterAll()
  }

  trait MockRemoteEnvironment extends StandardRemoteEnvironment {
    override def environmentId = "testEnv"

    def config: StandardRemoteEnvironmentConfig = StandardRemoteEnvironmentConfig(
      uri = "http://localhost:8087/api",
      batchSize = 100
    )

    override implicit val materializer: Materializer = Materializer(system)

    override def testModelMigrations: TestModelMigrations = new TestModelMigrations(
      mapProcessingTypeDataProvider(
        "streaming" -> new ProcessModelMigrator(new TestMigrations(1, 2))
      ),
      mapProcessingTypeDataProvider("streaming" -> flinkProcessValidator)
    )

  }

  private trait LastSentMigrateScenarioRequest {
    var lastlySentMigrateScenarioRequest: Option[MigrateScenarioData] = None
  }

  private def remoteEnvironmentMock(
      scenarioDescriptionVersion: Int
  ) = new MockRemoteEnvironment with LastSentMigrateScenarioRequest {

    override protected def request(
        uri: Uri,
        method: HttpMethod,
        request: MessageEntity,
        header: Seq[HttpHeader]
    ): Future[HttpResponse] = {
      import HttpMethods._
      import StatusCodes._
      // helpers
      def is(relative: String, m: HttpMethod): Boolean = {
        uri.toString.startsWith(s"$baseUri$relative") && method == m
      }

      object GetMigrationScenarioDescriptionVersion {
        def unapply(arg: (String, HttpMethod)): Boolean = is("/migration/scenario/description/version", GET)
      }

      object Migrate {
        def unapply(args: (String, HttpMethod)): Boolean = is("/migrate", POST)
      }
      // end helpers

      (uri.toString(), method) match {
        case GetMigrationScenarioDescriptionVersion() =>
          Marshal(ApiVersion(scenarioDescriptionVersion)).to[RequestEntity].map { entity =>
            HttpResponse(OK, entity = entity)
          }
        case Migrate() =>
          parseBodyToJson(request).as[MigrateScenarioRequestDtoV2] match {
            case Right(migrateScenarioRequestDtoV2) if migrateScenarioRequestDtoV2.version == 2 =>
              lastlySentMigrateScenarioRequest = Some(
                MigrateScenarioData.toDomain(migrateScenarioRequestDtoV2).rightValue
              )
            case Right(_) =>
              parseBodyToJson(request).as[MigrateScenarioRequestDtoV1] match {
                case Right(migrateScenarioRequestDtoV1) =>
                  lastlySentMigrateScenarioRequest = Some(
                    MigrateScenarioData.toDomain(migrateScenarioRequestDtoV1).rightValue
                  )
                case Left(_) => lastlySentMigrateScenarioRequest = None
              }
            case Left(_) =>
              parseBodyToJson(request).as[MigrateScenarioRequestDtoV1] match {
                case Right(migrateScenarioRequestDtoV1) =>
                  lastlySentMigrateScenarioRequest = Some(
                    MigrateScenarioData.toDomain(migrateScenarioRequestDtoV1).rightValue
                  )
                case Left(_) => lastlySentMigrateScenarioRequest = None
              }
          }

          Marshal(Right[NuDesignerError, Unit](())).to[RequestEntity].map { entity =>
            HttpResponse(OK, entity = entity)
          }
        case _ =>
          throw new AssertionError(s"Not expected $uri")
      }
    }

  }

  private def parseBodyToJson(request: MessageEntity) = {
    val stringBody = request match {
      case HttpEntity.Strict(_, byteString) => byteString.decodeString(HttpCharsets.`UTF-8`.nioCharset())
      case _                                => throw new IllegalStateException("Unhandled MessageEntity type")
    }
    parser.parse(stringBody).toOption.getOrElse(throw new IllegalStateException("Validation request should be a json"))
  }

  private def environmentForTestMigration(
      processes: List[ScenarioWithDetailsForMigrations],
      fragments: List[ScenarioWithDetailsForMigrations]
  ) = new MockRemoteEnvironment {

    private def allProcesses: List[ScenarioWithDetailsForMigrations] = processes ++ fragments

    override protected def request(
        uri: Uri,
        method: HttpMethod,
        request: MessageEntity,
        header: Seq[HttpHeader]
    ): Future[HttpResponse] = {
      object GetProcessesDetailsWithoutScenarioGraph {
        def unapply(arg: (Uri, HttpMethod)): Boolean = {
          arg._1.toString() == s"$baseUri/processes?isArchived=false" && arg._2 == HttpMethods.GET
        }
      }

      object GetProcessesDetails {
        def unapply(arg: (Uri, HttpMethod)): Option[Set[ProcessName]] = {
          val uri = arg._1
          if (uri.withQuery(Uri.Query.Empty) == baseUri.withPath(baseUri.path + "/processesDetails") && uri
              .query()
              .get("isArchived")
              .contains("false") && arg._2 == HttpMethods.GET) {
            uri.query().get("names").map(_.split(",").map(ProcessName(_)).toSet)
          } else {
            None
          }
        }
      }

      (uri, method) match {
        case GetProcessesDetailsWithoutScenarioGraph() =>
          // response without labels to test decoder fallback
          val response =
            allProcesses.map(_.copy(scenarioGraph = None)).asJson.mapArray(_.map(_.mapObject(_.remove("labels"))))
          Marshal(response)
            .to[ResponseEntity]
            .map { entity =>
              HttpResponse(entity = entity)
            }
        case GetProcessesDetails(names) =>
          Marshal(allProcesses.filter(p => names(p.name))).to[ResponseEntity].map { entity =>
            HttpResponse(entity = entity)
          }
        case _ => throw new IllegalArgumentException()
      }
    }

  }

}
