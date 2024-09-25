package pl.touk.nussknacker.ui.process.migrate

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri.{Path, Query}
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials}
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import akka.http.scaladsl.{Http, HttpExt}
import akka.stream.Materializer
import cats.data.EitherT
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Decoder
import io.circe.syntax.EncoderOps
import pl.touk.nussknacker.engine.api.component.ProcessingMode
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.{ProcessName, ScenarioVersion, VersionId}
import pl.touk.nussknacker.engine.deployment.EngineSetupName
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioWithDetailsForMigrations
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationErrors
import pl.touk.nussknacker.ui.NuDesignerError.XError
import pl.touk.nussknacker.ui.api.description.MigrationApiEndpoints.Dtos.ApiVersion
import pl.touk.nussknacker.ui.migrations.{
  MigrateScenarioData,
  MigrateScenarioDataV1,
  MigrateScenarioDataV2,
  MigrationApiAdapterService
}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.ScenarioGraphComparator.Difference
import pl.touk.nussknacker.ui.util.{ApiAdapterServiceError, OutOfRangeAdapterRequestError, ScenarioGraphComparator}
import pl.touk.nussknacker.ui.{FatalError, NuDesignerError}

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import scala.collection.parallel.ExecutionContextTaskSupport
import scala.collection.parallel.immutable.ParVector
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future}

trait RemoteEnvironment {

  val passUsernameInMigration: Boolean = true

  def compare(
      localGraph: ScenarioGraph,
      remoteProcessName: ProcessName,
      remoteProcessVersion: Option[VersionId]
  )(
      implicit ec: ExecutionContext
  ): Future[Either[NuDesignerError, Map[String, Difference]]]

  def processVersions(processName: ProcessName)(implicit ec: ExecutionContext): Future[List[ScenarioVersion]]

  def migrate(
      processingMode: ProcessingMode,
      engineSetupName: EngineSetupName,
      processCategory: String,
      scenarioLabels: List[String],
      scenarioGraph: ScenarioGraph,
      localScenarioVersionId: VersionId,
      processName: ProcessName,
      isFragment: Boolean
  )(
      implicit ec: ExecutionContext,
      loggedUser: LoggedUser
  ): Future[Either[NuDesignerError, Unit]]

  // TODO This method is used by an external project. We should move it to some api module
  def testMigration(
      processToInclude: ScenarioWithDetailsForMigrations => Boolean = _ => true,
      batchingExecutionContext: ExecutionContext
  )(
      implicit ec: ExecutionContext,
      user: LoggedUser
  ): Future[Either[NuDesignerError, List[TestMigrationResult]]]

}

final case class MigrationApiAdapterError(apiAdapterError: ApiAdapterServiceError)
    extends FatalError(
      apiAdapterError match {
        case OutOfRangeAdapterRequestError(currentVersion, signedNoOfVersionsLeftToApply) =>
          signedNoOfVersionsLeftToApply match {
            case n if n >= 0 =>
              s"Migration API Adapter error occurred when trying to adapt MigrateScenarioRequest in version: $currentVersion to $signedNoOfVersionsLeftToApply version(s) up"
            case _ =>
              s"Migration API Adapter error occurred when trying to adapt MigrateScenarioRequest in version: $currentVersion to ${-signedNoOfVersionsLeftToApply} version(s) down"
          }
      }
    )

final case class RemoteEnvironmentCommunicationError(statusCode: StatusCode, message: String)
    extends FatalError(message)

final case class MigrationValidationError(errors: ValidationErrors)
    extends FatalError({
      val messages = errors.globalErrors.map(_.error.message) ++
        errors.processPropertiesErrors.map(_.message) ++ errors.invalidNodes.map { case (node, nerror) =>
          s"$node - ${nerror.map(_.message).mkString(", ")}"
        }
      s"Cannot migrate, following errors occurred: ${messages.mkString(", ")}"
    })

final case class MissingScenarioGraphError(msg: String)
    extends FatalError(
      msg
    )

