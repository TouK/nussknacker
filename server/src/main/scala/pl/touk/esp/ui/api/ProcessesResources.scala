package pl.touk.esp.ui.api

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives
import argonaut.{EncodeJson, Json, PrettyParams}
import cats.data.Validated.{Invalid, Valid}
import cats.data.Writer
import pl.touk.esp.engine.canonicalgraph.CanonicalProcess
import pl.touk.esp.engine.canonicalgraph.canonicalnode._
import pl.touk.esp.engine.compile.ProcessValidator
import pl.touk.esp.engine.management.ProcessManager
import pl.touk.esp.engine.marshall.ProcessMarshaller
import pl.touk.esp.ui.api.ProcessValidation.ValidationResult
import pl.touk.esp.ui.process.displayedgraph.DisplayableProcess
import pl.touk.esp.ui.process.displayedgraph.displayablenode.DisplayableNode
import pl.touk.esp.ui.process.marshall.{DisplayableProcessCodec, ProcessConverter}
import pl.touk.esp.ui.process.repository.ProcessRepository
import pl.touk.esp.ui.process.repository.ProcessRepository._
import pl.touk.esp.ui.util.Argonaut62Support
import ProcessesResources._

import scala.concurrent.{ExecutionContext, Future}

class ProcessesResources(repository: ProcessRepository,
                         processManager: ProcessManager,
                         protected val processValidator: ProcessValidator)
                        (implicit ec: ExecutionContext)
  extends Directives with Argonaut62Support with ProcessValidation {

  import argonaut.ArgonautShapeless._
  import pl.touk.esp.engine.optics.Implicits._

  implicit val processEncode = EncodeJson.of[ProcessDetails]

  implicit val processListEncode = EncodeJson.of[List[ProcessDetails]]

  implicit val displayableProcessEncode = DisplayableProcessCodec.encoder

  implicit val displayableProcessDecode = DisplayableProcessCodec.decoder

  implicit val displayableProcessNodeDecode = DisplayableProcessCodec.nodeDecoder

  implicit val validationResultEncode = EncodeJson.of[ValidationResult]

  implicit val printer: Json => String =
    PrettyParams.spaces2.copy(dropNullKeys = true, preserveOrder = true).pretty

  import cats.syntax.traverse._
  import cats.instances.future._
  import cats.instances.list._
  import cats.instances.option._

  val route =
    path("processes") {
      get {
        complete {
          repository.fetchProcessesDetails().flatMap { details =>
            details.map(addProcessDetailsStatus).sequence
          }
        }
      }
    } ~ path("processes" / Segment) { id =>
      get {
        complete {
          repository.fetchProcessDetailsById(id).flatMap(p => p.map(addProcessDetailsStatus).sequence).map[ToResponseMarshallable] {
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
          val optionalDisplayableJsonFuture = repository.fetchProcessJsonById(id).map { optionalJson =>
            optionalJson.map(parseOrDie).map(ProcessConverter.toDisplayable)
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
            val canonical = ProcessConverter.fromDisplayable(displayableProcess)
            repository.withProcessJsonById(id) { _ =>
              Writer.value(Option(ProcessMarshaller.toJson(canonical, PrettyParams.nospace)))
            }.map[ToResponseMarshallable] {
              case Valid(_) =>
                validate(canonical) match {
                  case Valid(_) =>
                    ValidationResult(Map.empty)
                  case Invalid(errors) =>
                    errors
                }
              case Invalid(err) =>
                HttpResponse(status = StatusCodes.NotFound, entity = err.getMessage)
            }
          }
        }
      }
    } ~ path("processes" / Segment / "json" / Segment) { (processId, nodeId) =>
      put {
        entity(as[DisplayableNode]) { displayableNode =>
          complete {
            repository.withProcessJsonById(processId) { optionalCurrentProcessJson =>
              val currentProcessJson = optionalCurrentProcessJson.getOrElse {
                throw ProcessNotInitializedError(processId)
              }
              val currentCanonical = parseOrDie(currentProcessJson)
              val canonicalNode = ProcessConverter.nodeFromDisplayable(displayableNode)
              val modificationResult = currentCanonical.modify[CanonicalNode](nodeId)(_ => canonicalNode)
              if (modificationResult.modifiedCount < 1) {
                throw NodeNotFoundError(processId = processId, nodeId = nodeId)
              }
              Writer(modificationResult.value, Option(ProcessMarshaller.toJson(modificationResult.value, PrettyParams.nospace)))
            }.map[ToResponseMarshallable] {
              case Valid(writer) =>
                validate(writer.written) match { // Walidujemy cały proces bo błędy w węźle potrzebują kontekstu procesu
                  case Valid(_) =>
                    ValidationResult(Map.empty)
                  case Invalid(errors) =>
                    errors.filterNode(nodeId)
                }
              case Invalid(err) =>
                HttpResponse(status = StatusCodes.NotFound, entity = err.getMessage)
            }.recover[ToResponseMarshallable] {
              case err: ProcessNotInitializedError =>
                HttpResponse(status = StatusCodes.NotFound, entity = err.getMessage)
              case err: NodeNotFoundError =>
                HttpResponse(status = StatusCodes.NotFound, entity = err.getMessage)
            }
          }
        }
      }
    }

  private def parseOrDie(canonicalJson: String): CanonicalProcess = {
    ProcessMarshaller.fromJson(canonicalJson) match {
      case Valid(canonical) => canonical
      case Invalid(err) => throw new IllegalArgumentException(err.msg)
    }
  }

  private def addProcessDetailsStatus(processDetails: ProcessDetails)(implicit ec: ExecutionContext): Future[ProcessDetails] = {
    processManager.findJobStatus(processDetails.name).map { jobState =>
      processDetails.copy(tags = processDetails.tags ++ jobState.toList.map(js => "TEST: " + js.status)) //todo na razie tak bo mamy tylko procesy na testach
    }
  }

}

object ProcessesResources {

  case class ProcessNotInitializedError(id: String) extends Exception(s"Process $id is not initialized")

  case class NodeNotFoundError(processId: String, nodeId: String) extends Exception(s"Node $nodeId not found inside process $processId")

}