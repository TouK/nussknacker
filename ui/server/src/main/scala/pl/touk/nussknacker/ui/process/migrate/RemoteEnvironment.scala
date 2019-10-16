package pl.touk.nussknacker.ui.process.migrate

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.Uri.{Path, Query}
import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials}
import akka.http.scaladsl.model.{RequestEntity, _}
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import akka.stream.Materializer
import cats.data.EitherT
import cats.implicits._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Decoder
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.restmodel.processdetails.{BasicProcess, ProcessDetails, ProcessHistoryEntry, ValidatedProcessDetails}
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{ValidationErrors, ValidationResult}
import pl.touk.nussknacker.ui.EspError
import pl.touk.nussknacker.ui.EspError.XError
import pl.touk.nussknacker.ui.process.ProcessToSave
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.InvalidProcessTypeError
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.ProcessComparator
import pl.touk.nussknacker.ui.util.ProcessComparator.Difference

import scala.concurrent.{ExecutionContext, Future}

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

case class HttpRemoteEnvironmentConfig(user: String, password: String, targetEnvironmentId: String,
                                       remoteConfig: StandardRemoteEnvironmentConfig)

class HttpRemoteEnvironment(httpConfig: HttpRemoteEnvironmentConfig,
                            val testModelMigrations: TestModelMigrations,
                            val environmentId: String)
                           (implicit as: ActorSystem, val materializer: Materializer, ec: ExecutionContext) extends StandardRemoteEnvironment {
  override val config: StandardRemoteEnvironmentConfig = httpConfig.remoteConfig

  override def targetEnvironmentId: String = httpConfig.targetEnvironmentId

  val http = Http()

  override protected def request(uri: Uri, method: HttpMethod, request: MessageEntity): Future[HttpResponse] = {
    http.singleRequest(HttpRequest(uri = uri, method = method, entity = request,
      headers = List(Authorization(BasicHttpCredentials(httpConfig.user, httpConfig.password)))))
  }
}

case class StandardRemoteEnvironmentConfig(uri: String, batchSize: Int)

//TODO: extract interface to remote environment?
trait StandardRemoteEnvironment extends FailFastCirceSupport with RemoteEnvironment {

  def environmentId: String

  def config: StandardRemoteEnvironmentConfig

  def testModelMigrations: TestModelMigrations

  def baseUri = Uri(config.uri)

  implicit def materializer: Materializer

  override def processVersions(processName: ProcessName)(implicit ec: ExecutionContext): Future[List[ProcessHistoryEntry]] =
    invokeJson[ProcessDetails](HttpMethods.GET, List("processes", processName.value), Query(("businessView", true.toString))).map { result =>
      result.fold(_ => List(), _.history)
    }

  protected def request(path: Uri, method: HttpMethod, request: MessageEntity): Future[HttpResponse]

  override def compare(localProcess: DisplayableProcess, remoteProcessVersion: Option[Long], businessView: Boolean = false)(implicit ec: ExecutionContext) : Future[Either[EspError, Map[String, Difference]]] = {
    val id = localProcess.id
    (for {
      process <- EitherT(fetchProcessVersion(id, remoteProcessVersion, businessView))
      compared <- EitherT.fromEither[Future](compareProcess(id, localProcess)(process))
    } yield compared).value
  }

  private def compareProcess(id: String, localProcess: DisplayableProcess)(remoteProcessDetails: ProcessDetails) : Either[EspError, Map[String, Difference]] = remoteProcessDetails.json match {
    case Some(remoteProcess) => Right(ProcessComparator.compare(localProcess, remoteProcess))
    case None => Left(InvalidProcessTypeError(id))
  }

  override def migrate(localProcess: DisplayableProcess, category: String)
                      (implicit ec: ExecutionContext, loggedUser: LoggedUser) : Future[Either[EspError, Unit]] = {
    (for {
      validation <- EitherT(validateProcess(localProcess))
      _ <- EitherT.fromEither[Future](if (validation.errors != ValidationErrors.success) Left[EspError, Unit](MigrationValidationError(validation.errors)) else Right(()))
      _ <- createRemoteProcessIfNotExist(localProcess, category)
      _ <- EitherT.right[EspError](saveProcess(localProcess, comment = s"Process migrated from $environmentId by ${loggedUser.id}"))
    } yield ()).value
  }

  private def createRemoteProcessIfNotExist(localProcess: DisplayableProcess, category: String)
                                           (implicit ec: ExecutionContext): EitherT[Future, EspError, Unit] = {
    EitherT {
      invokeStatus(HttpMethods.GET, List("processes", localProcess.id)).flatMap { status =>
        if (status == StatusCodes.NotFound)
          invokeForSuccess(HttpMethods.POST, List("processes", localProcess.id, category), Query(("isSubprocess", localProcess.metaData.isSubprocess.toString)))
        else
          Future.successful(().asRight)
      }
    }
  }

