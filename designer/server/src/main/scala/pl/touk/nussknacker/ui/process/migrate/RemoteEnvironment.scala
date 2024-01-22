package pl.touk.nussknacker.ui.process.migrate

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import akka.actor.ActorSystem
import akka.http.scaladsl.{Http, HttpExt}
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.Uri.{Path, Query}
import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials, RawHeader}
import akka.http.scaladsl.model.{RequestEntity, _}
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import akka.stream.Materializer
import cats.data.EitherT
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Decoder
import pl.touk.nussknacker.engine.api.process.{ProcessName, VersionId}
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.restmodel.processdetails.{
  BasicProcess,
  ProcessDetails,
  ProcessVersion,
  ValidatedProcessDetails
}
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{ValidationErrors, ValidationResult}
import pl.touk.nussknacker.ui.EspError
import pl.touk.nussknacker.ui.EspError.XError
import pl.touk.nussknacker.ui.process.ProcessService.UpdateProcessCommand
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.RemoteUserName
import pl.touk.nussknacker.ui.process.repository.UpdateProcessComment
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.ProcessComparator
import pl.touk.nussknacker.ui.util.ProcessComparator.Difference

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import scala.collection.parallel.ExecutionContextTaskSupport
import scala.collection.parallel.immutable.ParVector
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.duration.DurationInt

trait RemoteEnvironment {

  val passUsernameInMigration: Boolean = true

  def compare(localProcess: DisplayableProcess, remoteProcessVersion: Option[VersionId])(
      implicit ec: ExecutionContext
  ): Future[Either[EspError, Map[String, Difference]]]

  def processVersions(processName: ProcessName)(implicit ec: ExecutionContext): Future[List[ProcessVersion]]

  def migrate(localProcess: DisplayableProcess, category: String)(
      implicit ec: ExecutionContext,
      loggedUser: LoggedUser
  ): Future[Either[EspError, Unit]]

  def testMigration(processToInclude: BasicProcess => Boolean = _ => true, batchingExecutionContext: ExecutionContext)(
      implicit ec: ExecutionContext
  ): Future[Either[EspError, List[TestMigrationResult]]]

}

final case class RemoteEnvironmentCommunicationError(statusCode: StatusCode, getMessage: String) extends EspError

final case class MigrationValidationError(errors: ValidationErrors) extends EspError {

  override def getMessage: String = {
    val messages = errors.globalErrors.map(_.message) ++
      errors.processPropertiesErrors.map(_.message) ++ errors.invalidNodes.map { case (node, nerror) =>
        s"$node - ${nerror.map(_.message).mkString(", ")}"
      }
    s"Cannot migrate, following errors occurred: ${messages.mkString(", ")}"
  }

}

final case class MigrationToArchivedError(processName: ProcessName, environment: String) extends EspError {
  def getMessage =
    s"Cannot migrate, scenario ${processName.value} is archived on $environment. You have to unarchive scenario on $environment in order to migrate."
}

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
    batchTimeout: FiniteDuration = 120 seconds
)

//TODO: extract interface to remote environment?
trait StandardRemoteEnvironment extends FailFastCirceSupport with RemoteEnvironment {

  private type FutureE[T] = EitherT[Future, EspError, T]

  def environmentId: String

  def config: StandardRemoteEnvironmentConfig

  def testModelMigrations: TestModelMigrations

  def baseUri: Uri = Uri(config.uri)

  implicit def materializer: Materializer

  override def processVersions(processName: ProcessName)(implicit ec: ExecutionContext): Future[List[ProcessVersion]] =
    invokeJson[ProcessDetails](HttpMethods.GET, List("processes", processName.value)).map { result =>
      result.fold(_ => List(), _.history.getOrElse(Nil))
    }

  protected def request(
      uri: Uri,
      method: HttpMethod,
      request: MessageEntity,
      headers: Seq[HttpHeader]
  ): Future[HttpResponse]

  override def compare(localProcess: DisplayableProcess, remoteProcessVersion: Option[VersionId])(
      implicit ec: ExecutionContext
  ): Future[Either[EspError, Map[String, Difference]]] = {
    val id = localProcess.id
    (for {
      process  <- EitherT(fetchProcessVersion(id, remoteProcessVersion))
      compared <- EitherT.rightT[Future, EspError](ProcessComparator.compare(localProcess, process.json))
    } yield compared).value
  }

