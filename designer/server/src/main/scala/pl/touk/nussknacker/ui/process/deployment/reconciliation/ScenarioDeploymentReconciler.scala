package pl.touk.nussknacker.ui.process.deployment.reconciliation

import cats.data.Validated
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.JobsRecoverySettings
import pl.touk.nussknacker.engine.api.component.NodesDeploymentData
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.DataFreshnessPolicy.Fresh
import pl.touk.nussknacker.engine.api.deployment.DeploymentUpdateStrategy.StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment._
import pl.touk.nussknacker.restmodel.validation.PrettyValidationErrors
import pl.touk.nussknacker.ui.process.ScenarioQuery
import pl.touk.nussknacker.ui.process.deployment.ScenarioResolver
import pl.touk.nussknacker.ui.process.deployment.deploymentstatus.EngineSideDeploymentStatusesProvider
import pl.touk.nussknacker.ui.process.processingtype.provider.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository.{
  DBIOActionRunner,
  FetchingProcessRepository,
  ScenarioActionRepository,
  ScenarioWithDetailsEntity
}
import pl.touk.nussknacker.ui.security.api.{LoggedUser, NussknackerInternalUser}
import slick.dbio.DBIOAction

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class ScenarioDeploymentReconciler(
    processingTypeServicesProvider: ProcessingTypeDataProvider[
      ScenarioDeploymentReconciler.ProcessingTypeServicesDeps,
      _
    ],
    deploymentStatusesProvider: EngineSideDeploymentStatusesProvider,
    actionRepository: ScenarioActionRepository,
    scenarioRepository: FetchingProcessRepository[Future],
    dbioActionRunner: DBIOActionRunner
)(implicit ec: ExecutionContext)
    extends LazyLogging {

  // We have to synchronize these statuses because engines (for example Flink) might have jobs retention mechanism
  // and finished jobs will disappear eventually on their side
  def synchronizeEngineFinishedDeploymentsLocalStatuses(): Future[Unit] = {
    implicit val user: LoggedUser                     = NussknackerInternalUser.instance
    implicit val freshnessPolicy: DataFreshnessPolicy = Fresh
    logger.debug("Synchronization of local status of finished deployments...")
    for {
      // Currently, synchronization is supported only for DeploymentManagers that supports DeploymentsStatusesQueryForAllScenarios
      bulkQueriedStatuses <- deploymentStatusesProvider.getBulkQueriedDeploymentStatusesForSupportedManagers(
        processingTypeServicesProvider.all.keys
      )
      deploymentStatuses = bulkQueriedStatuses.getAllDeploymentStatuses
      // We compare status by instances instead of by names. Thanks to that, PeriodicStateStatus won't be handled.
      // It is an expected behaviour because schedules finished status is handled inside PeriodicProcessService
      finishedDeploymentIds = deploymentStatuses
        .filter(status => SimpleStateStatus.isFinished(status.status))
        .flatMap(_.deploymentId)
      actionsIds = finishedDeploymentIds.flatMap(_.toActionIdOpt)
      actionsWithMarkingExecutionFinishedResult <- dbioActionRunner.run(DBIOAction.sequence(actionsIds.map { actionId =>
        actionRepository.markFinishedActionAsExecutionFinished(actionId).map(actionId -> _)
      }))
    } yield {
      val actionsMarkedAsExecutionFinished = actionsWithMarkingExecutionFinishedResult.collect {
        case (actionId, true) => actionId.toString
      }.toList
      if (actionsMarkedAsExecutionFinished.isEmpty) {
        logger.debug("None action marked as execution finished")
      } else {
        logger.debug(actionsMarkedAsExecutionFinished.mkString("Actions marked as execution finished: ", ", ", ""))
      }
    }
  }

  def recoverNotRunningDeploymentsThatShouldBeRunning(
      shouldRecover: JobsRecoverySettings => Boolean,
      isLeader: () => Boolean,
  ): Future[Unit] = {
    implicit val user: LoggedUser = NussknackerInternalUser.instance
    if (!isLeader()) {
      logger.info("Not a leader — skipping deployments recovery.")
      Future.unit
    } else {
      val processingTypeForWhichJobsShouldBeRecovered = processingTypeServicesProvider.all.toList.collect {
        case (processingType, processingTypeServices) if shouldRecover(processingTypeServices.jobsRecoverySettings) =>
          processingType
      }
      for {
        notFinishedDeploymentsThatAreNotRunning <- collectNotFinishedDeploymentsThatAreNotRunning(
          processingTypeForWhichJobsShouldBeRecovered
        )
        _ = logRecoveryBegin(notFinishedDeploymentsThatAreNotRunning)
        runDeploymentCommandsByProcessingType <-
          Future
            .sequence(notFinishedDeploymentsThatAreNotRunning.map { case (scenario, deploymentId) =>
              prepareRunDeploymentCommandForValidScenario(scenario, deploymentId)
            })
            .map(_.flatten)
        recoveryResult <- runDeploymentsCommandOneByOne(runDeploymentCommandsByProcessingType, isLeader)
        _ = logRecoveryEnd(recoveryResult)
      } yield ()
    }
  }

  // We are not using just Futures.sequence because we don't want to generate too much load and steal resources for other operational purposes
  private def runDeploymentsCommandOneByOne(
      runDeploymentCommandsByProcessingType: List[(ProcessingType, DMRunDeploymentCommand)],
      isLeader: () => Boolean,
  )(implicit user: LoggedUser): Future[List[Try[Unit]]] = {
    def loop(
        remaining: List[(ProcessingType, DMRunDeploymentCommand)],
        results: Vector[Try[Unit]],
    ): Future[Vector[Try[Unit]]] =
      remaining match {
        case Nil              => Future.successful(results)
        case _ if !isLeader() =>
          // Best-effort fence: isLeader() may flip to false mid-recovery. Stop starting new
          // deployments — the next leader re-runs recovery and picks up the rest.
          logger.warn(
            s"Lost leadership during recovery — aborting remaining ${remaining.size} deployment(s); " +
              "the next leader will recover them."
          )
          Future.successful(results)
        case (processingType, deployCommand) :: tail =>
          recoverScenarioJob(processingType, deployCommand).flatMap(result => loop(tail, results :+ result))
      }
    loop(runDeploymentCommandsByProcessingType, Vector.empty).map(_.toList)
  }

  private def logRecoveryBegin(
      notFinishedDeploymentsThatAreNotRunning: List[(ScenarioWithDetailsEntity[CanonicalProcess], DeploymentId)]
  ): Unit = {
    if (notFinishedDeploymentsThatAreNotRunning.isEmpty) {
      logger.info(
        s"No jobs to recover."
      )
    } else {
      logger.info(
        s"Starting jobs recovery process. ${notFinishedDeploymentsThatAreNotRunning.size} jobs to recover."
      )
    }
  }

  private def logRecoveryEnd(
      recoveryResult: List[Try[Unit]]
  ): Unit = {
    if (recoveryResult.nonEmpty) {
      val (successes, failures) = recoveryResult.partition(_.isSuccess)
      logger.info(
        s"Jobs recovery process finished. $successes jobs recovered successfully, $failures jobs recovery failed."
      )
    }
  }

  private def collectNotFinishedDeploymentsThatAreNotRunning(
      processingTypeForWhichJobsShouldBeRecovered: Iterable[ProcessingType]
  )(implicit user: LoggedUser): Future[List[(ScenarioWithDetailsEntity[CanonicalProcess], DeploymentId)]] = {
    implicit val freshnessPolicy: DataFreshnessPolicy = Fresh
    for {
      // In the perfect World we would fetch all deployments that are not finished for all scenarios, but with deployments modelled as actions, we have to fetch deployed scenarios
      // (having last state action = deploy and state = finished (not execution finished)). See comment in newdeployment.DeploymentService
      lastDeployedScenarios <- scenarioRepository.fetchLatestProcessesDetails[CanonicalProcess](
        ScenarioQuery.deployed.copy(processingTypes = Some(processingTypeForWhichJobsShouldBeRecovered))
      )
      notFinishedDeploymentsForScenario = lastDeployedScenarios.map(scenario =>
        (scenario, DeploymentId.fromActionId(scenario.lastDeployedAction.get.id))
      )
      // Currently, job recovery is supported only for DeploymentManagers that supports DeploymentsStatusesQueryForAllScenarios
      bulkQueriedStatuses <- deploymentStatusesProvider.getBulkQueriedDeploymentStatusesForSupportedManagers(
        processingTypeForWhichJobsShouldBeRecovered
      )
      notFinishedDeploymentsThatAreNotRunning = notFinishedDeploymentsForScenario.filter {
        case (scenario, deploymentId) =>
          val bulkQueriedStatusForScenario = bulkQueriedStatuses.getDeploymentStatusesUnsafe(scenario.idData).value
          !bulkQueriedStatusForScenario.exists(status =>
            status.deploymentId.contains(deploymentId) && SimpleStateStatus.isDefaultFollowingDeployStatus(
              status.status
            )
          )
      }
    } yield notFinishedDeploymentsThatAreNotRunning
  }

  private def prepareRunDeploymentCommandForValidScenario(
      scenario: ScenarioWithDetailsEntity[CanonicalProcess],
      deploymentId: DeploymentId
  )(implicit user: LoggedUser): Future[Option[(ProcessingType, DMRunDeploymentCommand)]] = {
    val lastDeployAction = scenario.lastDeployedAction.get
    // TODO: what should be in user name?
    val deployingUser = User(lastDeployAction.user, lastDeployAction.user)
    val deploymentData = DeploymentData(
      deploymentId,
      deployingUser,
      // TODO: Store this data and use them during jobs recovery. Currently after restart some jobs will work differently
      nodesData = NodesDeploymentData.empty,
      additionalDeploymentData = Map.empty,
      // TODO: is it correct?
      additionalModelConfigs = AdditionalModelConfigs.empty
    )
    processingTypeServicesProvider
      .forProcessingTypeUnsafe(scenario.processingType)
      .scenarioResolver
      .resolveScenario(scenario.json)
      .map {
        case Validated.Valid(resolvedScenario) =>
          Some(
            scenario.processingType -> DMRunDeploymentCommand(
              scenario.toEngineProcessVersion.copy(versionId = lastDeployAction.processVersionId),
              deploymentData,
              resolvedScenario,
              // This strategy has no sense in our case, see notice next to DeploymentUpdateStrategy
              DeploymentUpdateStrategy
                .ReplaceDeploymentWithSameScenarioName(RestoreStateFromReplacedJobSavepoint)
            )
          )
        case Validated.Invalid(errors) =>
          logger.error(
            s"Errors during scenario [${scenario.name}] resolution for job recovery purpose: " +
              s"${errors.map(PrettyValidationErrors.formatErrorMessage).toList.mkString}. Scenario won't be recovered"
          )
          None
      }
  }

  private def recoverScenarioJob(processingType: ProcessingType, deployCommand: DMRunDeploymentCommand)(
      implicit user: LoggedUser
  ): Future[Try[Unit]] = {
    val services = processingTypeServicesProvider
      .forProcessingTypeUnsafe(processingType)
    logger.info(
      s"Recovering scenario [${deployCommand.processVersion.processName}] deployment [${deployCommand.deploymentData.deploymentId}] on engine setup [${services.engineSetupName}]"
    )
    val deployManager = services.deploymentManager
    val deployResultFuture = {
      deployManager
        .processCommand(
          DMValidateScenarioCommand(
            processVersion = deployCommand.processVersion,
            deploymentData = deployCommand.deploymentData,
            canonicalProcess = deployCommand.canonicalProcess,
            updateStrategy = deployCommand.updateStrategy,
          )
        )
        .flatMap { _ =>
          deployManager.processCommand(deployCommand)
        }
    }
    deployResultFuture.transform {
      case Success(_) =>
        logger.info(
          s"Scenario [${deployCommand.processVersion.processName}] deployment [${deployCommand.deploymentData.deploymentId}] recovery on engine setup [${services.engineSetupName}] finished successfully"
        )
        Success(Success((): Unit))
      case Failure(ex) =>
        logger.warn(
          s"Scenario [${deployCommand.processVersion.processName}] deployment [${deployCommand.deploymentData.deploymentId}] recovery on engine setup [${services.engineSetupName}] failed. Application will start anyway.",
          ex
        )
        Success(Failure(ex))
    }
  }

}

object ScenarioDeploymentReconciler {

  final class ProcessingTypeServicesDeps(
      val deploymentManager: DeploymentManager,
      val engineSetupName: EngineSetupName,
      val jobsRecoverySettings: JobsRecoverySettings,
      val scenarioResolver: ScenarioResolver
  )

}