  override def testMigration(implicit ec: ExecutionContext): Future[Either[EspError, List[TestMigrationResult]]] = {
    (for {
      basicProcesses <- EitherT(fetchProcesses)
      processes      <- fetchGroupByGroup(basicProcesses)
      subProcesses   <- EitherT(fetchSubProcessesDetails)
    } yield testModelMigrations.testMigrations(processes, subProcesses)).value
  }

  private def fetchGroupByGroup[T](basicProcesses: List[BasicProcess])
                                  (implicit ec: ExecutionContext): EitherT[Future, EspError, List[ValidatedProcessDetails]] = {
    basicProcesses.map(_.name).grouped(config.batchSize)
      .foldLeft(EitherT.rightT[Future, EspError](List.empty[ValidatedProcessDetails])) { case (acc, processesGroup) =>
        for {
          current <- acc
          fetched <- fetchProcessesDetails(processesGroup)
        } yield current ::: fetched
      }
  }

  private def fetchProcesses(implicit ec: ExecutionContext): Future[Either[EspError, List[BasicProcess]]] = {
    invokeJson[List[BasicProcess]](HttpMethods.GET, List("processes"))
  }

  private def fetchProcessVersion(id: String, remoteProcessVersion: Option[Long], businessView: Boolean)
                                 (implicit ec: ExecutionContext): Future[Either[EspError, ProcessDetails]] = {
    invokeJson[ProcessDetails](HttpMethods.GET, List("processes", id) ++ remoteProcessVersion.map(_.toString).toList, Query(("businessView", businessView.toString)))
  }

  private def fetchProcessesDetails(names: List[String])(implicit ec: ExecutionContext) = EitherT {
    invokeJson[List[ValidatedProcessDetails]](
      HttpMethods.GET,
      "processesDetails" :: Nil,
      Query(("names", names.map(URLEncoder.encode(_, StandardCharsets.UTF_8.displayName())).mkString(",")))
    )
  }

  private def fetchSubProcessesDetails(implicit ec: ExecutionContext): Future[Either[EspError, List[ValidatedProcessDetails]]] = {
    invokeJson[List[ValidatedProcessDetails]](HttpMethods.GET, List("subProcessesDetails"))
  }

  private def validateProcess(process: DisplayableProcess)(implicit ec: ExecutionContext): Future[Either[EspError, ValidationResult]] = {
    for {
      processToValidate <- Marshal(process).to[MessageEntity]
      validation <- invokeJson[ValidationResult](HttpMethods.POST, List("processValidation"), requestEntity = processToValidate)
    } yield validation
  }

  private def saveProcess(process: DisplayableProcess, comment: String)(implicit ec: ExecutionContext): Future[Unit] = {
    for {
      processToSave <- Marshal(ProcessToSave(process, comment)).to[MessageEntity](marshaller, ec)
      _ <- invokeJson[ValidationResult](HttpMethods.PUT, List("processes", process.id), requestEntity = processToSave)
    } yield ()
  }

  private def invoke[T](method: HttpMethod, pathParts: List[String], query: Query = Query.Empty, requestEntity: RequestEntity = HttpEntity.Empty)
                       (f: HttpResponse => Future[T])(implicit ec: ExecutionContext): Future[T] = {
    val pathEncoded = pathParts.foldLeft[Path](baseUri.path)(_ / _)
    val uri = baseUri.withPath(pathEncoded).withQuery(query)

    request(uri, method, requestEntity) flatMap f
  }

  private def invokeForSuccess(method: HttpMethod, pathParts: List[String], query: Query = Query.Empty)(implicit ec: ExecutionContext): Future[XError[Unit]] =
    invoke(method, pathParts, query) { response =>
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

  private def invokeJson[T: Decoder](method: HttpMethod, pathParts: List[String],
                                     query: Query = Query.Empty, requestEntity: RequestEntity = HttpEntity.Empty)
                                    (implicit ec: ExecutionContext): Future[Either[EspError, T]] = {
    invoke(method, pathParts, query, requestEntity) { response =>
      if (response.status.isSuccess()) {
        Unmarshal(response.entity).to[T].map(Either.right)
      } else {
        Unmarshaller.stringUnmarshaller(response.entity)
          .map(error => Either.left(RemoteEnvironmentCommunicationError(response.status, error)))
      }
    }
  }
}
