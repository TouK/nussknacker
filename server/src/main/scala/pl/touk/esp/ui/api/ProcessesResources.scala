package pl.touk.esp.ui.api

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives
import argonaut.{EncodeJson, Json, PrettyParams}
import cats.data.Validated.{Invalid, Valid}
import pl.touk.esp.engine.compile.ProcessValidator
import pl.touk.esp.engine.management.ProcessManager
import pl.touk.esp.engine.marshall.ProcessMarshaller
import pl.touk.esp.ui.api.ProcessValidation.ValidationResult
import pl.touk.esp.ui.process.displayedgraph.DisplayableProcess
import pl.touk.esp.ui.process.marshall.{DisplayableProcessCodec, ProcessConverter}
import pl.touk.esp.ui.process.repository.ProcessRepository
import pl.touk.esp.ui.process.repository.ProcessRepository._
import pl.touk.esp.ui.util.Argonaut62Support

import scala.concurrent.{ExecutionContext, Future}

class ProcessesResources(repository: ProcessRepository,
                         processManager: ProcessManager,
                         protected val processValidator: ProcessValidator)
                        (implicit ec: ExecutionContext)
  extends Directives with Argonaut62Support with ProcessValidation {

  import argonaut.ArgonautShapeless._

  implicit val processEncode = EncodeJson.of[ProcessDetails]

  implicit val processListEncode = EncodeJson.of[List[ProcessDetails]]

  implicit val displayableProcessEncode = DisplayableProcessCodec.encoder

  implicit val displayableProcessDecode = DisplayableProcessCodec.decoder

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
            optionalJson.map(convertToDisplayable)
          }
          optionalDisplayableJsonFuture.map[ToResponseMarshallable] {
            case Some(process) =>
              process
            case None =>
              HttpResponse(
                status = StatusCodes.NotFound,
                entity = "Process not found"
              )
          }
        }
      } ~ put {
        entity(as[DisplayableProcess]) { displayableProcess =>
          complete {
            val canonical = ProcessConverter.fromDisplayable(displayableProcess)
            repository.withProcessJsonById(id) { _ =>
              Some(ProcessMarshaller.toJson(canonical, PrettyParams.nospace))
            }.map[ToResponseMarshallable] {
              case Valid(_) =>
                validate(canonical) match {
                  case Valid(_) =>
                    ValidationResult(Map.empty)
                  case Invalid(errors) =>
                    errors
                }
              case Invalid(ProcessIsMissingError) =>
                HttpResponse(
                  status = StatusCodes.NotFound,
                  entity = "Process not found"
                )
            }
          }
        }
      }
    }

  private def convertToDisplayable(canonicalJson: String): DisplayableProcess = {
    ProcessMarshaller.fromJson(canonicalJson) match {
      case Valid(canonical) => ProcessConverter.toDisplayable(canonical)
      case Invalid(err) => throw new IllegalArgumentException(err.msg)
    }
  }

  private def addProcessDetailsStatus(processDetails: ProcessDetails)(implicit ec: ExecutionContext): Future[ProcessDetails] = {
    processManager.findJobStatus(processDetails.name).map { jobState =>
      processDetails.copy(tags = processDetails.tags ++ jobState.toList.map(js => "TEST: " + js.status)) //todo na razie tak bo mamy tylko procesy na testach
    }
  }

}