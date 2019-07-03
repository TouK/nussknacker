package pl.touk.nussknacker.ui.process.migrate

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials}
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import argonaut.DecodeJson
import cats.data.EitherT
import cats.implicits._
import pl.touk.http.argonaut.Argonaut62Support
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.ui.EspError
import pl.touk.nussknacker.ui.EspError.XError
import pl.touk.nussknacker.ui.codec.UiCodecs
import pl.touk.nussknacker.ui.process.ProcessToSave
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.InvalidProcessTypeError
import pl.touk.nussknacker.restmodel.processdetails.{BasicProcess, ProcessDetails, ProcessHistoryEntry, ValidatedProcessDetails}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.ProcessComparator
import pl.touk.nussknacker.ui.util.ProcessComparator.Difference
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{ValidationErrors, ValidationResult}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

trait RemoteEnvironment {

  def targetEnvironmentId: String

  def compare(localProcess: DisplayableProcess, remoteProcessVersion: Option[Long],
              businessView: Boolean = false)(implicit ec: ExecutionContext) : Future[Either[EspError, Map[String, Difference]]]

  def processVersions(processName: ProcessName)(implicit ec: ExecutionContext) : Future[List[ProcessHistoryEntry]]

  def migrate(localProcess: DisplayableProcess, category: String)(implicit ec: ExecutionContext, loggedUser: LoggedUser) : Future[Either[EspError, Unit]]

  def testMigration(implicit ec: ExecutionContext) : Future[Either[EspError, List[TestMigrationResult]]]
}

case class RemoteEnvironmentCommunicationError(statusCode: StatusCode, getMessage: String) extends EspError

case class MigrationValidationError(errors: ValidationErrors) extends EspError {
  override def getMessage : String = {
    val messages = errors.globalErrors.map(_.message) ++
      errors.processPropertiesErrors.map(_.message) ++ errors.invalidNodes.map { case(node, nerror) => s"$node - ${nerror.map(_.message).mkString(", ")}"}
    s"Cannot migrate, following errors occurred: ${messages.mkString(", ")}"
  }
}

case class HttpRemoteEnvironmentConfig(user: String, password: String, targetEnvironmentId: String, remoteConfig: StandardRemoteEnvironmentConfig)

class HttpRemoteEnvironment(httpConfig: HttpRemoteEnvironmentConfig,
                            val testModelMigrations: TestModelMigrations,
                            val environmentId: String
                           )(implicit as: ActorSystem, val materializer: Materializer) extends StandardRemoteEnvironment {
  override val config: StandardRemoteEnvironmentConfig = httpConfig.remoteConfig

  override def targetEnvironmentId: String = httpConfig.targetEnvironmentId

  override protected def request(uri: Uri, method: HttpMethod, request: MessageEntity): Future[HttpResponse] = {
    // fixme: this shouldn't be done this way but I cannot get Akka client to send that many requests in a row otherwise
    Source.single(
      HttpRequest(uri = uri.toRelative, method = method, entity = request,
        headers = List(Authorization(BasicHttpCredentials(httpConfig.user, httpConfig.password))))
    ).via(Http().outgoingConnection(uri.authority.host.address(), uri.authority.port))
      .runWith(Sink.head)
  }
}

case class StandardRemoteEnvironmentConfig(uri: String,
                                           migrationTimeout: Duration,
                                           migrationBatchSize: Int)

//TODO: extract interface to remote environment?
trait StandardRemoteEnvironment extends Argonaut62Support with RemoteEnvironment with UiCodecs {

  def environmentId: String

  def config: StandardRemoteEnvironmentConfig

  def testModelMigrations: TestModelMigrations

  def baseUri = Uri(config.uri)

  implicit def materializer: Materializer

  private def invoke[T](method: HttpMethod, pathParts: List[String], queryString: Option[String] = None, requestEntity: RequestEntity = HttpEntity.Empty)
                       (f: HttpResponse => Future[T])(implicit ec: ExecutionContext): Future[T] = {
    val pathEncoded = pathParts.foldLeft[Path](baseUri.path)(_ / _)
    val uri = baseUri.withPath(pathEncoded).withRawQueryString(queryString.getOrElse(""))

    request(uri, method, requestEntity) flatMap f
  }

  private def invokeForSuccess(method: HttpMethod, pathParts: List[String])(implicit ec: ExecutionContext): Future[XError[Unit]] =
    invoke(method, pathParts) { response =>
      if (response.status.isSuccess()) {
        response.discardEntityBytes()
        Future.successful(().asRight)
      } else {
        Unmarshaller.stringUnmarshaller(response.entity)
          .map(error => RemoteEnvironmentCommunicationError(response.status, error).asLeft)
      }
    }

  private def invokeStatus(method: HttpMethod, pathParts: List[String])(implicit ec: ExecutionContext): Future[StatusCode] =
    invoke(method, pathParts) { response =>
      response.discardEntityBytes()
      Future.successful(response.status)
    }

