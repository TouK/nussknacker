package pl.touk.esp.ui.api

import java.time.LocalDateTime

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.headers.ContentDispositionTypes
import akka.http.scaladsl.model.{HttpMethods, HttpResponse, StatusCodes, headers}
import akka.http.scaladsl.server.{Directive, Directives, Route}
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import argonaut._
import cats.data.Validated.{Invalid, Valid}
import cats.data.Xor
import pl.touk.esp.engine.api.MetaData
import pl.touk.esp.engine.api.deployment.{GraphProcess, ProcessManager}
import pl.touk.esp.engine.canonicalgraph.CanonicalProcess
import pl.touk.esp.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.esp.ui.api.ProcessValidation.ValidationResult
import pl.touk.esp.ui.api.ProcessesResources.{UnmarshallError, WrongProcessId}
import pl.touk.esp.ui.process.displayedgraph.{DisplayableProcess, ProcessStatus}
import pl.touk.esp.ui.process.marshall.{DisplayableProcessCodec, ProcessConverter, ProcessTypeCodec, UiProcessMarshaller}
import pl.touk.esp.ui.process.repository.ProcessRepository
import pl.touk.esp.ui.process.repository.ProcessRepository._
import pl.touk.esp.ui.security.{LoggedUser, Permission}
import pl.touk.esp.ui.util.Argonaut62Support
import pl.touk.esp.ui.{BadRequestError, EspError, FatalError, NotFoundError}

import scala.concurrent.{ExecutionContext, Future}

