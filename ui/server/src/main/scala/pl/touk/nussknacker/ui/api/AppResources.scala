package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.{HttpResponse, StatusCodes, ResponseEntity, HttpEntity, ContentTypes}
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives.SecurityDirectives
import com.typesafe.config.{Config, ConfigRenderOptions}
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Printer
import io.circe.syntax._
import net.ceedubs.ficus.Ficus._
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ProcessStatus, ValidatedDisplayableProcess}
import pl.touk.nussknacker.restmodel.process.ProcessIdWithName
import pl.touk.nussknacker.restmodel.processdetails.BaseProcessDetails
import pl.touk.nussknacker.ui.process.processingtypedata.{ProcessingTypeDataProvider, ProcessingTypeDataReload}
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository
import pl.touk.nussknacker.ui.process.{JobStatusService, ProcessObjectsFinder}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.validation.ProcessValidation

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class AppResources(config: Config,
                   processingTypeDataReload: ProcessingTypeDataReload,
                   modelData: ProcessingTypeDataProvider[ModelData],
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
          val modelDataInfo = modelData.all.map {
            case (k,v) => (k.toString, v.configCreator.buildInfo())
          }.asJson
          (globalBuildInfo + ("processingType" -> modelDataInfo)).asJson
        }
      }
    }
  }

  private def toEntity(healthCheckProcessResponse: HealthCheckProcessResponse): ResponseEntity = {
    val printer = Printer.noSpaces.copy(dropNullValues = true)
    HttpEntity(
      ContentTypes.`application/json`,
      printer.pretty(healthCheckProcessResponse.asJson)
    )
  }

  def securedRoute(implicit user: LoggedUser): Route =
    pathPrefix("app") {
      path("healthCheck") {
        get {
          complete {
            HttpResponse(status = StatusCodes.OK, entity = toEntity(HealthCheckProcessResponse(OK)))
          }
        }
      } ~ path("healthCheck" / "process" / "deployed") {
        get {
          complete {
            notRunningProcessesThatShouldRun.map[HttpResponse] { set =>
              if (set.isEmpty) {
                val respEntity = toEntity(HealthCheckProcessResponse(OK))
                HttpResponse(status = StatusCodes.OK, entity = respEntity)
              } else {
                logger.warn(s"Processes not running: ${set.keys}")
                logger.debug(s"Processes not running - more details: $set")
                val respEntity = toEntity(HealthCheckProcessResponse(ERROR, Some("Deployed processes not running (probably failed)"), Some(set.keys.toSet)))
                HttpResponse(status = StatusCodes.InternalServerError, entity = respEntity)
              }
            }.recover[HttpResponse] {
              case NonFatal(e) =>
                logger.error("Failed to get statuses", e)
                val respEntity = toEntity(HealthCheckProcessResponse(ERROR, Some("Failed to retrieve job statuses")))
                HttpResponse(status = StatusCodes.InternalServerError, entity = respEntity)
            }
          }
        }
      } ~ path("healthCheck" / "process" / "validation")  {
        get {
          complete {
            processesWithValidationErrors.map[HttpResponse] { processes =>
              if (processes.isEmpty) {
                val respEntity = toEntity(HealthCheckProcessResponse(OK))
                HttpResponse(status = StatusCodes.OK, entity = respEntity)
              } else {
                val respEntity = toEntity(HealthCheckProcessResponse(ERROR, Some("Processes with validation errors"), Some(processes.toSet)))
                HttpResponse(status = StatusCodes.InternalServerError, entity = respEntity)
              }
            }
          }
        }
      } ~ path("unusedComponents")  {
        get {
          complete {
            val definition = modelData.all.values.map(_.processDefinition).toList
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
      } ~ pathPrefix("processingtype" / "reload") {
        authorize(user.isAdmin) {
          post {
            pathEnd {
              complete {
                processingTypeDataReload.reloadAll()
                HttpResponse(StatusCodes.NoContent)
              }
            }
          }
        }
      }
    }

  private def notRunningProcessesThatShouldRun(implicit ec: ExecutionContext, user: LoggedUser): Future[Map[String, Option[ProcessStatus]]] = {
    for {
      processes <- processRepository.fetchDeployedProcessesDetails[Unit]()
      statusMap <- Future.sequence(statusList(processes)).map(_.toMap)
    } yield {
      statusMap.filterNot{
        case (_, status) => status.exists(st => st.status.isDuringDeploy || st.status.isRunning)
      }.map{
        case (process, status) => (process.name, status)
      }
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

  private def statusList(processes: Seq[BaseProcessDetails[_]])(implicit user: LoggedUser) : Seq[Future[(String, Option[ProcessStatus])]] =
    processes.map(process =>
      findJobStatus(process.idWithName, process.processingType)
        .map((process.name, _))
    )

  private def findJobStatus(processId: ProcessIdWithName, processingType: ProcessingType)(implicit ec: ExecutionContext, user: LoggedUser): Future[Option[ProcessStatus]] = {
    jobStatusService.retrieveJobStatus(processId).recover {
      case NonFatal(e) =>
        logger.warn(s"Failed to get status of $processId: ${e.getMessage}", e)
        Some(ProcessStatus.failedToGet)
    }
  }
}
