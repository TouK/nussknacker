package pl.touk.esp.ui.api

import java.net.URLDecoder

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.{Directive, Directives, Route}
import akka.stream.Materializer
import argonaut._
import cats.data.Validated.{Invalid, Valid}
import cats.data.Xor
import pl.touk.esp.engine.api.MetaData
import pl.touk.esp.engine.api.deployment.{GraphProcess, ProcessManager}
import pl.touk.esp.engine.canonicalgraph.CanonicalProcess
import pl.touk.esp.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.esp.ui.api.ProcessValidation.ValidationResult
import pl.touk.esp.ui.api.ProcessesResources.{UnmarshallError, WrongProcessId}
import pl.touk.esp.ui.process.deployment.CheckStatus
import pl.touk.esp.ui.process.displayedgraph.{DisplayableProcess, ProcessStatus}
import pl.touk.esp.ui.process.marshall.{ProcessConverter, UiProcessMarshaller}
import pl.touk.esp.ui.process.repository.ProcessRepository
import pl.touk.esp.ui.process.repository.ProcessRepository._
import pl.touk.esp.ui.security.{LoggedUser, Permission}
import pl.touk.esp.ui.util.{AkkaHttpResponse, Argonaut62Support, MultipartUtils, PdfExporter}
import pl.touk.esp.ui.{BadRequestError, EspError, FatalError, NotFoundError}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration._
import EspErrorToHttp._

import scala.concurrent.{ExecutionContext, Future}

class ProcessesResources(repository: ProcessRepository,
                         managerActor: ActorRef,
                         processConverter: ProcessConverter,
                         processValidation: ProcessValidation)
                        (implicit ec: ExecutionContext, mat: Materializer)
  extends Directives with Argonaut62Support {

  import argonaut.ArgonautShapeless._
  import pl.touk.esp.ui.codec.UiCodecs._

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
            repository.fetchProcessesDetails().flatMap(fetchProcessStatesForProcesses)
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
        } ~ delete {
          complete {
            repository.deleteProcess(processId).map(toResponse(StatusCodes.OK))
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
                case None => repository.saveProcess(processId, category, GraphProcess(emptyProcess)).map(toResponse(StatusCodes.Created))
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
      } ~ path("processes" / "export" / Segment / LongNumber) { (processId, versionId) =>
        get {
          complete {
            repository.fetchProcessDetailsForId(processId, versionId).map { exportProcess }
          }
        }
      } ~ path("processes" / "exportToPdf" / Segment / LongNumber) { (processId, versionId) =>
          post {
            entity(as[Array[Byte]]) { (svg) =>
              complete {
                repository.fetchProcessDetailsForId(processId, versionId).map { exportProcessToPdf(new String(svg), _) }
             }
            }
          }
      } ~ path("processes" / "export" / Segment ) { processId =>
        get {
          complete {
            repository.fetchLatestProcessDetailsForProcessId(processId).map { exportProcess }
          }
        }
      } ~ path("processes" / "import" / Segment) { processId =>
        post {
          fileUpload("process") { case (metadata, byteSource) =>
            complete {
              MultipartUtils.readFile(byteSource).map[ToResponseMarshallable] { json =>
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

  private def exportProcess(processDetails: Option[ProcessDetails]) = processDetails match {
    case Some(process) =>
      process.json.map { json =>
        uiProcessMarshaller.toJson(processConverter.fromDisplayable(json), PrettyParams.spaces2)
      }.map { canonicalJson =>
        AkkaHttpResponse.asFile(canonicalJson, s"${process.id}.json")
      }.getOrElse(HttpResponse(status = StatusCodes.NotFound, entity = "Process not found"))
    case None =>
      HttpResponse(status = StatusCodes.NotFound, entity = "Process not found")
  }

  private def exportProcessToPdf(svg: String, processDetails: Option[ProcessDetails]) = processDetails match {
    case Some(process) =>
      process.json.map { json =>
        PdfExporter.exportToPdf(svg, process, json)
      }.map { pdf =>
        HttpResponse(status = StatusCodes.OK, entity = pdf)
      }.getOrElse(HttpResponse(status = StatusCodes.NotFound, entity = "Process not found"))
    case None =>
      HttpResponse(status = StatusCodes.NotFound, entity = "Process not found")
  }


  private def fetchProcessStatesForProcesses(processes: List[ProcessDetails]): Future[Map[String, Option[ProcessStatus]]] = {
    import cats.instances.future._
    import cats.instances.list._
    import cats.syntax.traverse._
    processes.map(process => findJobStatus(process.name).map(status => process.name -> status)).sequence.map(_.toMap)
  }

  private def findJobStatus(processName: String)(implicit ec: ExecutionContext): Future[Option[ProcessStatus]] = {
    implicit val timeout = Timeout(1 minute)
    (managerActor ? CheckStatus(processName)).mapTo[Option[ProcessStatus]]
  }

  private def toResponse(xor: Xor[EspError, ValidationResult]): ToResponseMarshallable =
    xor match {
      case Xor.Right(validationResult) =>
        validationResult
      case Xor.Left(err) =>
        espErrorToHttp(err)
    }

  private def toResponse(okStatus: StatusCode)(xor: Xor[EspError, Unit]): HttpResponse = xor match {
    case Xor.Left(error) => espErrorToHttp(error)
    case Xor.Right(_) => HttpResponse(status = okStatus)
  }


}

object ProcessesResources {

  case class UnmarshallError(message: String) extends Exception(message) with FatalError

  case class WrongProcessId(processId: String, givenId: String) extends Exception(s"Process has id $givenId instead of $processId") with BadRequestError

  case class ProcessNotInitializedError(id: String) extends Exception(s"Process $id is not initialized") with NotFoundError

  case class NodeNotFoundError(processId: String, nodeId: String) extends Exception(s"Node $nodeId not found inside process $processId") with NotFoundError

}