class ProcessesResources(repository: ProcessRepository,
                         processManager: ProcessManager,
                         processConverter: ProcessConverter,
                         processValidation: ProcessValidation)
                        (implicit ec: ExecutionContext, mat: Materializer)
  extends Directives with Argonaut62Support {

  import argonaut.ArgonautShapeless._
  import pl.touk.esp.engine.optics.Implicits._
  import cats.instances.future._
  import cats.instances.option._
  import cats.instances.list._
  import cats.syntax.traverse._

  implicit val processTypeCodec = ProcessTypeCodec.codec

  implicit val localDateTimeEncode = EncodeJson.of[String].contramap[LocalDateTime](_.toString)

  implicit val displayableProcessCodec = DisplayableProcessCodec.codec

  implicit val validationResultEncode = EncodeJson.of[ValidationResult]

  implicit val processHistory = EncodeJson.of[ProcessHistoryEntry]

  implicit val processListEncode = EncodeJson.of[List[ProcessDetails]]

  implicit val printer: Json => String =
    PrettyParams.spaces2.copy(dropNullKeys = true, preserveOrder = true).pretty

  val uiProcessMarshaller = UiProcessMarshaller()

  def route(implicit user: LoggedUser): Route = {
    def authorizeMethod = extractMethod.flatMap[Unit] {
      case HttpMethods.POST | HttpMethods.PUT | HttpMethods.DELETE => authorize(user.hasPermission(Permission.Write))
      case HttpMethods.GET => authorize(user.hasPermission(Permission.Read))
      //czyli co??? options?
      case _ => Directive.Empty
    }
    authorizeMethod {
      path("processes") {
        get {
          complete {
            repository.fetchProcessesDetails()
          }
        }
      } ~ path("processes" / "status") {
        get {
          complete {
            for {
              processes <- repository.fetchProcessesDetails()
              processStatesForProcess <- fetchProcessStatesForProcesses(processes)
            } yield processStatesForProcess
          }
        }

      } ~ path("processes" / Segment) { processId =>
        get {
          complete {
            repository.fetchLatestProcessDetailsForProcessId(processId).map[ToResponseMarshallable] {
              case Some(process) => process
              case None => HttpResponse(status = StatusCodes.NotFound, entity = "Process not found")
            }
          }
        }
      } ~ path("processes" / Segment / LongNumber) { (processId, versionId) =>
        get {
          complete {
            repository.fetchProcessDetailsForId(processId, versionId).map[ToResponseMarshallable] {
              case Some(process) => process
              case None => HttpResponse(status = StatusCodes.NotFound, entity = "Process not found")
            }
          }
        }
      } ~ path("processes" / Segment / "json") { processId =>
        put {
          entity(as[DisplayableProcess]) { displayableProcess =>
            complete {
              val canonical = processConverter.fromDisplayable(displayableProcess)
              val json = uiProcessMarshaller.toJson(canonical, PrettyParams.nospace)
              repository.updateProcess(processId, GraphProcess(json)).map { result =>
                toResponse {
                  result.map(_ => processValidation.validate(canonical))
                }
              }
            }
          }
        }
      } ~ path("processes" / Segment / Segment) { (processId, category) =>
        authorize(user.categories.contains(category)) {
          post {
            complete {
              val emptyCanonical = CanonicalProcess(MetaData(id = processId), ExceptionHandlerRef(List()), List())
              val emptyProcess = uiProcessMarshaller.toJson(emptyCanonical, PrettyParams.nospace)

              repository.fetchLatestProcessDetailsForProcessId(processId).flatMap {
                case Some(_) => Future(HttpResponse(status = StatusCodes.BadRequest, entity = "Process already exists"))
                case None => repository.saveProcess(processId, category, GraphProcess(emptyProcess)).map {
                  case Xor.Left(error) => espErrorToHttp(error)
                  case Xor.Right(_) => HttpResponse(status = StatusCodes.Created)
                }
              }

            }
          }
        }

      } ~ path("processes" / Segment / "status") { processId =>
        get {
          complete {
            repository.fetchLatestProcessDetailsForProcessId(processId).flatMap[ToResponseMarshallable] {
              case Some(process) =>
                findJobStatus(process.name).map {
                  case Some(status) => status
                  case None => HttpResponse(status = StatusCodes.OK, entity = "Process is not running")
                }
              case None =>
                Future.successful(HttpResponse(status = StatusCodes.NotFound, entity = "Process not found"))
            }
          }
        }
      } ~ path("processes" / "export" / Segment) { processIdWithJson =>
        get {
          complete {
            val processId = processIdWithJson.replace(".json", "")
            repository.fetchLatestProcessDetailsForProcessId(processId).map {
              case Some(process) =>
                process.json.map { json =>
                  uiProcessMarshaller.toJson(processConverter.fromDisplayable(json), PrettyParams.spaces2)
                }.map { canJson =>
                  HttpResponse(status = StatusCodes.OK, entity = canJson,
                    headers = List(headers.`Content-Disposition`(ContentDispositionTypes.attachment, Map("filename" -> s"$processId.json")))
                  )
                }.getOrElse(HttpResponse(status = StatusCodes.NotFound, entity = "Process not found"))
              case None =>
                HttpResponse(status = StatusCodes.NotFound, entity = "Process not found")
            }
          }
        }
      } ~ path("processes" / "import" / Segment) { processId =>
        post {
          fileUpload("process") { case (metadata, byteSource) =>
            complete {
              readFile(byteSource).map[ToResponseMarshallable] { json =>
                (uiProcessMarshaller.fromJson(json) match {
                  case Valid(process) if process.metaData.id != processId => Invalid(WrongProcessId(processId, process.metaData.id))
                  case Valid(process) => Valid(process)
                  case Invalid(unmarshallError) => Invalid(UnmarshallError(unmarshallError.msg))
                }) match {
                  case Valid(process) => processConverter.toDisplayable(process)
                  case Invalid(error) => espErrorToHttp(error)
                }
              }
            }
          }
        }
      }
    }
  }

  def readFile(byteSource: Source[ByteString, Any]): Future[String] =
    byteSource.runWith(Sink.seq)
      .map(_.flatten.toArray).map(new String(_))


  private def fetchProcessStatesForProcesses(processes: List[ProcessDetails]): Future[Map[String, Option[ProcessStatus]]] = {
    processes.map(process => findJobStatus(process.name).map(status => process.name -> status)).sequence.map(_.toMap)
  }

  private def findJobStatus(processName: String)(implicit ec: ExecutionContext): Future[Option[ProcessStatus]] = {
    processManager.findJobStatus(processName).map(statusOpt => statusOpt.map(ProcessStatus.apply))
  }

  private def toResponse(xor: Xor[EspError, ValidationResult]): ToResponseMarshallable =
    xor match {
      case Xor.Right(validationResult) =>
        validationResult
      case Xor.Left(err) =>
        espErrorToHttp(err)
    }

  private def espErrorToHttp(error: EspError) = {
    val statusCode = error match {
      case e: NotFoundError => StatusCodes.NotFound
      case e: FatalError => StatusCodes.InternalServerError
      case e: BadRequestError => StatusCodes.BadRequest
      //unknown?
      case _ => StatusCodes.InternalServerError
    }
    HttpResponse(status = statusCode, entity = error.getMessage)
  }

}

object ProcessesResources {

  case class UnmarshallError(message: String) extends Exception(message) with FatalError

  case class WrongProcessId(processId: String, givenId: String) extends Exception(s"Process has id $givenId instead of $processId") with BadRequestError

  case class ProcessNotInitializedError(id: String) extends Exception(s"Process $id is not initialized") with NotFoundError

  case class NodeNotFoundError(processId: String, nodeId: String) extends Exception(s"Node $nodeId not found inside process $processId") with NotFoundError

}