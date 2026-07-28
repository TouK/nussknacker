package pl.touk.nussknacker.ui.process.migrate

import cats.data.EitherT
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import io.circe.Decoder
import io.circe.syntax.EncoderOps
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.{Http, HttpExt}
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.model.Uri.{Path, Query}
import org.apache.pekko.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials, RawHeader}
import org.apache.pekko.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import org.apache.pekko.stream.Materializer
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.{ProcessName, ScenarioVersion, VersionId}
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioWithDetailsForMigrations
import pl.touk.nussknacker.ui.NuDesignerError
import pl.touk.nussknacker.ui.api.description.MigrationApiEndpoints.Codecs.MigrateScenarioRequestDto.encoder
import pl.touk.nussknacker.ui.api.description.MigrationApiEndpoints.Dtos.ApiVersion
import pl.touk.nussknacker.ui.api.description.scenarioActivity.Dtos.{ScenarioActivities, ScenarioActivity}
import pl.touk.nussknacker.ui.migrations.MigrateScenarioData
import pl.touk.nussknacker.ui.security.api.{
  AuthManager,
  ImpersonatedUser,
  ImpersonatedUserData,
  ImpersonationSupport,
  LoggedUser,
  RealLoggedUser
}

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.DurationInt
import scala.util.control.NonFatal

final case class HttpRemoteEnvironmentConfig(
    uri: String,
    user: String,
    password: String,
    targetEnvironmentId: String,
    remoteConfig: StandardRemoteEnvironmentConfig
)

