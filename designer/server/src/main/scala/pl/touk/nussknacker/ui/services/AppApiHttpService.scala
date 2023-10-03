package pl.touk.nussknacker.ui.services

import com.typesafe.config.{Config, ConfigRenderOptions}
import com.typesafe.scalalogging.LazyLogging
import io.circe.parser
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.ProblemStateStatus
import pl.touk.nussknacker.engine.api.deployment.{DataFreshnessPolicy, ProcessState}
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.version.BuildInfo
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ValidatedDisplayableProcess}
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.restmodel.processdetails.BaseProcessDetails
import pl.touk.nussknacker.ui.api.AppApiEndpoints
import pl.touk.nussknacker.ui.api.AppApiEndpoints.Dtos._
import pl.touk.nussknacker.ui.process.ProcessCategoryService
import pl.touk.nussknacker.ui.process.deployment.DeploymentService
import pl.touk.nussknacker.ui.process.processingtypedata.{ProcessingTypeDataProvider, ProcessingTypeDataReload}
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository.FetchProcessesDetailsQuery
import pl.touk.nussknacker.ui.security.api.{AuthenticationResources, LoggedUser}
import pl.touk.nussknacker.ui.validation.ProcessValidation

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class AppApiHttpService(
    config: Config,
    authenticator: AuthenticationResources,
    processingTypeDataReloader: ProcessingTypeDataReload,
    modelData: ProcessingTypeDataProvider[ModelData, _],
    processRepository: FetchingProcessRepository[Future],
    processValidation: ProcessValidation,
    deploymentService: DeploymentService,
    processCategoryService: ProcessCategoryService,
    shouldExposeConfig: Boolean
)(implicit executionContext: ExecutionContext)
    extends BaseHttpService(config, processCategoryService, authenticator)
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
                  processes = Some(set.keys.toSet)
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
          UserCategoriesWithProcessingTypesDto(processCategoryService.getUserCategoriesWithType(loggedUser))
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

  private def problemStateByProcessName(implicit user: LoggedUser): Future[Map[String, ProcessState]] = {
    for {
      processes <- processRepository.fetchProcessesDetails[Unit](FetchProcessesDetailsQuery.deployed)
      statusMap <- Future.sequence(mapNameToProcessState(processes)).map(_.toMap)
      withProblem = statusMap.collect {
        case (name, processStatus @ ProcessState(_, _ @ProblemStateStatus(_, _), _, _, _, _, _, _, _, _)) =>
          (name, processStatus)
      }
    } yield withProblem
  }

  private def mapNameToProcessState(
      processes: Seq[BaseProcessDetails[_]]
  )(implicit user: LoggedUser): Seq[Future[(String, ProcessState)]] = {
    // Problems should be detected by Healtcheck very quickly. Because of that we return fresh states for list of processes
    implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh
    processes.map(process => deploymentService.getProcessState(process).map((process.name, _)))
  }

  private def processesWithValidationErrors(implicit user: LoggedUser): Future[List[String]] = {
    processRepository
      .fetchProcessesDetails[DisplayableProcess](FetchProcessesDetailsQuery.unarchivedProcesses)
      .map { processes =>
        processes
          .map(process => new ValidatedDisplayableProcess(process.json, processValidation.validate(process.json)))
          .filter(process => !process.validationResult.errors.isEmpty)
          .map(_.id)
      }
  }
}