final case class MigrationToArchivedError(processName: ProcessName, environment: String)
    extends FatalError(
      s"Cannot migrate, scenario $processName is archived on $environment. You have to unarchive scenario on $environment in order to migrate."
    )

final case class HttpRemoteEnvironmentConfig(
    user: String,
    password: String,
    targetEnvironmentId: String,
    remoteConfig: StandardRemoteEnvironmentConfig,
    passUsernameInMigration: Boolean = true
)

class HttpRemoteEnvironment(
    httpConfig: HttpRemoteEnvironmentConfig,
    val testModelMigrations: TestModelMigrations,
    val environmentId: String
)(implicit as: ActorSystem, val materializer: Materializer)
    extends StandardRemoteEnvironment
    with LazyLogging
    with AutoCloseable {
  override val config: StandardRemoteEnvironmentConfig = httpConfig.remoteConfig

  override val passUsernameInMigration: Boolean = httpConfig.passUsernameInMigration

  private val closeTimeout = 10 seconds

  val http: HttpExt = Http()

  override protected def request(
      uri: Uri,
      method: HttpMethod,
      request: MessageEntity,
      headers: Seq[HttpHeader]
  ): Future[HttpResponse] = {
    logger.debug("Sending request to remote environment: {} {}", method.value, uri)
    http.singleRequest(
      HttpRequest(
        uri = uri,
        method = method,
        entity = request,
        headers = List(Authorization(BasicHttpCredentials(httpConfig.user, httpConfig.password))) ++ headers
      )
    )
  }

  override def close(): Unit = Await.ready(http.shutdownAllConnectionPools(), closeTimeout)

  def closeAsync(): Future[Unit] = http.shutdownAllConnectionPools()
}

final case class StandardRemoteEnvironmentConfig(
    uri: String,
    batchSize: Int = 10,
    batchTimeout: FiniteDuration = 120 seconds,
    useLegacyCreateScenarioApi: Boolean = false
)

//TODO: extract interface to remote environment?
trait StandardRemoteEnvironment extends FailFastCirceSupport with RemoteEnvironment with LazyLogging {

  private type FutureE[T] = EitherT[Future, NuDesignerError, T]

  private val migrationApiAdapterService: MigrationApiAdapterService = new MigrationApiAdapterService()

  def environmentId: String

  def config: StandardRemoteEnvironmentConfig

  def testModelMigrations: TestModelMigrations

  def baseUri: Uri = Uri(config.uri)

  implicit def materializer: Materializer

  override def processVersions(processName: ProcessName)(implicit ec: ExecutionContext): Future[List[ScenarioVersion]] =
    invokeJson[ScenarioWithDetailsForMigrations](HttpMethods.GET, List("processes", processName.value)).map { result =>
      result.fold(_ => List(), _.historyUnsafe)
    }

  protected def request(
      uri: Uri,
      method: HttpMethod,
      request: MessageEntity,
      headers: Seq[HttpHeader]
  ): Future[HttpResponse]

  override def compare(
      localGraph: ScenarioGraph,
      remoteProcessName: ProcessName,
      remoteProcessVersion: Option[VersionId]
  )(
      implicit ec: ExecutionContext
  ): Future[Either[NuDesignerError, Map[String, Difference]]] = {
    (for {
      process <- EitherT(fetchProcessVersion(remoteProcessName, remoteProcessVersion))
      compared <- EitherT.rightT[Future, NuDesignerError](
        ScenarioGraphComparator.compare(localGraph, process.scenarioGraphUnsafe)
      )
    } yield compared).value
  }