class HttpRemoteEnvironment(
    httpConfig: HttpRemoteEnvironmentConfig,
    override val testModelMigrations: TestModelMigrations,
    override val localEnvironmentId: String,
    override val remoteEnvironmentId: String,
    impersonationSupport: ImpersonationSupport
)(implicit override val ec: ExecutionContext, as: ActorSystem, val materializer: Materializer)
    extends StandardRemoteEnvironment
    with LazyLogging
    with AutoCloseable {

  override val config: StandardRemoteEnvironmentConfig = httpConfig.remoteConfig

  private val closeTimeout = 10 seconds

  def baseUri: Uri = Uri(httpConfig.uri)

  val http: HttpExt = Http()

  override def close(): Unit = Await.ready(closeAsync(), closeTimeout)

  def closeAsync(): Future[Unit] = http.shutdownAllConnectionPools()

  override def processVersions(processName: ProcessName): Future[RemoteScenarioVersions] =
    invokeJson[ScenarioWithDetailsForMigrations](
      HttpMethods.GET,
      List("processes", processName.value)
    ).map {
      _.fold(
        error => {
          logFetchError("scenario versions", processName, error)
          RemoteScenarioVersions(Nil, remoteUnavailable = !isScenarioAbsentOnRemote(error))
        },
        details => RemoteScenarioVersions(details.historyUnsafe, remoteUnavailable = false)
      )
    }.recover { case NonFatal(ex) =>
      logger.error(s"Failed to fetch scenario versions from remote environment for scenario ${processName.value}", ex)
      RemoteScenarioVersions(Nil, remoteUnavailable = true)
    }

  override def scenarioGraphsForVersions(
      processName: ProcessName,
      versionIds: List[VersionId]
  ): Future[Map[VersionId, ScenarioGraph]] =
    invokeJson[VersionGraphs](
      HttpMethods.GET,
      List("processes", processName.value, "versions", "graphs"),
      Query(("versionIds", versionIds.map(_.value).mkString(",")))
    ).map(
      _.fold(
        error => {
          logFetchError("scenario graphs", processName, error)
          Map.empty[VersionId, ScenarioGraph]
        },
        _.versions.map(g => g.versionId -> g.scenarioGraph).toMap
      )
    ).recover { case NonFatal(ex) =>
      logger.warn(s"Failed to fetch scenario graphs from remote environment for scenario ${processName.value}", ex)
      Map.empty
    }

  override def activities(processName: ProcessName): Future[List[ScenarioActivity]] =
    invokeJson[ScenarioActivities](
      HttpMethods.GET,
      List("processes", processName.value, "activity", "activities")
    ).map(
      _.fold(
        error => {
          logFetchError("activities", processName, error)
          List.empty[ScenarioActivity]
        },
        _.activities
      )
    ).recover { case NonFatal(ex) =>
      logger.warn(s"Failed to fetch activities from remote environment for scenario ${processName.value}", ex)
      List.empty
    }

  private def isScenarioAbsentOnRemote(error: NuDesignerError): Boolean = error match {
    case RemoteEnvironmentCommunicationError(StatusCodes.NotFound, _) => true
    case _                                                           => false
  }

  private def logFetchError(what: String, processName: ProcessName, error: NuDesignerError): Unit = error match {
    case RemoteEnvironmentCommunicationError(StatusCodes.NotFound, _) =>
      logger.warn(
        s"Remote environment doesn't have $what for scenario ${processName.value} " +
          s"(scenario not migrated there, or an older Nussknacker version without this endpoint)"
      )
    case RemoteEnvironmentCommunicationError(statusCode, message)
        if statusCode == StatusCodes.Unauthorized || statusCode == StatusCodes.Forbidden =>
      logger.error(
        s"Remote environment rejected our credentials while fetching $what for scenario " +
          s"${processName.value}: $statusCode $message. Check the remote environment configuration."
      )
    case RemoteEnvironmentCommunicationError(statusCode, message) =>
      logger.warn(
        s"Failed to fetch $what from remote environment for scenario ${processName.value}: $statusCode $message"
      )
    case other =>
      logger.warn(s"Failed to fetch $what from remote environment for scenario ${processName.value}: $other")
  }

  override protected def fetchRemoteMigrationScenarioDescriptionVersion: FutureE[Int] = {
    EitherT {
      invokeJson[ApiVersion](
        HttpMethods.GET,
        List("migration", "scenario", "description", "version"),
        Query.Empty,
        requestEntity = HttpEntity.Empty
      ).map(_.map(_.version))
    }
  }

  override protected def migrateScenario(
      migrateScenarioData: MigrateScenarioData
  )(implicit loggedUser: LoggedUser): FutureE[Unit] = {
    val dto = MigrateScenarioData.fromDomain(migrateScenarioData)
    EitherT {
      invokeForSuccess(
        HttpMethods.POST,
        List("migrate"),
        Query.Empty,
        HttpEntity(dto.asJson.noSpaces),
        impersonationHeader(loggedUser)
      ).map {
        case left @ Left(nuDesignerError) =>
          logger.warn(s"Migration error: $nuDesignerError")
          left
        case right => right
      }
    }
  }

  override protected def fetchProcesses: FutureE[List[ScenarioWithDetailsForMigrations]] = EitherT {
    invokeJson[List[ScenarioWithDetailsForMigrations]](
      HttpMethods.GET,
      List("processes"),
      Query(("isArchived", "false"))
    )
  }

  override protected def fetchProcessVersion(
      name: ProcessName,
      remoteProcessVersion: Option[VersionId]
  ): FutureE[ScenarioWithDetailsForMigrations] = EitherT {
    invokeJson[ScenarioWithDetailsForMigrations](
      HttpMethods.GET,
      List("processes", name.value) ++ remoteProcessVersion.map(_.value.toString).toList,
      Query()
    )
  }

  override protected def fetchProcessesDetails(
      names: List[ProcessName]
  ): FutureE[List[ScenarioWithDetailsForMigrations]] = EitherT {
    invokeJson[List[ScenarioWithDetailsForMigrations]](
      HttpMethods.GET,
      "processesDetails" :: Nil,
      Query(
        ("names", names.map(_.value).mkString(",")),
        ("isArchived", "false"),
        ("skipNodeResults", "true"),
      )
    )
  }

  protected def request(
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

  private def impersonationHeader(loggedUser: LoggedUser): List[HttpHeader] = {
    val roles = loggedUser match {
      case r: RealLoggedUser   => r.roles
      case i: ImpersonatedUser => i.impersonatedUser.roles
    }
    impersonationSupport.toImpersonatedUserIdentityWithSupportCheck(
      ImpersonatedUserData(loggedUser.id, loggedUser.username, roles)
    ) match {
      case Right(identityString) => List(RawHeader(AuthManager.impersonateHeaderName, identityString))
      case Left(_)               => Nil
    }
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
  )(implicit ec: ExecutionContext): Future[Either[NuDesignerError, Unit]] =
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
          .map(error => RemoteEnvironmentCommunicationError(response.status, error).asLeft)
      }
    }
  }

}
