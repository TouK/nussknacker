package pl.touk.nussknacker.ui.services

import com.typesafe.config.{Config, ConfigRenderOptions}
import com.typesafe.scalalogging.LazyLogging
import io.circe.parser
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.component.ComponentId
import pl.touk.nussknacker.engine.api.deployment.ProcessState
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.ProblemStateStatus
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.version.BuildInfo
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.ui.api.AppApiEndpoints.Dtos._
import pl.touk.nussknacker.ui.api.{AppApiEndpoints, ComponentResourceApiEndpoints}
import pl.touk.nussknacker.ui.api.ComponentResourceApiEndpoints.Dtos.{
  ComponentListElementDto,
  ComponentUsageErrorResponseDto,
  ComponentUsageSuccessfulResponseDto,
  ComponentUsagesInScenarioDto,
  ComponentsListSuccessfulResponseDto
}
import pl.touk.nussknacker.ui.component.ComponentService
import pl.touk.nussknacker.ui.process.ProcessService.{FetchScenarioGraph, GetScenarioWithDetailsOptions}
import pl.touk.nussknacker.ui.process.processingtypedata.{ProcessingTypeDataProvider, ProcessingTypeDataReload}
import pl.touk.nussknacker.ui.process.{ProcessCategoryService, ProcessService, ScenarioQuery, UserCategoryService}
import pl.touk.nussknacker.ui.security.api.{AuthenticationResources, LoggedUser}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.control.NonFatal

class AppApiHttpService(
    config: Config,
    authenticator: AuthenticationResources,
    processingTypeDataReloader: ProcessingTypeDataReload,
    modelData: ProcessingTypeDataProvider[ModelData, _],
    processService: ProcessService,
    getProcessCategoryService: () => ProcessCategoryService,
    shouldExposeConfig: Boolean,
    componentService: ComponentService
)(implicit executionContext: ExecutionContext)
    extends BaseHttpService(config, getProcessCategoryService, authenticator)
    with LazyLogging {

  private val appApiEndpoints = new AppApiEndpoints(authenticator.authenticationMethod())

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
              logger.warn(s"Scenarios with status PROBLEM: ${set.keys}")
              logger.debug(s"Scenarios with status PROBLEM: $set")
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
          val modelDataInfo: Map[ProcessingType, Map[String, String]] =
            modelData.all.mapValuesNow(_.configCreator.buildInfo())
          BuildInfoDto(
            BuildInfo.name,
            BuildInfo.gitCommit,
            BuildInfo.buildTime,
            BuildInfo.version,
            modelDataInfo,
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
    appApiEndpoints.userCategoriesWithProcessingTypesEndpoint
      .serverSecurityLogic(authorizeKnownUser[Unit])
      .serverLogicSuccess { loggedUser => _ =>
        Future {
          val userCategoryService = new UserCategoryService(getProcessCategoryService())
          UserCategoriesWithProcessingTypesDto(userCategoryService.getUserCategoriesWithType(loggedUser))
        }
      }
  }

  expose {
    appApiEndpoints.processingTypeDataReloadEndpoint
      .serverSecurityLogic(authorizeAdminUser[Unit])
      .serverLogic { _ => _ =>
        Future(
          success(processingTypeDataReloader.reloadAll())
        )
      }
  }

  private val componentApiEndpoints = new ComponentResourceApiEndpoints(authenticator.authenticationMethod())

  expose {
    componentApiEndpoints.componentsListEndpoint
      .serverSecurityLogic(authorizeKnownUser[Unit])
      .serverLogicSuccess { user => _ =>
        val componentList = Await.result(componentService.getComponentsList(user), Duration.Inf)
        Future.successful(
          ComponentsListSuccessfulResponseDto(
            componentList.map(comp => ComponentListElementDto(comp))
          )
        )
      }
  }

  expose {
    componentApiEndpoints.componentUsageEndpoint
      .serverSecurityLogic(authorizeKnownUser[String])
      .serverLogic { user: LoggedUser => componentId: String =>
        val usages =
          Await.result(componentService.getComponentUsages(ComponentId(componentId))(user), Duration.Inf)
        Future(
          usages match {
            case Left(_) => businessError(s"Component ${componentId} not exist.")
            case Right(value) =>
              success(
                ComponentUsageSuccessfulResponseDto(
                  value.map(usage => ComponentUsagesInScenarioDto(usage))
                )
              )
          }
        )
      }
  }

  private def problemStateByProcessName(implicit user: LoggedUser): Future[Map[ProcessName, ProcessState]] = {
    for {
      processes <- processService.getProcessesWithDetails(
        ScenarioQuery.deployed,
        GetScenarioWithDetailsOptions.detailsOnly.copy(fetchState = true)
      )
      statusMap = processes.flatMap(process => process.state.map(process.name -> _)).toMap
      withProblem = statusMap.collect {
        case (name, processStatus @ ProcessState(_, _ @ProblemStateStatus(_, _), _, _, _, _, _, _, _, _)) =>
          (name, processStatus)
      }
    } yield withProblem
  }

  private def processesWithValidationErrors(implicit user: LoggedUser): Future[List[String]] = {
    processService
      .getProcessesWithDetails(
        ScenarioQuery.unarchivedProcesses,
        GetScenarioWithDetailsOptions.withsScenarioGraph.withValidation
      )
      .map { processes =>
        processes
          .filterNot(_.validationResultUnsafe.errors.isEmpty)
          .map(_.id)
      }
  }

}