  private def invokeJson[T: DecodeJson](method: HttpMethod, pathParts: List[String],
                                        queryString: Option[String] = None, requestEntity: RequestEntity = HttpEntity.Empty)
                                       (implicit ec: ExecutionContext): Future[Either[EspError, T]] = {
    invoke(method, pathParts, queryString, requestEntity) { response =>
      if (response.status.isSuccess()) {
        Unmarshal(response.entity).to[T].map(Either.right)
      } else {
        Unmarshaller.stringUnmarshaller(response.entity)
          .map(error => Either.left(RemoteEnvironmentCommunicationError(response.status, error)))
      }
    }
  }

  override def processVersions(processName: ProcessName)(implicit ec: ExecutionContext): Future[List[ProcessHistoryEntry]] =
    invokeJson[ProcessDetails](HttpMethods.GET, List("processes", processName.value), Some("businessView=true")).map { result =>
      result.fold(_ => List(), _.history)
    }

  protected def request(path: Uri, method: HttpMethod, request: MessageEntity): Future[HttpResponse]

  override def compare(localProcess: DisplayableProcess, remoteProcessVersion: Option[Long], businessView: Boolean = false)(implicit ec: ExecutionContext) : Future[Either[EspError, Map[String, Difference]]] = {
    val id = localProcess.id

    (for {
      //TODO: move urls to some constants...
      process <- EitherT(invokeJson[ProcessDetails](HttpMethods.GET, List("processes", id) ++ remoteProcessVersion.map(_.toString).toList, Some(s"businessView=$businessView")))
      compared <- EitherT.fromEither[Future](compareProcess(id, localProcess)(process))
    } yield compared).value

  }

  override def migrate(localProcess: DisplayableProcess, category: String)
                      (implicit ec: ExecutionContext, loggedUser: LoggedUser) : Future[Either[EspError, Unit]]= {

    val comment = s"Process migrated from $environmentId by ${loggedUser.id}"
    (for {
      processToValidate <- EitherT.right(Marshal(localProcess).to[MessageEntity])
      validation <- EitherT(invokeJson[ValidationResult](HttpMethods.POST, List("processValidation"), requestEntity = processToValidate))
      _ <- EitherT.fromEither[Future](if (validation.errors != ValidationErrors.success) Left[EspError, Unit](MigrationValidationError(validation.errors)) else Right(()))

      _ <- createRemoteProcessIfNotExist(localProcess, category)

      processToSave <- EitherT.right(Marshal(ProcessToSave(localProcess, comment)).to[MessageEntity])
      _ <- EitherT(invokeJson[ValidationResult](HttpMethods.PUT, List("processes", localProcess.id), requestEntity = processToSave))
    } yield ()).value
  }

  private def createRemoteProcessIfNotExist(localProcess: DisplayableProcess, category: String)
                                           (implicit ec: ExecutionContext): EitherT[Future, EspError, Unit] = {
    EitherT {
      invokeStatus(HttpMethods.GET, List("processes", localProcess.id)).flatMap { status =>
        if (status == StatusCodes.NotFound)
          invokeForSuccess(HttpMethods.POST, List("processes", localProcess.id, category))
        else
          Future.successful(().asRight)
      }
    }
  }

  private def compareProcess(id: String, localProcess: DisplayableProcess)(remoteProcessDetails: ProcessDetails) : Either[EspError, Map[String, Difference]] = remoteProcessDetails.json match {
    case Some(remoteProcess) => Right(ProcessComparator.compare(localProcess, remoteProcess))
    case None => Left(InvalidProcessTypeError(id))
  }

  override def testMigration(implicit ec: ExecutionContext): Future[Either[EspError, List[TestMigrationResult]]] = {
    (for {
      basicProcesses <- EitherT(invokeJson[List[BasicProcess]](HttpMethods.GET, List("processes")))
      processes      <- fetchGroupByGroup(basicProcesses, basicProcess => EitherT(invokeJson[ValidatedProcessDetails](HttpMethods.GET, List("processes", basicProcess.name))))
      subprocesses   <- EitherT(invokeJson[List[ValidatedProcessDetails]](HttpMethods.GET, List("subProcessesDetails")))
    } yield testModelMigrations.testMigrations(processes, subprocesses)).value
  }

  private def fetchGroupByGroup[T](basicProcesses: List[BasicProcess],
                                   getOne: BasicProcess => EitherT[Future, EspError, ValidatedProcessDetails])
                                  (implicit ec: ExecutionContext): EitherT[Future, EspError, List[ValidatedProcessDetails]] = {
    EitherT(Future.successful(Await.result(
      Future.sequence(
        basicProcesses.grouped(config.migrationBatchSize)
          .flatMap(_.map(getOne(_).value))
          .toList
      ).map(_.sequence),
      config.migrationTimeout
    )))
  }
}