  override def migrate(
      localProcess: DisplayableProcess,
      category: String
  )(implicit ec: ExecutionContext, loggedUser: LoggedUser): Future[Either[EspError, Unit]] = {
    (for {
      validation <- EitherT(validateProcess(localProcess))
      _ <- EitherT.fromEither[Future](
        if (validation.errors != ValidationErrors.success)
          Left[EspError, Unit](MigrationValidationError(validation.errors))
        else Right(())
      )
      processEither <- fetchProcessDetails(localProcess.id)
      _ <- processEither match {
        case Right(processDetails) if processDetails.isArchived =>
          EitherT.leftT[Future, EspError](MigrationToArchivedError(processDetails.idWithName.name, environmentId))
        case Right(_) => EitherT.rightT[Future, EspError](())
        case Left(RemoteEnvironmentCommunicationError(StatusCodes.NotFound, _)) =>
          val userToForward = if (passUsernameInMigration) Some(loggedUser) else None
          createProcessOnRemote(localProcess, category, userToForward)
        case Left(other) => EitherT.leftT[Future, EspError](other)
      }
      usernameToPass = if (passUsernameInMigration) Some(RemoteUserName(loggedUser.username)) else None
      _ <- EitherT {
        saveProcess(
          localProcess,
          UpdateProcessComment(s"Scenario migrated from $environmentId by ${loggedUser.username}"),
          usernameToPass
        )
      }
    } yield ()).value
  }

  private def createProcessOnRemote(localProcess: DisplayableProcess, category: String, loggedUser: Option[LoggedUser])(
      implicit ec: ExecutionContext
  ): FutureE[Unit] = {
    val remoteUserNameHeader: List[HttpHeader] =
      loggedUser.map(user => RawHeader(RemoteUserName.headerName, user.username)).toList
    EitherT {
      invokeForSuccess(
        HttpMethods.POST,
        List("processes", localProcess.id, category),
        Query(("isFragment", localProcess.metaData.isFragment.toString)),
        HttpEntity.Empty,
        remoteUserNameHeader
      )
    }
  }

  // We need to be cautious when choosing maxParallelism of batchingExecutionContext as validation may call external systems and we don't want to overwhelm them with requests
  override def testMigration(
      processToInclude: BasicProcess => Boolean = _ => true,
      batchingExecutionContext: ExecutionContext
  )(implicit ec: ExecutionContext): Future[Either[EspError, List[TestMigrationResult]]] = {
    (for {
      allBasicProcesses <- EitherT(fetchProcesses)
      basicProcesses = allBasicProcesses.filterNot(_.isFragment).filter(processToInclude)
      basicFragments = allBasicProcesses.filter(_.isFragment).filter(processToInclude)
      processes <- fetchGroupByGroup(basicProcesses, batchingExecutionContext)
      fragments <- fetchGroupByGroup(basicFragments, batchingExecutionContext)
    } yield testModelMigrations.testMigrations(processes, fragments, batchingExecutionContext)).value
  }

  private def fetchGroupByGroup(
      basicProcesses: List[BasicProcess],
      batchingExecutionContext: ExecutionContext
  )(implicit ec: ExecutionContext): FutureE[List[ValidatedProcessDetails]] = {
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

  private def fetchProcesses(implicit ec: ExecutionContext): Future[Either[EspError, List[BasicProcess]]] = {
    invokeJson[List[BasicProcess]](HttpMethods.GET, List("processes"), Query(("isArchived", "false")))
  }

  private def fetchProcessVersion(id: String, remoteProcessVersion: Option[VersionId])(
      implicit ec: ExecutionContext
  ): Future[Either[EspError, ProcessDetails]] = {
    invokeJson[ProcessDetails](
      HttpMethods.GET,
      List("processes", id) ++ remoteProcessVersion.map(_.value.toString).toList,
      Query()
    )
  }

  private def fetchProcessDetails(
      id: String
  )(implicit ec: ExecutionContext): FutureE[Either[EspError, ProcessDetails]] = {
    EitherT(invokeJson[ProcessDetails](HttpMethods.GET, List("processes", id)).map(_.asRight))
  }

  private def fetchProcessesDetails(names: List[ProcessName])(implicit ec: ExecutionContext) = EitherT {
    invokeJson[List[ValidatedProcessDetails]](
      HttpMethods.GET,
      "processesDetails" :: Nil,
      Query(
        ("names", names.map(ns => URLEncoder.encode(ns.value, StandardCharsets.UTF_8)).mkString(",")),
        ("isArchived", "false"),
        ("skipNodeResults", "true"),
      )
    )
  }

  private def validateProcess(
      process: DisplayableProcess
  )(implicit ec: ExecutionContext): Future[Either[EspError, ValidationResult]] = {
    for {
      processToValidate <- Marshal(process).to[MessageEntity]
      validation <- invokeJson[ValidationResult](
        HttpMethods.POST,
        List("processValidation"),
        requestEntity = processToValidate
      )
    } yield validation
  }

  private def saveProcess(
      process: DisplayableProcess,
      comment: UpdateProcessComment,
      forwardedUserName: Option[RemoteUserName]
  )(implicit ec: ExecutionContext): Future[Either[EspError, ValidationResult]] = {
    for {
      processToSave <- Marshal(UpdateProcessCommand(process, comment, forwardedUserName))
        .to[MessageEntity](marshaller, ec)
      response <- invokeJson[ValidationResult](
        HttpMethods.PUT,
        List("processes", process.id),
        requestEntity = processToSave
      )
    } yield response
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
  )(implicit ec: ExecutionContext): Future[Either[EspError, T]] = {
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
