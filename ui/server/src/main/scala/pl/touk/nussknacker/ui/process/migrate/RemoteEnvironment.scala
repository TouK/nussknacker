package pl.touk.nussknacker.ui.process.migrate

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials}
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import akka.stream.Materializer
import argonaut.DecodeJson
import cats.data.EitherT
import cats.implicits._
import pl.touk.http.argonaut.Argonaut62Support
import pl.touk.nussknacker.ui.EspError
import pl.touk.nussknacker.ui.codec.UiCodecs
import pl.touk.nussknacker.ui.process.ProcessToSave
import pl.touk.nussknacker.ui.process.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.{InvalidProcessTypeError, ProcessDetails, ProcessHistoryEntry, ValidatedProcessDetails}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.ProcessComparator
import pl.touk.nussknacker.ui.util.ProcessComparator.Difference
import pl.touk.nussknacker.ui.validation.ValidationResults.{ValidationErrors, ValidationResult}

import scala.concurrent.{ExecutionContext, Future}

trait RemoteEnvironment {

  def targetEnvironmentId: String

  def compare(localProcess: DisplayableProcess, remoteProcessVersion: Option[Long],
              businessView: Boolean = false)(implicit ec: ExecutionContext) : Future[Either[EspError, Map[String, Difference]]]

  def processVersions(processId: String)(implicit ec: ExecutionContext) : Future[List[ProcessHistoryEntry]]

  def migrate(localProcess: DisplayableProcess)(implicit ec: ExecutionContext, loggedUser: LoggedUser) : Future[Either[EspError, Unit]]

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

case class HttpRemoteEnvironmentConfig(url: String, user: String, password: String, environmentId: String)


class HttpRemoteEnvironment(config: HttpRemoteEnvironmentConfig,
                            val testModelMigrations: TestModelMigrations,
                            val environmentId: String)(implicit as: ActorSystem, val materializer: Materializer) extends StandardRemoteEnvironment {

  override def targetEnvironmentId : String = config.environmentId

  val http = Http()

  override def baseUrl: Uri = config.url

  override protected def request(uri: Uri, method: HttpMethod, request: MessageEntity): Future[HttpResponse] = {
    http.singleRequest(HttpRequest(uri = uri, method = method, entity = request,
      headers = List(Authorization(BasicHttpCredentials(config.user, config.password)))))
  }
}

//TODO: extract interface to remote environment?
trait StandardRemoteEnvironment extends Argonaut62Support with RemoteEnvironment with UiCodecs {

  def environmentId: String

  def testModelMigrations: TestModelMigrations

  def baseUrl: Uri

  implicit def materializer: Materializer

  private def invoke[T:DecodeJson](method: HttpMethod, pathParts: List[String], queryString: Option[String] = None, requestEntity: RequestEntity = HttpEntity.Empty)(implicit ec: ExecutionContext)
    : Future[Either[EspError, T]]= {
    val pathEncoded = pathParts.foldLeft[Path](baseUrl.path)(_ / _)
    val uri = baseUrl
      .withPath(pathEncoded)
      .withRawQueryString(queryString.getOrElse(""))
    request(uri, method, requestEntity).flatMap { response =>
      if (response.status.isSuccess()) {
        Unmarshal(response.entity).to[T].map[Either[EspError, T]](Right(_))
      } else {
        Unmarshaller
          .stringUnmarshaller(response.entity).map(error => Left[EspError, T](RemoteEnvironmentCommunicationError(response.status, error)))
      }
    }
  }

  override def processVersions(processId: String)(implicit ec: ExecutionContext): Future[List[ProcessHistoryEntry]] =
    invoke[ProcessDetails](HttpMethods.GET, List("processes", processId), Some("businessView=true")).map { result =>
      result.fold(_ => List(), _.history)
    }

  protected def request(path: Uri, method: HttpMethod, request: MessageEntity): Future[HttpResponse]

  override def compare(localProcess: DisplayableProcess, remoteProcessVersion: Option[Long], businessView: Boolean = false)(implicit ec: ExecutionContext) : Future[Either[EspError, Map[String, Difference]]] = {
    val id = localProcess.id

    (for {
      //TODO: move urls to some constants...
      process <- EitherT(invoke[ProcessDetails](HttpMethods.GET, List("processes", id) ++ remoteProcessVersion.map(_.toString).toList, Some(s"businessView=$businessView")))
      compared <- EitherT.fromEither[Future](compareProcess(id, localProcess)(process))
    } yield compared).value

  }

  override def migrate(localProcess: DisplayableProcess)(implicit ec: ExecutionContext, loggedUser: LoggedUser) : Future[Either[EspError, Unit]]= {

    val comment = s"Process migrated from $environmentId by ${loggedUser.id}"
    (for {
      processToValidate <- EitherT.right(Marshal(localProcess).to[MessageEntity])
      validation <- EitherT(invoke[ValidationResult](HttpMethods.POST, List("processValidation"), requestEntity = processToValidate))
      _ <- EitherT.fromEither[Future](if (validation.errors != ValidationErrors.success) Left[EspError, Unit](MigrationValidationError(validation.errors)) else Right(()))
      processToSave <- EitherT.right(Marshal(ProcessToSave(localProcess, comment)).to[MessageEntity])
      result <- EitherT(invoke[ValidationResult](HttpMethods.PUT, List("processes", localProcess.id), requestEntity = processToSave))
    } yield ()).value
  }

  private def compareProcess(id: String, localProcess: DisplayableProcess)(remoteProcessDetails: ProcessDetails) : Either[EspError, Map[String, Difference]] = remoteProcessDetails.json match {
    case Some(remoteProcess) => Right(ProcessComparator.compare(localProcess, remoteProcess))
    case None => Left(InvalidProcessTypeError(id))
  }

  override def testMigration(implicit ec: ExecutionContext): Future[Either[EspError, List[TestMigrationResult]]] = {
    (for {
      processes <- EitherT(invoke[List[ValidatedProcessDetails]](HttpMethods.GET,List("processesDetails")))
      subprocesses <- EitherT(invoke[List[ValidatedProcessDetails]](HttpMethods.GET,List("subProcessesDetails")))
    } yield testModelMigrations.testMigrations(processes, subprocesses)).value
  }
}