  override def migrate(
      processingMode: ProcessingMode,
      engineSetupName: EngineSetupName,
      processCategory: String,
      scenarioLabels: List[String],
      scenarioGraph: ScenarioGraph,
      localScenarioVersionId: VersionId,
      processName: ProcessName,
      isFragment: Boolean
  )(implicit ec: ExecutionContext, loggedUser: LoggedUser): Future[Either[NuDesignerError, Unit]] = {

    val result: EitherT[Future, NuDesignerError, Unit] = for {
      remoteScenarioDescriptionVersion <- fetchRemoteMigrationScenarioDescriptionVersion
      localScenarioDescriptionVersion = migrationApiAdapterService.getCurrentApiVersion
      migrateScenarioRequest: MigrateScenarioData =
        MigrateScenarioDataV2(
          environmentId,
          Some(localScenarioVersionId),
          loggedUser.username,
          processingMode,
          engineSetupName,
          processCategory,
          scenarioLabels,
          scenarioGraph,
          processName,
          isFragment
        )
      versionsDifference = localScenarioDescriptionVersion - remoteScenarioDescriptionVersion
      transformedMigrateScenarioRequestE =
        if (versionsDifference > 0)
          migrationApiAdapterService.adaptDown(migrateScenarioRequest, versionsDifference)
        else
          Right(migrateScenarioRequest)
      _ <- handleTransformedMigrateScenarioRequest(transformedMigrateScenarioRequestE)
    } yield ()

    result.value
  }

  private def handleTransformedMigrateScenarioRequest(
      transformedMigrateScenarioRequestE: Either[ApiAdapterServiceError, MigrateScenarioData]
  )(implicit ec: ExecutionContext): EitherT[Future, NuDesignerError, Unit] = {
    transformedMigrateScenarioRequestE match {
      case Left(apiAdapterServiceError) =>
        EitherT.leftT(MigrationApiAdapterError(apiAdapterServiceError))
      case Right(transformedMigrateScenarioRequest) =>
        import pl.touk.nussknacker.ui.api.description.MigrationApiEndpoints.Codecs.MigrateScenarioRequestDto.encoder
        val dto = MigrateScenarioData.fromDomain(transformedMigrateScenarioRequest)
        EitherT(
          invokeForSuccess(
            HttpMethods.POST,
            List("migrate"),
            Query.Empty,
            HttpEntity(dto.asJson.noSpaces),
            List()
          )
        )
    }
  }

  private def fetchRemoteMigrationScenarioDescriptionVersion(implicit ec: ExecutionContext) = {
    EitherT(
      fetchRemoteMigrationScenarioDescriptionVersionAux.map[Either[NuDesignerError, Int]](apiVersion =>
        Right(apiVersion.version)
      )
    )
  }

  private def fetchRemoteMigrationScenarioDescriptionVersionAux(implicit ec: ExecutionContext): Future[ApiVersion] = {
    // TODO: let's use client created from Tapir endpoints instead
    invoke[ApiVersion](
      HttpMethods.GET,
      List("migration", "scenario", "description", "version"),
      Query.Empty,
      requestEntity = HttpEntity.Empty,
      headers = Seq.empty
    ) { res =>
      Unmarshal(res.entity).to[ApiVersion]
    }
  }

  // We need to be cautious when choosing maxParallelism of batchingExecutionContext as validation may call external systems and we don't want to overwhelm them with requests
  override def testMigration(
      processToInclude: ScenarioWithDetailsForMigrations => Boolean = _ => true,
      batchingExecutionContext: ExecutionContext
  )(implicit ec: ExecutionContext, user: LoggedUser): Future[Either[NuDesignerError, List[TestMigrationResult]]] = {
    (for {
      allBasicProcesses <- EitherT(fetchProcesses)
      basicProcesses = allBasicProcesses.filterNot(_.isFragment).filter(processToInclude)
      basicFragments = allBasicProcesses.filter(_.isFragment).filter(processToInclude)
      processes <- fetchGroupByGroup(basicProcesses, batchingExecutionContext)
      fragments <- fetchGroupByGroup(basicFragments, batchingExecutionContext)
    } yield testModelMigrations.testMigrations(processes, fragments, batchingExecutionContext)).value
  }

