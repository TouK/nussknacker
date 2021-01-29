package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{HttpResponse, ResponseEntity, StatusCodes}
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives.SecurityDirectives
import com.typesafe.config.{Config, ConfigRenderOptions}
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.syntax._
import net.ceedubs.ficus.Ficus._
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.deployment.ProcessState
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ValidatedDisplayableProcess}
import pl.touk.nussknacker.restmodel.processdetails.BaseProcessDetails
import pl.touk.nussknacker.ui.process.processingtypedata.{ProcessingTypeDataProvider, ProcessingTypeDataReload}
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository
import pl.touk.nussknacker.ui.process.{ProcessService, ProcessObjectsFinder}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.validation.ProcessValidation

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class AppResources(config: Config,
                   processingTypeDataReload: ProcessingTypeDataReload,
                   modelData: ProcessingTypeDataProvider[ModelData],
                   processRepository: FetchingProcessRepository[Future],
                   processValidation: ProcessValidation,
                   processService: ProcessService)(implicit ec: ExecutionContext)
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

  private def createHealthCheckHttpResponse(status: HealthCheckProcessResponseStatus, message: Option[String] = None, processes: Option[Set[String]] = None): Future[HttpResponse] =
    Marshal(HealthCheckProcessResponse(status, message, processes))
      .to[ResponseEntity]
      .map(res => HttpResponse(
        status = status match {
          case OK => StatusCodes.OK
          case ERROR => StatusCodes.InternalServerError
        },
        entity = res))

  def securedRoute(implicit user: LoggedUser): Route =
    pathPrefix("app") {
      path("healthCheck") {
        get {
          complete {
            createHealthCheckHttpResponse(OK)
          }
        }
      } ~ path("healthCheck" / "process" / "deployment") {
        get {
          complete {
            notRunningProcessesThatShouldRun.map[Future[HttpResponse]] { set =>
              if (set.isEmpty) {
                createHealthCheckHttpResponse(OK)
              } else {
                logger.warn(s"Processes not running: ${set.keys}")
                logger.debug(s"Processes not running - more details: $set")
                createHealthCheckHttpResponse(ERROR, Some("Deployed processes not running (probably failed)"), Some(set.keys.toSet))
              }
            }.recover[Future[HttpResponse]] {
              case NonFatal(e) =>
                logger.error("Failed to get statuses", e)
                createHealthCheckHttpResponse(ERROR, Some("Failed to retrieve job statuses"))
            }
          }
        }
      } ~ path("healthCheck" / "process" / "validation")  {
        get {
          complete {
            processesWithValidationErrors.map[Future[HttpResponse]] { processes =>
              if (processes.isEmpty) {
                createHealthCheckHttpResponse(OK)
              } else {
                createHealthCheckHttpResponse(ERROR, Some("Processes with validation errors"), Some(processes.toSet))
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

  private def notRunningProcessesThatShouldRun(implicit ec: ExecutionContext, user: LoggedUser): Future[Map[String, ProcessState]] = {
    for {
      processes <- processRepository.fetchDeployedProcessesDetails[Unit]()
      statusMap <- Future.sequence(statusList(processes)).map(_.toMap)
    } yield {
      statusMap.filterNot{
        case (_, status) => status.isDeployed
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

  private def statusList(processes: Seq[BaseProcessDetails[_]])(implicit user: LoggedUser) : Seq[Future[(String, ProcessState)]] =
    processes.map(process => processService.getProcessState(process.idWithName).map((process.name, _)))
}
