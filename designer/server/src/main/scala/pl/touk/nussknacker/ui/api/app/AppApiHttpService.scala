package pl.touk.nussknacker.ui.api.app

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
import pl.touk.nussknacker.ui.api.BaseHttpService
import pl.touk.nussknacker.ui.api.app.AppApiEndpoints.Dtos._
import pl.touk.nussknacker.ui.process.ProcessCategoryService
import pl.touk.nussknacker.ui.process.deployment.DeploymentService
import pl.touk.nussknacker.ui.process.processingtypedata.{ProcessingTypeDataProvider, ProcessingTypeDataReload}
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository.FetchProcessesDetailsQuery
import pl.touk.nussknacker.ui.security.api.{AuthenticationResources, LoggedUser}
import pl.touk.nussknacker.ui.validation.ProcessValidation
import sttp.tapir.server.ServerEndpoint

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class AppApiHttpService(config: Config,
                        authenticator: AuthenticationResources,
                        processingTypeDataReloader: ProcessingTypeDataReload,
                        modelData: ProcessingTypeDataProvider[ModelData, _],
                        processRepository: FetchingProcessRepository[Future],
                        processValidation: ProcessValidation,
                        deploymentService: DeploymentService,
                        processCategoryService: ProcessCategoryService,
                        exposeConfig: Boolean)
                       (implicit executionContext: ExecutionContext)
  extends BaseHttpService(config, processCategoryService, authenticator)
    with LazyLogging {

  private val appApiEndpoints = new AppApiEndpoints(authenticator.authenticationMethod())
  val allEndpoints = serverEndpoints.map(_.endpoint)

  def serverEndpoints: List[ServerEndpoint[Any, Future]] =
    List(
      healthCheck,
      buildInfo,
      processDeploymentHealthCheck,
      processValidationHealthCheck,
      userCategoriesWithProcessingTypes,
      processingTypeDataReload
    ) ::: (if (exposeConfig) serverConfigInfo :: Nil else Nil)

  private val healthCheck = {
    appApiEndpoints.appHealthCheckEndpoint
      .serverLogicSuccess { _ =>
        Future.successful(HealthCheckProcessSuccessResponseDto)
      }
  }

  private val processDeploymentHealthCheck = {
    appApiEndpoints.processDeploymentHealthCheckEndpoint
      .serverSecurityLogic(authorize[HealthCheckProcessErrorResponseDto])
      .serverLogic { implicit loggedUser =>
        _ =>
          problemStateByProcessName
            .map { set =>
              if (set.isEmpty) {
                Right(HealthCheckProcessSuccessResponseDto)
              } else {
                logger.warn(s"Scenarios with status PROBLEM: ${set.keys}")
                logger.debug(s"Scenarios with status PROBLEM: $set")
                Left(Left(HealthCheckProcessErrorResponseDto(
                  message = Some("Scenarios with status PROBLEM"),
                  processes = Some(set.keys.toSet)
                )))
              }
            }
            .recover {
              case NonFatal(e) =>
                logger.error("Failed to get statuses", e)
                Left(Left(HealthCheckProcessErrorResponseDto(
                  message = Some("Failed to retrieve job statuses"),
                  processes = None
                )))
            }
      }
  }

  private val processValidationHealthCheck = {
    appApiEndpoints.processValidationHealthCheckEndpoint
      .serverSecurityLogic(authorize[HealthCheckProcessErrorResponseDto])
      .serverLogic { implicit loggedUser =>
        _ =>
          processesWithValidationErrors.map { processes =>
            if (processes.isEmpty) {
              Right(HealthCheckProcessSuccessResponseDto)
            } else {
              Left(Left(HealthCheckProcessErrorResponseDto(
                message = Some("Scenarios with validation errors"),
                processes = Some(processes.toSet)
              )))
            }
          }
      }
  }

  private val buildInfo = {
    appApiEndpoints.buildInfoEndpoint
      .serverLogicSuccess { _ =>
        Future {
          import net.ceedubs.ficus.Ficus._
          val configuredBuildInfo = config.getAs[Map[String, String]]("globalBuildInfo").getOrElse(Map())
          val modelDataInfo: Map[ProcessingType, Map[String, String]] = modelData.all.mapValuesNow(_.configCreator.buildInfo())
          BuildInfoDto(BuildInfo.name, BuildInfo.gitCommit, BuildInfo.buildTime, BuildInfo.version, modelDataInfo, configuredBuildInfo)
        }
      }
  }

  private val serverConfigInfo = {
    appApiEndpoints.serverConfigEndpoint
      .serverSecurityLogic(authorizeAdmin[Unit])
      .serverLogic { _ => _ =>
        Future {
          val configJson = parser.parse(config.root().render(ConfigRenderOptions.concise())).left.map(_.message)
          configJson match {
            case Right(json) =>
              Right(ServerConfigInfoDto(json))
            case Left(errorMessage) =>
              logger.error(s"Cannot create JSON from the Nussknacker configuration. Error: $errorMessage")
              throw new Exception("Cannot prepare configuration")
          }
        }
      }
  }

  private val userCategoriesWithProcessingTypes = {
    appApiEndpoints.userCategoriesWithProcessingTypesEndpoint
      .serverSecurityLogic(authorize[Unit])
      .serverLogicSuccess { loggedUser =>
        _ =>
          Future {
            UserCategoriesWithProcessingTypesDto(processCategoryService.getUserCategoriesWithType(loggedUser))
          }
      }
  }

  private val processingTypeDataReload =
    appApiEndpoints.processingTypeDataReloadEndpoint
      .serverSecurityLogic(authorizeAdmin[Unit])
      .serverLogic { _ => _ =>
        Future(Right {
          processingTypeDataReloader.reloadAll()
        })
      }

  private def problemStateByProcessName(implicit user: LoggedUser): Future[Map[String, ProcessState]] = {
    for {
      processes <- processRepository.fetchProcessesDetails[Unit](FetchProcessesDetailsQuery.deployed)
      statusMap <- Future.sequence(mapNameToProcessState(processes)).map(_.toMap)
      withProblem = statusMap.collect {
        case (name, processStatus@ProcessState(_, _@ProblemStateStatus(_, _), _, _, _, _, _, _, _, _)) => (name, processStatus)
      }
    } yield withProblem
  }

  private def mapNameToProcessState(processes: Seq[BaseProcessDetails[_]])(implicit user: LoggedUser): Seq[Future[(String, ProcessState)]] = {
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