  private def fetchGroupByGroup(
      basicProcesses: List[ScenarioWithDetailsForMigrations],
      batchingExecutionContext: ExecutionContext
  )(implicit ec: ExecutionContext): FutureE[List[ScenarioWithDetailsForMigrations]] = {
    val groupedBasicProcesses = basicProcesses
      .map(_.name)
      .grouped(config.batchSize)
      .toVector
    // We create ParVector manually instead of calling par for compatibility with Scala 2.12
    val parallelCollection = new ParVector(groupedBasicProcesses)
    parallelCollection.tasksupport = new ExecutionContextTaskSupport(batchingExecutionContext)
    val fetchProcessDetailsOperation = parallelCollection.map(processesGroup => {
      Await.result(fetchProcessesDetails(processesGroup).value, config.batchTimeout)
    })
    EitherT {
      Future {
        for {
          scenariosWithDetails <- fetchProcessDetailsOperation.toList.sequence
        } yield scenariosWithDetails.flatten
      }
    }
  }

  private def fetchProcesses(
      implicit ec: ExecutionContext
  ): Future[Either[NuDesignerError, List[ScenarioWithDetailsForMigrations]]] = {
    invokeJson[List[ScenarioWithDetailsForMigrations]](
      HttpMethods.GET,
      List("processes"),
      Query(("isArchived", "false"))
    )
  }

  private def fetchProcessVersion(name: ProcessName, remoteProcessVersion: Option[VersionId])(
      implicit ec: ExecutionContext
  ): Future[Either[NuDesignerError, ScenarioWithDetailsForMigrations]] = {
    invokeJson[ScenarioWithDetailsForMigrations](
      HttpMethods.GET,
      List("processes", name.value) ++ remoteProcessVersion.map(_.value.toString).toList,
      Query()
    )
  }

  private def fetchProcessesDetails(names: List[ProcessName])(implicit ec: ExecutionContext) = EitherT {
    invokeJson[List[ScenarioWithDetailsForMigrations]](
      HttpMethods.GET,
      "processesDetails" :: Nil,
      Query(
        ("names", names.map(ns => URLEncoder.encode(ns.value, StandardCharsets.UTF_8)).mkString(",")),
        ("isArchived", "false"),
        ("skipNodeResults", "true"),
      )
    )
  }

  private def invoke[T](
      method: HttpMethod,
      pathParts: List[String],
      query: Query = Query.Empty,
      requestEntity: RequestEntity = HttpEntity.Empty,
      headers: Seq[HttpHeader]
  )(f: HttpResponse => Future[T])(implicit ec: ExecutionContext): Future[T] = {
    val pathEncoded = pathParts.foldLeft[Path](baseUri.path)(_ / _)
    val uri         = baseUri.withPath(pathEncoded).withQuery(query)

    request(uri, method, requestEntity, headers) flatMap f
  }

  private def invokeForSuccess(
      method: HttpMethod,
      pathParts: List[String],
      query: Query = Query.Empty,
      requestEntity: RequestEntity,
      headers: Seq[HttpHeader]
  )(implicit ec: ExecutionContext): Future[XError[Unit]] =
    invoke(method, pathParts, query, requestEntity, headers) { response =>
      if (response.status.isSuccess()) {
        response.discardEntityBytes()
        Future.successful(().asRight)
      } else {
        Unmarshaller
          .stringUnmarshaller(response.entity)
          .map(error => RemoteEnvironmentCommunicationError(response.status, error).asLeft)
      }
    }

  private def invokeJson[T: Decoder](
      method: HttpMethod,
      pathParts: List[String],
      query: Query = Query.Empty,
      requestEntity: RequestEntity = HttpEntity.Empty
  )(implicit ec: ExecutionContext): Future[Either[NuDesignerError, T]] = {
    invoke(method, pathParts, query, requestEntity, headers = Nil) { response =>
      if (response.status.isSuccess()) {
        Unmarshal(response.entity).to[T].map(Either.right)
      } else {
        Unmarshaller
          .stringUnmarshaller(response.entity)
          .map(error => Either.left(RemoteEnvironmentCommunicationError(response.status, error)))
      }
    }
  }

}
