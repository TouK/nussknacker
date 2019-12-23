package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives.SecurityDirectives
import com.typesafe.config.{Config, ConfigRenderOptions}
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.syntax._
import net.ceedubs.ficus.Ficus._
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ProcessStatus, ValidatedDisplayableProcess}
import pl.touk.nussknacker.restmodel.process.ProcessIdWithName
import pl.touk.nussknacker.restmodel.processdetails.BaseProcessDetails
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository
import pl.touk.nussknacker.ui.process.{JobStatusService, ProcessObjectsFinder}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.validation.ProcessValidation

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class AppResources(config: Config,
                   modelData: Map[ProcessingType, ModelData],
                   processRepository: FetchingProcessRepository[Future],
                   processValidation: ProcessValidation,
                   jobStatusService: JobStatusService)(implicit ec: ExecutionContext)
  extends Directives with FailFastCirceSupport with LazyLogging with RouteWithUser with RouteWithoutUser with SecurityDirectives {

  //We use duplicated pathPrefix("app") code - look at comment in NussknackerApp where routes are created
  def publicRoute(): Route = pathPrefix("app") {
    path("buildInfo") {
      get {
        complete {
          val globalBuildInfo = config.getAs[Map[String, String]]("globalBuildInfo").getOrElse(Map()).mapValues(_.asJson)
          val modelDataInfo = modelData.map {
            case (k,v) => (k.toString, v.configCreator.buildInfo())
          }.asJson
          (globalBuildInfo + ("processingType" -> modelDataInfo)).asJson
        }
      }
    }
  }

  def securedRoute(implicit user: LoggedUser): Route =
    pathPrefix("app") {
      path("healthCheck") {
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
            processRepository.fetchAllProcessesDetails[DisplayableProcess]().map { processes =>
              ProcessObjectsFinder.findUnusedComponents(processes, definition)
            }
          }
        }
      } ~ path("config") {
        //config can contain sensitive information, so only Admin can see it
        authorize(user.isAdmin) {
          get {
            complete {
              io.circe.parser.parse(config.root().render(ConfigRenderOptions.concise())).left.map(_.message)
            }
          }
        }
      }
    }

  private def notRunningProcessesThatShouldRun(implicit ec: ExecutionContext, user: LoggedUser) : Future[Set[String]] = {
    for {
      processes <- processRepository.fetchProcessesDetails[Unit]()
      statusMap <- Future.sequence(statusList(processes)).map(_.toMap)
    } yield {
      statusMap.filter { case (_, status) => !status.exists(_.isOkForDeployed) }.keySet
    }
  }

  private def processesWithValidationErrors(implicit ec: ExecutionContext, user: LoggedUser): Future[List[String]] = {
    processRepository.fetchProcessesDetails[DisplayableProcess]().map { processes =>
      val processesWithErrors = processes.flatMap(_.json)
        .map(process => new ValidatedDisplayableProcess(process, processValidation.validate(process)))
        .filter(process => !process.validationResult.errors.isEmpty)
      processesWithErrors.map(_.id)
    }
  }

  private def statusList(processes: Seq[BaseProcessDetails[_]])(implicit user: LoggedUser) : Seq[Future[(String, Option[ProcessStatus])]] = {
    processes
      .filter(_.isDeployed)
      .map(process => findJobStatus(process.idWithName, process.processingType).map((process.name, _)))
  }

  private def findJobStatus(processId: ProcessIdWithName, processingType: ProcessingType)(implicit ec: ExecutionContext, user: LoggedUser): Future[Option[ProcessStatus]] = {
    jobStatusService.retrieveJobStatus(processId).recover {
      case NonFatal(e) =>
        logger.warn(s"Failed to get status of $processId: ${e.getMessage}", e)
        Some(ProcessStatus.failedToGet)
    }
  }
}
