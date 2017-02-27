package pl.touk.esp.ui.api

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.{Directive, Directives, Route}
import akka.stream.Materializer
import argonaut._
import cats.data.Validated.{Invalid, Valid}
import cats.data.{Xor, XorT}
import cats.instances.future._
import pl.touk.esp.engine.api.MetaData
import pl.touk.esp.engine.api.deployment.{GraphProcess, ProcessManager}
import pl.touk.esp.engine.canonicalgraph.CanonicalProcess
import pl.touk.esp.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.esp.ui.api.ProcessValidation.ValidationResult
import pl.touk.esp.ui.api.ProcessesResources.{UnmarshallError, WrongProcessId}
import pl.touk.esp.ui.process.deployment.CheckStatus
import pl.touk.esp.ui.process.displayedgraph.{DisplayableProcess, ProcessStatus}
import pl.touk.esp.ui.process.marshall.{ProcessConverter, UiProcessMarshaller}
import pl.touk.esp.ui.process.repository.{ProcessActivityRepository, ProcessRepository}
import pl.touk.esp.ui.process.repository.ProcessRepository._
import pl.touk.esp.ui.security.{LoggedUser, Permission}
import pl.touk.esp.ui.util.{AkkaHttpResponse, Argonaut62Support, MultipartUtils, PdfExporter}
import pl.touk.esp.ui.{BadRequestError, EspError, FatalError, NotFoundError}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration._
import EspErrorToHttp._
import pl.touk.esp.ui.db.entity.ProcessEntity.ProcessingType
import pl.touk.esp.ui.db.entity.ProcessEntity.ProcessingType.ProcessingType
import pl.touk.esp.ui.process.repository.ProcessActivityRepository.ProcessActivity

import scala.concurrent.{ExecutionContext, Future}

class ProcessesResources(repository: ProcessRepository,
                         managerActor: ActorRef,
                         processActivityRepository: ProcessActivityRepository,
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
              val canonical = ProcessConverter.fromDisplayable(displayableProcess)
              val json = uiProcessMarshaller.toJson(canonical, PrettyParams.nospace)

              (for {
                validation <- XorT.fromXor[Future](processValidation.validate(displayableProcess).fatalAsError)
                result <- XorT(repository.updateProcess(processId, GraphProcess(json)))
              } yield validation).value.map(toResponse)

            }
          }
        }
      } ~ path("processes" / Segment / Segment / Segment) { (processId, category, processingType) =>
        authorize(user.categories.contains(category)) {
          post {
            complete {
              val emptyCanonical = CanonicalProcess(MetaData(id = processId), ExceptionHandlerRef(List()), List())
              val emptyProcess = uiProcessMarshaller.toJson(emptyCanonical, PrettyParams.nospace)

              repository.fetchLatestProcessDetailsForProcessId(processId).flatMap {
                case Some(_) => Future(HttpResponse(status = StatusCodes.BadRequest, entity = "Process already exists"))
                case None => repository.saveNewProcess(processId, category, GraphProcess(emptyProcess), ProcessingType.withName(processingType)).map(toResponse(StatusCodes.Created))
              }

            }
          }
        }

      } ~ path("processes" / Segment / "status") { processId =>
        get {
          complete {
            repository.fetchLatestProcessDetailsForProcessId(processId).flatMap[ToResponseMarshallable] {
              case Some(process) =>
                findJobStatus(process.name, process.processingType).map {
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
                 repository.fetchProcessDetailsForId(processId, versionId).flatMap { process =>
                   processActivityRepository.findActivity(processId).map(exportProcessToPdf(new String(svg), process, _)) }
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
                  case Valid(process) =>
                    repository.fetchLatestProcessDetailsForProcessIdXor(processId).map { detailsXor =>
                      val validatedProcess = detailsXor.map(details =>
                        ProcessConverter.toDisplayable(process, details.processingType).validated(processValidation)
                      )
                      toResponseXor(validatedProcess)
                    }

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
        uiProcessMarshaller.toJson(ProcessConverter.fromDisplayable(json), PrettyParams.spaces2)
      }.map { canonicalJson =>
        AkkaHttpResponse.asFile(canonicalJson, s"${process.id}.json")
      }.getOrElse(HttpResponse(status = StatusCodes.NotFound, entity = "Process not found"))
    case None =>
      HttpResponse(status = StatusCodes.NotFound, entity = "Process not found")
  }

  private def exportProcessToPdf(svg: String, processDetails: Option[ProcessDetails], processActivity: ProcessActivity) = processDetails match {
    case Some(process) =>
      process.json.map { json =>
        PdfExporter.exportToPdf(svg, process, processActivity, json)
      }.map { pdf =>
        HttpResponse(status = StatusCodes.OK, entity = pdf)
      }.getOrElse(HttpResponse(status = StatusCodes.NotFound, entity = "Process not found"))
    case None =>
      HttpResponse(status = StatusCodes.NotFound, entity = "Process not found")
  }


  private def fetchProcessStatesForProcesses(processes: List[ProcessDetails])(implicit user: LoggedUser): Future[Map[String, Option[ProcessStatus]]] = {
    import cats.instances.future._
    import cats.instances.list._
    import cats.syntax.traverse._
    processes.map(process => findJobStatus(process.name, process.processingType).map(status => process.name -> status)).sequence.map(_.toMap)
  }

  private def findJobStatus(processName: String, processingType: ProcessingType)(implicit ec: ExecutionContext, user: LoggedUser): Future[Option[ProcessStatus]] = {
    implicit val timeout = Timeout(1 minute)
    (managerActor ? CheckStatus(processName, user)).mapTo[Option[ProcessStatus]]
  }


}

object ProcessesResources {

  case class UnmarshallError(message: String) extends Exception(message) with FatalError

  case class WrongProcessId(processId: String, givenId: String) extends Exception(s"Process has id $givenId instead of $processId") with BadRequestError

  case class ProcessNotInitializedError(id: String) extends Exception(s"Process $id is not initialized") with NotFoundError

  case class NodeNotFoundError(processId: String, nodeId: String) extends Exception(s"Node $nodeId not found inside process $processId") with NotFoundError

}