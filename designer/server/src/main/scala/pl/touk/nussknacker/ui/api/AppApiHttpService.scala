package pl.touk.nussknacker.ui.api

import com.typesafe.config.{Config, ConfigRenderOptions}
import com.typesafe.scalalogging.LazyLogging
import io.circe.parser
import pl.touk.nussknacker.engine.api.deployment.ProcessState
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.ProblemStateStatus
import pl.touk.nussknacker.engine.api.process.{ProcessName, ProcessingType}
import pl.touk.nussknacker.engine.util.Implicits.RichTupleList
import pl.touk.nussknacker.engine.version.BuildInfo
import pl.touk.nussknacker.ui.api.description.AppApiEndpoints
import pl.touk.nussknacker.ui.api.description.AppApiEndpoints.Dtos._
import pl.touk.nussknacker.ui.process.ProcessService.GetScenarioWithDetailsOptions
import pl.touk.nussknacker.ui.process.processingtype.provider.{
  ProcessingTypeDataProvider,
  ReloadableProcessingTypeDataProvider
}
import pl.touk.nussknacker.ui.process.{ProcessService, ScenarioQuery}
import pl.touk.nussknacker.ui.security.api.{AuthManager, LoggedUser, NussknackerInternalUser}
import pl.touk.nussknacker.ui.util.ExecutionContextWithIORuntime

import scala.concurrent.Future
import scala.util.control.NonFatal

class AppApiHttpService(
    config: Config,
    authManager: AuthManager,
    processingTypeDataReloader: ReloadableProcessingTypeDataProvider,
    modelBuildInfos: ProcessingTypeDataProvider[Map[String, String], _],
    categories: ProcessingTypeDataProvider[String, _],
    processService: ProcessService,
    shouldExposeConfig: Boolean
)(implicit ec: ExecutionContextWithIORuntime)
    extends BaseHttpService(authManager)
    with LazyLogging {

  import ec.ioRuntime

  private val appApiEndpoints = new AppApiEndpoints(authManager.authenticationEndpointInput())

  expose {
    appApiEndpoints.appHealthCheckEndpoint
      .serverLogicSuccess { _ =>
        Future.successful(HealthCheckProcessSuccessResponseDto())
      }
  }

  expose {
    appApiEndpoints.processDeploymentHealthCheckEndpoint
      .serverSecurityLogic(authorizeKnownUser[HealthCheckProcessErrorResponseDto])
      .serverLogic { implicit loggedUser => _ =>
        problemStateByProcessName
          .map { set =>
            if (set.isEmpty) {
              success(HealthCheckProcessSuccessResponseDto())
            } else {
              if (logger.underlying.isDebugEnabled) {
                logger.debug(s"Scenarios with status PROBLEM: $set")
              } else {
                logger.warn(s"Scenarios with status PROBLEM: ${set.keys}")
              }
              businessError(
                HealthCheckProcessErrorResponseDto(
                  message = Some("Scenarios with status PROBLEM"),
                  processes = Some(set.keys.map(_.value).toSet)
                )
              )
            }
          }
          .recover { case NonFatal(e) =>
            logger.error("Failed to get statuses", e)
            businessError(
              HealthCheckProcessErrorResponseDto(
                message = Some("Failed to retrieve job statuses"),
                processes = None
              )
            )
          }
      }
  }

  expose {
    appApiEndpoints.processValidationHealthCheckEndpoint
      .serverSecurityLogic(authorizeKnownUser[HealthCheckProcessErrorResponseDto])
      .serverLogic { implicit loggedUser => _ =>
        processesWithValidationErrors.map { processes =>
          if (processes.isEmpty) {
            success(HealthCheckProcessSuccessResponseDto())
          } else {
            businessError(
              HealthCheckProcessErrorResponseDto(
                message = Some("Scenarios with validation errors"),
                processes = Some(processes.toSet)
              )
            )
          }
        }
      }
  }

  expose {
    appApiEndpoints.buildInfoEndpoint
      .serverLogicSuccess { _ =>
        Future {
          import net.ceedubs.ficus.Ficus._
          val configuredBuildInfo = config.getAs[Map[String, String]]("globalBuildInfo")
          // TODO: Warning, here is a little security leak. Everyone can discover configured processing types.
          //       We should consider adding an authorization of access rights to this data.
          val modelBuildInfo: Map[ProcessingType, Map[String, String]] =
            modelBuildInfos.all(NussknackerInternalUser.instance)
          BuildInfoDto(
            BuildInfo.name,
            BuildInfo.gitCommit,
            BuildInfo.buildTime,
            BuildInfo.version,
            modelBuildInfo,
            configuredBuildInfo
          )
        }
      }
  }

  expose(when = shouldExposeConfig) {
    appApiEndpoints.serverConfigEndpoint
      .serverSecurityLogic(authorizeAdminUser[Unit])
      .serverLogic { _ => _ =>
        Future {
          val configJson = parser.parse(config.root().render(ConfigRenderOptions.concise())).left.map(_.message)
          configJson match {
            case Right(json) =>
              success(ServerConfigInfoDto(json))
            case Left(errorMessage) =>
              logger.error(s"Cannot create JSON from the Nussknacker configuration. Error: $errorMessage")
              throw new Exception("Cannot prepare configuration")
          }
        }
      }
  }

  expose {
    // This endpoint is used only by external project
    // TODO: We should remove this endpoint after we fully switch to processing modes - see ConfigProcessCategoryService.checkCategoryToProcessingTypeMappingAmbiguity
    appApiEndpoints.userCategoriesWithProcessingTypesEndpoint
      .serverSecurityLogic(authorizeKnownUser[Unit])
      .serverLogicSuccess { loggedUser => _ =>
        Future {
          val processingTypeByCategory = categories
            .all(loggedUser)
            .toList
            .map { case (processingType, category) =>
              category -> processingType
            }
            .toMapCheckingDuplicates
          UserCategoriesWithProcessingTypesDto(processingTypeByCategory)
        }
      }
  }

  expose {
    appApiEndpoints.processingTypeDataReloadEndpoint
      .serverSecurityLogic(authorizeAdminUser[Unit])
      .serverLogicSuccess { _ => _ =>
        processingTypeDataReloader.reloadAll().unsafeToFuture()
      }
  }

  private def problemStateByProcessName(implicit user: LoggedUser): Future[Map[ProcessName, ProcessState]] = {
    for {
      processes <- processService.getLatestProcessesWithDetails(
        ScenarioQuery.deployed,
        GetScenarioWithDetailsOptions.detailsOnly.copy(fetchState = true)
      )
      statusMap = processes.flatMap(process => process.state.map(process.name -> _)).toMap
      withProblem = statusMap.collect {
        case (name, processStatus @ ProcessState(_, _ @ProblemStateStatus(_, _), _, _, _, _, _, _, _, _, _, _)) =>
          (name, processStatus)
      }
    } yield withProblem
  }

  private def processesWithValidationErrors(implicit user: LoggedUser): Future[List[String]] = {
    processService
      .getLatestProcessesWithDetails(
        ScenarioQuery.unarchivedProcesses,
        GetScenarioWithDetailsOptions.withScenarioGraph.withValidation
      )
      .map { processes =>
        processes
          .filterNot(_.validationResultUnsafe.errors.isEmpty)
          .map(_.name.value)
      }
  }

}
