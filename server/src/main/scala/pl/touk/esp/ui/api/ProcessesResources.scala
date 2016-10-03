package pl.touk.esp.ui.api

import java.time.LocalDateTime

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives
import argonaut._
import cats.data.Xor
import pl.touk.esp.engine.api.deployment.{GraphProcess, ProcessManager, ProcessState}
import pl.touk.esp.engine.canonicalgraph.canonicalnode._
import pl.touk.esp.engine.compile.ProcessCompilationError
import pl.touk.esp.engine.graph.node.NodeData
import pl.touk.esp.engine.marshall.ProcessMarshaller
import pl.touk.esp.ui.api.ProcessValidation.ValidationResult
import pl.touk.esp.ui.api.ProcessesResources._
import pl.touk.esp.ui.process.displayedgraph.{DisplayableProcess, ProcessProperties, ProcessStatus}
import pl.touk.esp.ui.process.marshall.{DisplayableProcessCodec, ProcessConverter, ProcessTypeCodec}
import pl.touk.esp.ui.process.repository.ProcessRepository
import pl.touk.esp.ui.process.repository.ProcessRepository._
import pl.touk.esp.ui.util.Argonaut62Support
import pl.touk.esp.ui.{BadRequestError, EspError, FatalError, NotFoundError}

import scala.concurrent.{ExecutionContext, Future}

class ProcessesResources(repository: ProcessRepository,
                         processManager: ProcessManager,
                         processConverter: ProcessConverter,
                         processValidation: ProcessValidation)
                        (implicit ec: ExecutionContext)
  extends Directives with Argonaut62Support {

  import argonaut.ArgonautShapeless._
  import pl.touk.esp.engine.optics.Implicits._

  implicit val processTypeCodec = ProcessTypeCodec.codec

  implicit val localDateTimeEncode = EncodeJson.of[String].contramap[LocalDateTime](_.toString)

  implicit val displayableProcessCodec = DisplayableProcessCodec.codec

  implicit val displayableProcessNodeDecoder = DisplayableProcessCodec.nodeDecoder

  implicit val displayableProcessNodeEncoder = DisplayableProcessCodec.nodeEncoder

  implicit val validationResultEncode = EncodeJson.of[ValidationResult]

  implicit val processListEncode = EncodeJson.of[List[ProcessDetails]]

  implicit val printer: Json => String =
    PrettyParams.spaces2.copy(dropNullKeys = true, preserveOrder = true).pretty

  import cats.instances.future._
  import cats.instances.list._
  import cats.instances.option._
  import cats.syntax.traverse._

  val route =
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

    } ~ path("processes" / Segment) { id =>
      get {
        complete {
          repository.fetchProcessDetailsById(id).map[ToResponseMarshallable] {
            case Some(process) =>
              process
            case None =>
              HttpResponse(
                status = StatusCodes.NotFound,
                entity = "Process not found"
              )
          }
        }
      }
    } ~ path("processes" / Segment / "json") { id =>
      get {
        complete {
          val optionalDisplayableJsonFuture = repository.fetchProcessDeploymentById(id).map { optionalDeployment =>
            optionalDeployment.collect {
              case GraphProcess(json) => processConverter.toDisplayableOrDie(json)
            }
          }
          optionalDisplayableJsonFuture.map[ToResponseMarshallable] {
            case Some(process) =>
              process
            case None =>
              HttpResponse(status = StatusCodes.NotFound, entity = s"Process $id not found")
          }
        }
      } ~ put {
        entity(as[DisplayableProcess]) { displayableProcess =>
          complete {
            repository.withProcessJsonById(id) { _ =>
              val canonical = processConverter.fromDisplayable(displayableProcess)
              Xor.right((canonical, Option(ProcessMarshaller.toJson(canonical, PrettyParams.nospace))))
            }.map { canonicalXor =>
              toResponse(
                canonicalXor.map { canonical =>
                  processValidation.validate(canonical)
                }
              )
            }
          }
        }
      }
    } ~ path("processes" / Segment / "json" / "properties") { processId =>
      put {
        entity(as[ProcessProperties]) { properties =>
          complete {
            repository.withParsedProcessById(processId) { currentCanonical =>
              val modificatedProcess = currentCanonical.copy(
                metaData = currentCanonical.metaData.copy(parallelism = properties.parallelism),
                exceptionHandlerRef = properties.exceptionHandler)
              Xor.right(modificatedProcess)
            }.map { canonicalXor =>
              toResponse(canonicalXor.map(processValidation.validateFilteringResults(_, ProcessCompilationError.ProcessNodeId)))
            }
          }
        }
      }
    } ~ path("processes" / Segment / "json" / "node" / Segment) { (processId, nodeId) =>
      put {
        entity(as[NodeData]) { displayableNode =>
          complete {
            repository.withParsedProcessById(processId) { currentCanonical =>
              val canonicalNode = processConverter.nodeFromDisplayable(displayableNode)
              val modificationResult = currentCanonical.modify(nodeId)(_ => canonicalNode)
              if (modificationResult.modifiedCount < 1)
                Xor.left(NodeNotFoundError(processId = processId, nodeId = nodeId))
              else
                Xor.right(modificationResult.value)
            }.map { canonicalXor =>
              toResponse(canonicalXor.map(processValidation.validateFilteringResults(_, nodeId)))
            }
          }
        }
      }
    } ~ path("processes" / Segment / "status" ) { processId =>
      get {
        complete {
          repository.fetchProcessDetailsById(processId).flatMap[ToResponseMarshallable] {
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
    }

  private def fetchProcessStatesForProcesses(processes: List[ProcessDetails]): Future[Map[String, Option[ProcessStatus]]] = {
    processes.map(process => findJobStatus(process.name).map(status => process.name -> status)).sequence.map(_.toMap)
  }

  private def toResponse(xor: Xor[EspError, ValidationResult]): ToResponseMarshallable =
    xor match {
      case Xor.Right(validationResult) =>
        validationResult
      case Xor.Left(err) =>
        espErrorToHttp(err)
    }

  private def espErrorToHttp(error: EspError) = {
    val statusCode =  error match {
      case e:NotFoundError =>  StatusCodes.NotFound
      case e:FatalError => StatusCodes.InternalServerError
      case e:BadRequestError => StatusCodes.BadRequest
      //unknown?
      case _ => StatusCodes.InternalServerError
    }
    HttpResponse(status = statusCode, entity = error.getMessage)
  }

  private def findJobStatus(processName: String)(implicit ec: ExecutionContext): Future[Option[ProcessStatus]] = {
    processManager.findJobStatus(processName).map(statusOpt => statusOpt.map(ProcessStatus.apply))
  }

}

object ProcessesResources {

  case class UnmarshallError(message: String) extends Exception(message) with FatalError

  case class ProcessNotInitializedError(id: String) extends Exception(s"Process $id is not initialized") with NotFoundError

  case class NodeNotFoundError(processId: String, nodeId: String) extends Exception(s"Node $nodeId not found inside process $processId") with NotFoundError

}