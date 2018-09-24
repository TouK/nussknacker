package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives.SecurityDirectives
import argonaut.{Json, JsonParser}
import com.typesafe.config.{Config, ConfigRenderOptions}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.ui.process.{JobStatusService, ProcessObjectsFinder}
import pl.touk.nussknacker.ui.process.displayedgraph.ProcessStatus
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.ProcessDetails
import pl.touk.http.argonaut.Argonaut62Support
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.ui.security.api.{LoggedUser, Permission}
import pl.touk.nussknacker.ui.validation.ProcessValidation

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class AppResources(config: Config,
                   modelData: Map[ProcessingType, ModelData],
                   processRepository: FetchingProcessRepository,
                   processValidation: ProcessValidation,
                   jobStatusService: JobStatusService)(implicit ec: ExecutionContext)
  extends Directives with Argonaut62Support with LazyLogging with RouteWithUser with SecurityDirectives {

  import argonaut.ArgonautShapeless._

  def route(implicit user: LoggedUser): Route =
    pathPrefix("app") {
      path("buildInfo") {
        get {
          complete {
            modelData.map {
              case (k,v) => (k.toString, v.configCreator.buildInfo())
            }
          }
        }
      } ~ path("healthCheck") {
        get {
          complete {
            notRunningProcessesThatShouldRun.map[HttpResponse] { set =>
              if (set.isEmpty) {
                HttpResponse(status = StatusCodes.OK)
              } else {
                logger.warn(s"Processes not running: $set")
                HttpResponse(status = StatusCodes.InternalServerError, entity = s"Deployed processes not running (probably failed): \n${set.mkString(", ")}")
              }
            }.recover[HttpResponse] {
              case NonFatal(e) =>
                logger.error("Failed to get statuses", e)
                HttpResponse(status = StatusCodes.InternalServerError, entity = "Failed to retrieve job statuses")
            }
          }
        }
      } ~ path("sanityCheck")  {
        get {
          complete {
            processesWithValidationErrors.map[HttpResponse] { processes =>
              if (processes.isEmpty) {
                HttpResponse(status = StatusCodes.OK)
              } else {
                val message = s"Processes with validation errors: \n${processes.mkString(", ")}"
                HttpResponse(status = StatusCodes.InternalServerError, entity = message)
              }
            }
          }
        }
      } ~ path("unusedComponents")  {
        get {
          complete {
            val definition = modelData.values.map(_.processDefinition).toList
            processRepository.fetchAllProcessesDetails().map { processes =>
              ProcessObjectsFinder.findUnusedComponents(processes, definition)
            }
          }
        }
      } ~ path("config") {
        //config can contain sensitive information, so only Admin can see it
        authorize(user.hasPermission(Permission.Admin)) {
          get {
            complete {
              JsonParser.parse(config.root().render(ConfigRenderOptions.concise()))
            }
          }
        }
      }
    }


  private def notRunningProcessesThatShouldRun(implicit ec: ExecutionContext, user: LoggedUser) : Future[Set[String]] = {

    for {
      processes <- processRepository.fetchProcessesDetails()
      statusMap <- Future.sequence(statusList(processes)).map(_.toMap)
    } yield {
      statusMap.filter { case (_, status) => !status.exists(_.isRunning) }.keySet
    }
  }

  private def processesWithValidationErrors(implicit ec: ExecutionContext, user: LoggedUser): Future[List[String]] = {
    processRepository.fetchProcessesDetails().map { processes =>
      val processesWithErrors = processes.flatMap(_.json)
        .map(_.validated(processValidation))
        .filter(process => !process.validationResult.errors.isEmpty)
      processesWithErrors.map(_.id)
    }
  }

  private def statusList(processes: Seq[ProcessDetails])(implicit user: LoggedUser) : Seq[Future[(String, Option[ProcessStatus])]] = {
    processes
      .filterNot(_.currentlyDeployedAt.isEmpty)
      .map(process => findJobStatus(process.name, process.processingType).map((process.name, _)))
  }

  private def findJobStatus(processName: String, processingType: ProcessingType)(implicit ec: ExecutionContext, user: LoggedUser): Future[Option[ProcessStatus]] = {
    jobStatusService.retrieveJobStatus(processName).recover {
      case NonFatal(e) =>
        logger.warn(s"Failed to get status of $processName: ${e.getMessage}", e)
        Some(ProcessStatus.failedToGet)
    }
  }
}
