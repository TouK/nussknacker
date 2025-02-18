package pl.touk.nussknacker.ui.process.periodic

import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.component.{
  ComponentAdditionalConfig,
  DesignerWideComponentId,
  NodesDeploymentData
}
import pl.touk.nussknacker.engine.api.deployment.StateStatus.StatusName
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.scheduler.model.{ScheduleProperty => _, _}
import pl.touk.nussknacker.engine.api.deployment.scheduler.services._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.ProblemStateStatus
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessName, VersionId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{AdditionalModelConfigs, DeploymentData, DeploymentId}
import pl.touk.nussknacker.engine.util.AdditionalComponentConfigsForRuntimeExtractor
import pl.touk.nussknacker.ui.process.periodic.PeriodicProcessService._
import pl.touk.nussknacker.ui.process.periodic.PeriodicStateStatus._
import pl.touk.nussknacker.ui.process.periodic.model.PeriodicProcessDeploymentStatus.PeriodicProcessDeploymentStatus
import pl.touk.nussknacker.ui.process.periodic.model._
import pl.touk.nussknacker.ui.process.periodic.utils.DeterministicUUIDFromLong
import pl.touk.nussknacker.ui.process.repository.PeriodicProcessesRepository

import java.time.chrono.ChronoLocalDateTime
import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant, LocalDateTime}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class PeriodicProcessService(
    delegateDeploymentManager: DeploymentManager,
    scheduledExecutionPerformer: ScheduledExecutionPerformer,
    periodicProcessesRepository: PeriodicProcessesRepository,
    periodicProcessListener: ScheduledProcessListener,
    additionalDeploymentDataProvider: AdditionalDeploymentDataProvider,
    deploymentRetryConfig: DeploymentRetryConfig,
    executionConfig: PeriodicExecutionConfig,
    maxFetchedPeriodicScenarioActivities: Option[Int],
    processConfigEnricher: ProcessConfigEnricher,
    clock: Clock,
    actionService: ProcessingTypeActionService,
    configsFromProvider: Map[DesignerWideComponentId, ComponentAdditionalConfig],
)(implicit ec: ExecutionContext)
    extends LazyLogging {

  import cats.syntax.all._
  import periodicProcessesRepository._

  private type Callback        = () => Future[Unit]
  private type NeedsReschedule = Boolean

  private implicit class WithCallbacksSeq(result: Future[List[Callback]]) {
    def runWithCallbacks: Future[Unit] =
      result.flatMap(callbacks => Future.sequence(callbacks.map(_()))).map(_ => ())
  }

  private val emptyCallback: Callback = () => Future.successful(())

  private implicit val localDateTimeOrdering: Ordering[LocalDateTime] = Ordering.by(identity[ChronoLocalDateTime[_]])

  def getScenarioActivitiesSpecificToPeriodicProcess(
      processIdWithName: ProcessIdWithName,
      after: Option[Instant],
  ): Future[List[ScenarioActivity]] = for {
    schedulesState <- periodicProcessesRepository
      .getSchedulesState(
        processIdWithName.name,
        after.map(localDateTimeAtSystemDefaultZone)
      )
      .run
    groupedByProcess        = schedulesState.groupedByPeriodicProcess
    deployments             = groupedByProcess.flatMap(_.deployments)
    deploymentsWithStatuses = deployments.flatMap(d => scheduledExecutionStatusAndDateFinished(d).map((d, _)))
    activities = deploymentsWithStatuses.map { case (deployment, metadata) =>
      ScenarioActivity.PerformedScheduledExecution(
        scenarioId = ScenarioId(processIdWithName.id.value),
        // The periodic process executions are stored in the PeriodicProcessService datasource, with ids of type Long
        // We need the ScenarioActivityId to be a unique UUID, generated in an idempotent way from Long id.
        // It is important, because if the ScenarioActivityId would change, the activity may be treated as a new one,
        // and, for example, GUI may have to refresh more often than necessary .
        scenarioActivityId = ScenarioActivityId(DeterministicUUIDFromLong.longUUID(deployment.id.value)),
        user = ScenarioUser.internalNuUser,
        date = metadata.dateDeployed.getOrElse(metadata.dateFinished),
        scenarioVersionId = Some(ScenarioVersionId.from(deployment.periodicProcess.deploymentData.versionId)),
        scheduledExecutionStatus = metadata.status,
        dateFinished = metadata.dateFinished,
        scheduleName = deployment.scheduleName.display,
        createdAt = metadata.dateCreated,
        nextRetryAt = deployment.nextRetryAt.map(instantAtSystemDefaultZone),
        retriesLeft = deployment.nextRetryAt.map(_ => deployment.retriesLeft),
      )
    }
    limitedActivities = maxFetchedPeriodicScenarioActivities match {
      case Some(limit) => activities.sortBy(_.date).takeRight(limit)
      case None        => activities
    }
  } yield limitedActivities

  def schedule(
      schedule: ScheduleProperty,
      processVersion: ProcessVersion,
      canonicalProcess: CanonicalProcess,
      processActionId: ProcessActionId,
      beforeSchedule: => Future[Unit] = Future.unit
  ): Future[Unit] = {
    prepareInitialScheduleDates(schedule) match {
      case Right(scheduleDates) =>
        beforeSchedule.flatMap(_ =>
          scheduleWithInitialDates(schedule, processVersion, canonicalProcess, scheduleDates, processActionId)
        )
      case Left(error) =>
        Future.failed(error)
    }
  }

  def prepareInitialScheduleDates(
      schedule: ScheduleProperty
  ): Either[PeriodicProcessException, List[(ScheduleName, Option[LocalDateTime])]] = {
    val schedules = schedule match {
      case MultipleScheduleProperty(schedules) =>
        schedules
          .map { case (k, pp) =>
            pp.nextRunAt(clock).map(v => ScheduleName(Some(k)) -> v)
          }
          .toList
          .sequence
      case e: SingleScheduleProperty => e.nextRunAt(clock).map(t => List((ScheduleName(None), t)))
    }
    (schedules match {
      case Left(error) => Left(s"Problem with parsing periodic property: $error")
      case Right(scheduleDates) if scheduleDates.forall(_._2.isEmpty) =>
        Left(
          s"Problem with the scheduled date. It seems that a date from the past was configured in the CRON configuration."
        )
      case correctSchedules => correctSchedules
    }).left.map(new PeriodicProcessException(_))
  }

  private def scheduleWithInitialDates(
      scheduleProperty: ScheduleProperty,
      processVersion: ProcessVersion,
      canonicalProcess: CanonicalProcess,
      scheduleDates: List[(ScheduleName, Option[LocalDateTime])],
      processActionId: ProcessActionId,
  ): Future[Unit] = {
    logger.info("Scheduling periodic scenario: {} on {}", processVersion, scheduleDates)

    for {
      inputConfigDuringExecution <- scheduledExecutionPerformer.provideInputConfigDuringExecutionJson()
      deploymentWithJarData <- scheduledExecutionPerformer.prepareDeploymentWithRuntimeParams(
        processVersion,
      )
      enrichedProcessConfig <- processConfigEnricher.onInitialSchedule(
        ProcessConfigEnricher.InitialScheduleData(canonicalProcess, inputConfigDuringExecution.serialized)
      )
      _ <- initialSchedule(
        scheduleProperty,
        scheduleDates,
        deploymentWithJarData,
        canonicalProcess,
        enrichedProcessConfig.inputConfigDuringExecutionJson,
        processActionId,
      )
    } yield ()
  }

  private def initialSchedule(
      scheduleMap: ScheduleProperty,
      scheduleDates: List[(ScheduleName, Option[LocalDateTime])],
      deploymentWithJarData: DeploymentWithRuntimeParams,
      canonicalProcess: CanonicalProcess,
      inputConfigDuringExecutionJson: String,
      processActionId: ProcessActionId,
  ): Future[Unit] = {
    periodicProcessesRepository
      .create(
        deploymentWithJarData,
        inputConfigDuringExecutionJson,
        canonicalProcess,
        scheduleMap,
        processActionId
      )
      .run
      .flatMap { process =>
        scheduleDates.collect {
          case (name, Some(date)) =>
            periodicProcessesRepository
              .schedule(process.id, name, date, deploymentRetryConfig.deployMaxRetries)
              .run
              .flatMap { data =>
                handleEvent(ScheduledEvent(data.toDetails, firstSchedule = true))
              }
          case (name, None) =>
            logger.warn(s"Schedule $name does not have date to schedule")
            Future.successful(())
        }.sequence
      }
      .map(_ => ())
  }

  def findToBeDeployed: Future[Seq[PeriodicProcessDeployment]] = {
    for {
      toBeDeployed <- periodicProcessesRepository.findToBeDeployed.run.flatMap { toDeployList =>
        Future.sequence(toDeployList.map(checkIfNotRunning)).map(_.flatten)
      }
      // We retry scenarios that failed on deployment. Failure recovery of running scenarios should be handled by Flink's restart strategy
      toBeRetried <- periodicProcessesRepository.findToBeRetried.run
      // We don't block scheduled deployments by retries
    } yield toBeDeployed.sortBy(d => (d.runAt, d.createdAt)) ++ toBeRetried.sortBy(d => (d.nextRetryAt, d.createdAt))
  }

  // Currently we don't allow simultaneous runs of one scenario - only sequential, so if other schedule kicks in, it'll have to wait
  // TODO: we show allow to deploy scenarios with different scheduleName to be deployed simultaneous
  private def checkIfNotRunning(
      toDeploy: PeriodicProcessDeployment
  ): Future[Option[PeriodicProcessDeployment]] = {
    delegateDeploymentManager
      .getScenarioDeploymentsStatuses(toDeploy.periodicProcess.deploymentData.processName)(DataFreshnessPolicy.Fresh)
      .map(
        _.value
          .map(_.status)
          .find(SimpleStateStatus.DefaultFollowingDeployStatuses.contains)
          .map { _ =>
            logger.debug(s"Deferring run of ${toDeploy.display} as scenario is currently running")
            None
          }
          .getOrElse(Some(toDeploy))
      )
  }

  def handleFinished: Future[Unit] = {
    def handleSingleProcess(processName: ProcessName, schedules: SchedulesState): Future[Unit] =
      synchronizeDeploymentsStates(processName, schedules)
        .flatMap { case (_, needRescheduleDeploymentIds) =>
          schedules.groupedByPeriodicProcess
            .collect {
              case processScheduleData
                  if processScheduleData.deployments.exists(d => needRescheduleDeploymentIds.contains(d.id)) =>
                reschedule(processScheduleData, needRescheduleDeploymentIds)
            }
            .sequence
            .runWithCallbacks
        }

    for {
      schedules <- periodicProcessesRepository
        .findActiveSchedulesForProcessesHavingDeploymentWithMatchingStatus(
          Set(PeriodicProcessDeploymentStatus.Deployed, PeriodicProcessDeploymentStatus.FailedOnDeploy),
        )
        .run
      // we handle each job separately, if we fail at some point, we will continue on next handleFinished run
      _ <- Future.sequence(schedules.groupByProcessName.toList.map(handleSingleProcess _ tupled))
    } yield ()
  }

  // Returns tuple of lists:
  // - matching, active deployment ids on DM side
  // - deployment ids that need to be reschedules
  private def synchronizeDeploymentsStates(
      processName: ProcessName,
      schedules: SchedulesState
  ): Future[(Set[PeriodicProcessDeploymentId], Set[PeriodicProcessDeploymentId])] =
    for {
      runtimeStatuses <- delegateDeploymentManager
        .getScenarioDeploymentsStatuses(processName)(DataFreshnessPolicy.Fresh)
        .map(_.value)
      _ = logger.debug(s"Process '$processName' runtime statuses: ${runtimeStatuses.map(_.toString)}")
      scheduleDeploymentsWithStatus = schedules.schedules.values.toList.flatMap { scheduleData =>
        logger.debug(
          s"Process '$processName' latest deployment ids: ${scheduleData.latestDeployments.map(_.id.toString)}"
        )
        scheduleData.latestDeployments.map { deployment =>
          (
            scheduleData.process.deploymentData.processName,
            scheduleData.process.deploymentData.versionId,
            deployment,
            runtimeStatuses.getStatus(deployment.id)
          )
        }
      }
      _ = logger.debug(
        s"Process '$processName' schedule deployments with status: ${scheduleDeploymentsWithStatus.map(_.toString)}"
      )
      needRescheduleDeployments <- Future
        .sequence(scheduleDeploymentsWithStatus.map { case (processName, versionId, deploymentData, statusOpt) =>
          synchronizeDeploymentState(processName, versionId, deploymentData, statusOpt).map { needReschedule =>
            Option(deploymentData.id).filter(_ => needReschedule)
          }
        })
        .map(_.flatten.toSet)
      followingDeployDeploymentsForSchedules = scheduleDeploymentsWithStatus.collect {
        case (_, _, deployment, Some(status))
            if SimpleStateStatus.DefaultFollowingDeployStatuses.contains(status.status) =>
          deployment.id
      }.toSet
    } yield (followingDeployDeploymentsForSchedules, needRescheduleDeployments)

  // We assume that this method leaves with data in consistent state
  private def synchronizeDeploymentState(
      processName: ProcessName,
      versionId: VersionId,
      deployment: ScheduleDeploymentData,
      statusDetails: Option[DeploymentStatusDetails],
  ): Future[NeedsReschedule] = {
    implicit class RichFuture[Unit](a: Future[Unit]) {
      def needsReschedule(value: Boolean): Future[NeedsReschedule] = a.map(_ => value)
    }
    statusDetails.map(_.status) match {
      case Some(status)
          if ProblemStateStatus.isProblemStatus(
            status
          ) && deployment.state.status != PeriodicProcessDeploymentStatus.Failed =>
        markFailedAction(deployment, statusDetails).needsReschedule(executionConfig.rescheduleOnFailure)
      case Some(status)
          if EngineStatusesToReschedule.contains(
            status
          ) && deployment.state.status != PeriodicProcessDeploymentStatus.Finished =>
        markFinished(processName, versionId, deployment, statusDetails).needsReschedule(value = true)
      case None
          if deployment.state.status == PeriodicProcessDeploymentStatus.Deployed
            && deployment.deployedAt.exists(_.isBefore(LocalDateTime.now().minusMinutes(5))) =>
        // status is None if DeploymentManager isn't aware of a job that was just deployed
        // this can be caused by a race in e.g. FlinkRestManager
        // (because /jobs/overview used in getProcessStates isn't instantly aware of submitted jobs)
        // so freshly deployed deployments aren't considered
        markFinished(processName, versionId, deployment, statusDetails).needsReschedule(value = true)
      case _ =>
        Future.successful(()).needsReschedule(value = false)
    }
  }

  private def reschedule(
      processScheduleData: PeriodicProcessScheduleData,
      needRescheduleDeploymentIds: Set[PeriodicProcessDeploymentId]
  ): Future[Callback] = {
    import processScheduleData._
    val scheduleActions = deployments.map { deployment =>
      if (needRescheduleDeploymentIds.contains(deployment.id))
        nextRunAt(deployment, clock) match {
          case Right(Some(futureDate)) =>
            logger.info(s"Rescheduling ${deployment.display} to $futureDate")
            val action = periodicProcessesRepository
              .schedule(process.id, deployment.scheduleName, futureDate, deploymentRetryConfig.deployMaxRetries)
              .run
              .flatMap { data =>
                handleEvent(ScheduledEvent(data.toDetails, firstSchedule = false))
              }
            Some(action)
          case Right(None) =>
            logger.info(s"No next run of ${deployment.display}")
            None
          case Left(error) =>
            logger.error(s"Wrong periodic property, error: $error for ${deployment.display}")
            None
        }
      else
        Option(deployment)
          .filter(_.state.status == PeriodicProcessDeploymentStatus.Scheduled)
          .map(_ => Future.successful(()))

    }

    if (scheduleActions.forall(_.isEmpty)) {
      logger.info(s"No scheduled deployments for periodic process: ${process.id.value}. Deactivating")
      deactivateAction(process).flatMap { _ =>
        markProcessActionExecutionFinished(processScheduleData.process.processActionId)
      }

    } else
      scheduleActions.flatten.sequence.as(emptyCallback)
  }

  private def nextRunAt(
      deployment: PeriodicProcessDeployment,
      clock: Clock
  ): Either[String, Option[LocalDateTime]] =
    (deployment.periodicProcess.scheduleProperty, deployment.scheduleName.value) match {
      case (MultipleScheduleProperty(schedules), Some(name)) =>
        schedules.get(name).toRight(s"Failed to find schedule: $deployment.scheduleName").flatMap(_.nextRunAt(clock))
      case (e: SingleScheduleProperty, None) => e.nextRunAt(clock)
      case (schedule, name)                  => Left(s"Schedule name: $name mismatch with schedule: $schedule")
    }

  private def markFinished(
      processName: ProcessName,
      versionId: VersionId,
      deployment: ScheduleDeploymentData,
      state: Option[DeploymentStatusDetails],
  ): Future[Unit] = {
    logger.info(s"Marking ${deployment.display} with status: ${deployment.state.status} as finished")
    for {
      _                   <- periodicProcessesRepository.markFinished(deployment.id).run
      currentState        <- periodicProcessesRepository.findProcessData(deployment.id).run
      canonicalProcessOpt <- periodicProcessesRepository.fetchCanonicalProcess(deployment.periodicProcessId).run
      canonicalProcess = canonicalProcessOpt.getOrElse {
        throw new PeriodicProcessException(
          s"Could not fetch CanonicalProcess with ProcessVersion for processName=$processName, versionId=$versionId"
        )
      }
    } yield handleEvent(FinishedEvent(currentState.toDetails, canonicalProcess, state))
  }

  private def handleFailedDeployment(
      deployment: PeriodicProcessDeployment,
      state: Option[DeploymentStatusDetails]
  ): Future[Unit] = {
    def calculateNextRetryAt = now().plus(deploymentRetryConfig.deployRetryPenalize.toMillis, ChronoUnit.MILLIS)

    val retriesLeft =
      // case of initial deploy - not a retry
      if (deployment.nextRetryAt.isEmpty) deployment.retriesLeft
      else deployment.retriesLeft - 1

    val (nextRetryAt, status) =
      if (retriesLeft < 1)
        (None, PeriodicProcessDeploymentStatus.FailedOnDeploy)
      else
        (Some(calculateNextRetryAt), PeriodicProcessDeploymentStatus.RetryingDeploy)

    logger.info(
      s"Marking ${deployment.display} as $status. Retries left: $retriesLeft. Next retry at: ${nextRetryAt.getOrElse("-")}"
    )

    for {
      _ <- periodicProcessesRepository.markFailedOnDeployWithStatus(deployment.id, status, retriesLeft, nextRetryAt).run
      currentState <- periodicProcessesRepository.findProcessData(deployment.id).run
    } yield handleEvent(FailedOnDeployEvent(currentState.toDetails, state))
  }

  private def markFailedAction(
      deployment: ScheduleDeploymentData,
      state: Option[DeploymentStatusDetails]
  ): Future[Unit] = {
    logger.info(s"Marking ${deployment.display} as failed.")
    for {
      _            <- periodicProcessesRepository.markFailed(deployment.id).run
      currentState <- periodicProcessesRepository.findProcessData(deployment.id).run
    } yield handleEvent(FailedOnRunEvent(currentState.toDetails, state))
  }

  def deactivate(processName: ProcessName): Future[Iterable[DeploymentId]] =
    for {
      activeSchedules                     <- getLatestDeploymentsForActiveSchedules(processName)
      (runningDeploymentsForSchedules, _) <- synchronizeDeploymentsStates(processName, activeSchedules)
      _ <- activeSchedules.groupedByPeriodicProcess.map(p => deactivateAction(p.process)).sequence.runWithCallbacks
    } yield runningDeploymentsForSchedules.map(deployment => DeploymentId(deployment.toString))

  private def deactivateAction(
      process: PeriodicProcess
  ): Future[Callback] = {
    logger.info(s"Deactivate periodic process id: ${process.id.value}")
    for {
      _ <- periodicProcessesRepository.markInactive(process.id).run
      // we want to delete jars only after we successfully mark process as inactive. It's better to leave jar garbage than
      // have process without jar
    } yield () => scheduledExecutionPerformer.cleanAfterDeployment(process.deploymentData.runtimeParams)
  }

  private def markProcessActionExecutionFinished(
      processActionIdOption: Option[ProcessActionId]
  ): Future[Callback] =
    Future.successful { () =>
      processActionIdOption
        .map(actionService.markActionExecutionFinished)
        .sequence
        .map(_ => ())
    }

  def deploy(deployment: PeriodicProcessDeployment): Future[Unit] = {
    // TODO: set status before deployment?
    val id = deployment.id
    val deploymentData = DeploymentData(
      DeploymentId(id.toString),
      DeploymentData.systemUser,
      additionalDeploymentDataProvider.prepareAdditionalData(deployment.toDetails),
      // TODO: in the future we could allow users to specify nodes data during schedule requesting
      NodesDeploymentData.empty,
      AdditionalModelConfigs(
        AdditionalComponentConfigsForRuntimeExtractor.getRequiredAdditionalConfigsForRuntime(configsFromProvider)
      )
    )
    val deploymentWithJarData = deployment.periodicProcess.deploymentData
    val deploymentAction = for {
      _ <- Future.successful(
        logger.info("Deploying scenario {} for deployment id {}", deploymentWithJarData, id)
      )
      processName = deploymentWithJarData.processName
      versionId   = deploymentWithJarData.versionId
      canonicalProcessOpt <- periodicProcessesRepository.fetchCanonicalProcess(deployment.periodicProcess.id).run
      canonicalProcess = canonicalProcessOpt.getOrElse {
        throw new PeriodicProcessException(
          s"Could not fetch CanonicalProcess for processName=$processName, versionId=$versionId"
        )
      }
      processVersionOpt <- periodicProcessesRepository.fetchProcessVersion(processName, versionId)
      processVersion = processVersionOpt.getOrElse {
        throw new PeriodicProcessException(
          s"Could not fetch ProcessVersion for processName=$processName, versionId=$versionId"
        )
      }
      inputConfigDuringExecutionJsonOpt <- periodicProcessesRepository
        .fetchInputConfigDuringExecutionJson(deployment.periodicProcess.id)
        .run
      inputConfigDuringExecutionJson = inputConfigDuringExecutionJsonOpt.getOrElse {
        throw new PeriodicProcessException(
          s"Could not fetch inputConfigDuringExecutionJson for processName=${processName}, versionId=${versionId}"
        )
      }
      enrichedProcessConfig <- processConfigEnricher.onDeploy(
        ProcessConfigEnricher.DeployData(
          canonicalProcess,
          processVersion,
          inputConfigDuringExecutionJson,
          deployment.toDetails
        )
      )
      externalDeploymentId <- scheduledExecutionPerformer.deployWithRuntimeParams(
        deploymentWithJarData,
        enrichedProcessConfig.inputConfigDuringExecutionJson,
        deploymentData,
        canonicalProcess,
        processVersion,
      )
    } yield externalDeploymentId
    deploymentAction
      .flatMap { externalDeploymentId =>
        logger.info("Scenario has been deployed {} for deployment id {}", deploymentWithJarData, id)
        // TODO: add externalDeploymentId??
        periodicProcessesRepository
          .markDeployed(id)
          .run
          .flatMap(_ => periodicProcessesRepository.findProcessData(id).run)
          .flatMap(afterChange => handleEvent(DeployedEvent(afterChange.toDetails, externalDeploymentId)))
      }
      // We can recover since deployment actor watches only future completion.
      .recoverWith { case exception =>
        logger.error(s"Scenario deployment ${deployment.display} failed", exception)
        handleFailedDeployment(deployment, None)
      }
  }

  // TODO: allow access to DB in listener?
  private def handleEvent(event: ScheduledProcessEvent): Future[Unit] = {
    Future.successful {
      try {
        periodicProcessListener.onScheduledProcessEvent.applyOrElse(event, (_: ScheduledProcessEvent) => ())
      } catch {
        case NonFatal(e) => throw new PeriodicProcessException("Failed to invoke listener", e)
      }
    }
  }

  private def now(): LocalDateTime = LocalDateTime.now(clock)

  def getMergedStatusDetails(
      name: ProcessName
  )(implicit freshnessPolicy: DataFreshnessPolicy): Future[WithDataFreshnessStatus[DeploymentStatusDetails]] = {
    delegateDeploymentManager.getScenarioDeploymentsStatuses(name).flatMap { statusesWithFreshness =>
      logger.debug(s"Statuses for $name: $statusesWithFreshness")
      mergeStatusWithDeployments(name, statusesWithFreshness.value).map { statusDetails =>
        statusesWithFreshness.copy(value = statusDetails)
      }
    }
  }

  def deploymentsStatusesQueryForAllScenariosSupport: DeploymentsStatusesQueryForAllScenariosSupport =
    delegateDeploymentManager.deploymentsStatusesQueryForAllScenariosSupport match {
      case supported: DeploymentsStatusesQueryForAllScenariosSupported =>
        new DeploymentsStatusesQueryForAllScenariosSupported {

          override def getAllScenariosDeploymentsStatuses()(
              implicit freshnessPolicy: DataFreshnessPolicy
          ): Future[WithDataFreshnessStatus[Map[ProcessName, List[DeploymentStatusDetails]]]] = {
            for {
              allStatusDetailsInDelegate <- supported.getAllScenariosDeploymentsStatuses()
              allStatusDetailsInPeriodic <- mergeStatusWithDeployments(allStatusDetailsInDelegate.value)
              result = allStatusDetailsInPeriodic.map { case (name, status) => (name, List(status)) }
            } yield allStatusDetailsInDelegate.map(_ => result)
          }

        }

      case NoDeploymentsStatusesQueryForAllScenariosSupport =>
        NoDeploymentsStatusesQueryForAllScenariosSupport
    }

  private def mergeStatusWithDeployments(
      name: ProcessName,
      runtimeStatuses: List[DeploymentStatusDetails]
  ): Future[DeploymentStatusDetails] = {
    def toDeploymentStatuses(schedulesState: SchedulesState) = schedulesState.schedules.toList
      .flatMap { case (scheduleId, scheduleData) =>
        scheduleData.latestDeployments.map { deployment =>
          PeriodicDeploymentStatus(
            deployment.id,
            scheduleId,
            deployment.createdAt,
            deployment.runAt,
            deployment.state.status,
            scheduleData.process.active,
            runtimeStatuses.getStatus(deployment.id)
          )
        }
      }
      .sorted(PeriodicDeploymentStatus.ordering.reverse)

    for {
      activeSchedules <- getLatestDeploymentsForActiveSchedules(name, MaxDeploymentsStatus)
      inactiveSchedules <- getLatestDeploymentsForLatestInactiveSchedules(
        name,
        MaxDeploymentsStatus,
        MaxDeploymentsStatus
      )
    } yield {
      val status = PeriodicProcessStatus(toDeploymentStatuses(activeSchedules), toDeploymentStatuses(inactiveSchedules))
      status.mergedStatusDetails
    }
  }

  private def mergeStatusWithDeployments(
      runtimeStatuses: Map[ProcessName, List[DeploymentStatusDetails]]
  ): Future[Map[ProcessName, DeploymentStatusDetails]] = {
    def toDeploymentStatuses(processName: ProcessName, schedulesState: SchedulesState) =
      schedulesState.schedules.toList
        .flatMap { case (scheduleId, scheduleData) =>
          scheduleData.latestDeployments.map { deployment =>
            PeriodicDeploymentStatus(
              deployment.id,
              scheduleId,
              deployment.createdAt,
              deployment.runAt,
              deployment.state.status,
              scheduleData.process.active,
              runtimeStatuses.getOrElse(processName, List.empty).getStatus(deployment.id)
            )
          }
        }
        .sorted(PeriodicDeploymentStatus.ordering.reverse)

    for {
      activeSchedules   <- getLatestDeploymentsForActiveSchedules(MaxDeploymentsStatus)
      inactiveSchedules <- getLatestDeploymentsForLatestInactiveSchedules(MaxDeploymentsStatus, MaxDeploymentsStatus)
    } yield {
      val allProcessNames = activeSchedules.keySet ++ inactiveSchedules.keySet
      allProcessNames.map { processName =>
        val activeSchedulesForProcess   = activeSchedules.getOrElse(processName, SchedulesState(Map.empty))
        val inactiveSchedulesForProcess = inactiveSchedules.getOrElse(processName, SchedulesState(Map.empty))
        val status = PeriodicProcessStatus(
          toDeploymentStatuses(processName, activeSchedulesForProcess),
          toDeploymentStatuses(processName, inactiveSchedulesForProcess)
        )
        (processName, status.mergedStatusDetails)
      }.toMap
    }
  }

  def getLatestDeploymentsForActiveSchedules(
      processName: ProcessName,
      deploymentsPerScheduleMaxCount: Int = 1
  ): Future[SchedulesState] =
    periodicProcessesRepository
      .getLatestDeploymentsForActiveSchedules(
        processName,
        deploymentsPerScheduleMaxCount,
      )
      .run

  def getLatestDeploymentsForActiveSchedules(
      deploymentsPerScheduleMaxCount: Int
  ): Future[Map[ProcessName, SchedulesState]] =
    periodicProcessesRepository.getLatestDeploymentsForActiveSchedules(deploymentsPerScheduleMaxCount).run

  def getLatestDeploymentsForLatestInactiveSchedules(
      processName: ProcessName,
      inactiveProcessesMaxCount: Int,
      deploymentsPerScheduleMaxCount: Int
  ): Future[SchedulesState] =
    periodicProcessesRepository
      .getLatestDeploymentsForLatestInactiveSchedules(
        processName,
        inactiveProcessesMaxCount,
        deploymentsPerScheduleMaxCount,
      )
      .run

  def getLatestDeploymentsForLatestInactiveSchedules(
      inactiveProcessesMaxCount: Int,
      deploymentsPerScheduleMaxCount: Int
  ): Future[Map[ProcessName, SchedulesState]] =
    periodicProcessesRepository
      .getLatestDeploymentsForLatestInactiveSchedules(
        inactiveProcessesMaxCount,
        deploymentsPerScheduleMaxCount
      )
      .run

  implicit class RuntimeStatusesExt(runtimeStatuses: List[DeploymentStatusDetails]) {

    private val runtimeStatusesMap = runtimeStatuses.flatMap(status => status.deploymentId.map(_ -> status)).toMap
    def getStatus(deploymentId: PeriodicProcessDeploymentId): Option[DeploymentStatusDetails] =
      runtimeStatusesMap.get(DeploymentId(deploymentId.toString))

  }

  private def scheduledExecutionStatusAndDateFinished(
      entity: PeriodicProcessDeployment,
  ): Option[FinishedScheduledExecutionMetadata] = {
    for {
      status <- entity.state.status match {
        case PeriodicProcessDeploymentStatus.Scheduled =>
          None
        case PeriodicProcessDeploymentStatus.Deployed =>
          None
        case PeriodicProcessDeploymentStatus.Finished =>
          Some(ScheduledExecutionStatus.Finished)
        case PeriodicProcessDeploymentStatus.Failed =>
          Some(ScheduledExecutionStatus.Failed)
        case PeriodicProcessDeploymentStatus.RetryingDeploy =>
          Some(ScheduledExecutionStatus.DeploymentWillBeRetried)
        case PeriodicProcessDeploymentStatus.FailedOnDeploy =>
          Some(ScheduledExecutionStatus.DeploymentFailed)
      }
      dateCreated  = instantAtSystemDefaultZone(entity.createdAt)
      dateDeployed = entity.state.deployedAt.map(instantAtSystemDefaultZone)
      dateFinished <- entity.state.completedAt.map(instantAtSystemDefaultZone)
    } yield FinishedScheduledExecutionMetadata(
      status = status,
      dateCreated = dateCreated,
      dateDeployed = dateDeployed,
      dateFinished = dateFinished
    )
  }

  // LocalDateTime's in the context of PeriodicProcess are created using clock with system default timezone
  private def instantAtSystemDefaultZone(localDateTime: LocalDateTime): Instant = {
    localDateTime.atZone(clock.getZone).toInstant
  }

  private def localDateTimeAtSystemDefaultZone(instant: Instant): LocalDateTime = {
    instant.atZone(clock.getZone).toLocalDateTime
  }

}

object PeriodicProcessService {

  // TODO: some configuration?
  private[periodic] val MaxDeploymentsStatus = 5

  private val DeploymentStatusesToReschedule =
    Set(PeriodicProcessDeploymentStatus.Deployed, PeriodicProcessDeploymentStatus.Failed)

  // Should we reschedule canceled status? It can be useful in case when job hang, we cancel it and want to be rescheduled
  private val EngineStatusesToReschedule = Set(SimpleStateStatus.Finished, SimpleStateStatus.Canceled)

  // This class represents status of all schedules for given scenario. It is designed to contain simple representation of statuses
  // for each historical and active deployments. mergedStatusDetails and methods below are for purpose of presentation
  // of single, merged status similar to this available for streaming job. This merged status should be a straightforward derivative
  // of these deployments statuses so it will be easy to figure out it by user.
  private case class PeriodicProcessStatus(
      activeDeploymentsStatuses: List[PeriodicDeploymentStatus],
      inactiveDeploymentsStatuses: List[PeriodicDeploymentStatus]
  ) extends LazyLogging {

    // Currently we don't present deployments - theirs statuses are available only in tooltip - because of that we have to pick
    // one "merged" status that will be presented to users
    def mergedStatusDetails: DeploymentStatusDetails = {
      def toPeriodicProcessStatusWithMergedStatus(mergedStatus: StateStatus) = PeriodicProcessStatusWithMergedStatus(
        activeDeploymentsStatuses,
        inactiveDeploymentsStatuses,
        mergedStatus
      )

      def createStatusDetails(mergedStatus: StateStatus, periodicDeploymentIdOpt: Option[PeriodicProcessDeploymentId]) =
        DeploymentStatusDetails(
          status = toPeriodicProcessStatusWithMergedStatus(mergedStatus),
          deploymentId = periodicDeploymentIdOpt.map(_.toString).map(DeploymentId(_)),
          version = None
        )

      pickMostImportantActiveDeployment(activeDeploymentsStatuses)
        .map { deploymentStatus =>
          if (deploymentStatus.isWaitingForReschedule) {
            deploymentStatus.runtimeStatusOpt
              .map(_.copy(status = toPeriodicProcessStatusWithMergedStatus(WaitingForScheduleStatus)))
              .getOrElse(createStatusDetails(WaitingForScheduleStatus, Some(deploymentStatus.deploymentId)))
          } else if (deploymentStatus.status == PeriodicProcessDeploymentStatus.Scheduled) {
            createStatusDetails(ScheduledStatus(deploymentStatus.runAt), Some(deploymentStatus.deploymentId))
          } else if (Set(PeriodicProcessDeploymentStatus.Failed, PeriodicProcessDeploymentStatus.FailedOnDeploy)
              .contains(deploymentStatus.status)) {
            createStatusDetails(ProblemStateStatus.Failed, Some(deploymentStatus.deploymentId))
          } else if (deploymentStatus.status == PeriodicProcessDeploymentStatus.RetryingDeploy) {
            createStatusDetails(SimpleStateStatus.DuringDeploy, Some(deploymentStatus.deploymentId))
          } else {
            deploymentStatus.runtimeStatusOpt
              .map(runtimeDetails =>
                runtimeDetails.copy(status = toPeriodicProcessStatusWithMergedStatus(runtimeDetails.status))
              )
              .getOrElse {
                createStatusDetails(WaitingForScheduleStatus, Some(deploymentStatus.deploymentId))
              }
          }
        }
        .getOrElse {
          if (inactiveDeploymentsStatuses.isEmpty) {
            createStatusDetails(SimpleStateStatus.NotDeployed, None)
          } else {
            val latestInactiveProcessId =
              inactiveDeploymentsStatuses.maxBy(_.scheduleId.processId.value).scheduleId.processId
            val latestDeploymentsForEachScheduleOfLatestProcessId = latestDeploymentForEachSchedule(
              inactiveDeploymentsStatuses.filter(_.scheduleId.processId == latestInactiveProcessId)
            )

            if (latestDeploymentsForEachScheduleOfLatestProcessId.forall(
                _.status == PeriodicProcessDeploymentStatus.Finished
              )) {
              createStatusDetails(SimpleStateStatus.Finished, None)
            } else {
              createStatusDetails(SimpleStateStatus.Canceled, None)
            }
          }
        }
    }

  }

  /**
   * Returns latest deployment. It can be in any status (consult [[PeriodicProcessDeploymentStatus]]).
   * For multiple schedules only single schedule is returned in the following order:
   * <ol>
   * <li>If there are any deployed scenarios, then the first one is returned. Please be aware that deployment of previous
   * schedule could fail.</li>
   * <li>If there are any failed scenarios, then the last one is returned. We want to inform user, that some deployments
   * failed and the scenario should be rescheduled/retried manually.
   * <li>If there are any scheduled scenarios, then the first one to be run is returned.
   * <li>If there are any finished scenarios, then the last one is returned. It should not happen because the scenario
   * should be deactivated earlier.
   * </ol>
   */
  private[periodic] def pickMostImportantActiveDeployment(
      activeDeploymentsStatuses: List[PeriodicDeploymentStatus]
  ): Option[PeriodicDeploymentStatus] = {
    val lastActiveDeploymentStatusForEachSchedule =
      latestDeploymentForEachSchedule(activeDeploymentsStatuses).sorted

    def first(status: PeriodicProcessDeploymentStatus) =
      lastActiveDeploymentStatusForEachSchedule.find(_.status == status)

    def last(status: PeriodicProcessDeploymentStatus) =
      lastActiveDeploymentStatusForEachSchedule.reverse.find(_.status == status)

    first(PeriodicProcessDeploymentStatus.Deployed)
      .orElse(last(PeriodicProcessDeploymentStatus.Failed))
      .orElse(last(PeriodicProcessDeploymentStatus.RetryingDeploy))
      .orElse(last(PeriodicProcessDeploymentStatus.FailedOnDeploy))
      .orElse(first(PeriodicProcessDeploymentStatus.Scheduled))
      .orElse(last(PeriodicProcessDeploymentStatus.Finished))
  }

  private def latestDeploymentForEachSchedule(deploymentsStatuses: List[PeriodicDeploymentStatus]) = {
    deploymentsStatuses
      .groupBy(_.scheduleId)
      .values
      .toList
      .map(_.min(PeriodicDeploymentStatus.ordering.reverse))
  }

  case class PeriodicProcessStatusWithMergedStatus(
      activeDeploymentsStatuses: List[PeriodicDeploymentStatus],
      inactiveDeploymentsStatuses: List[PeriodicDeploymentStatus],
      mergedStatus: StateStatus
  ) extends StateStatus {

    override def name: StatusName = mergedStatus.name

  }

  case class PeriodicDeploymentStatus( // Probably it is too much technical to present to users, but the only other alternative
      // to present to users is scheduleName+runAt
      deploymentId: PeriodicProcessDeploymentId,
      scheduleId: ScheduleId,
      createdAt: LocalDateTime,
      runAt: LocalDateTime,
      // This status is almost fine but:
      // - we don't have cancel status - we have to check processActive as well (isCanceled)
      // - sometimes we have to check runtimeStatusOpt (isWaitingForReschedule)
      status: PeriodicProcessDeploymentStatus,
      processActive: Boolean,
      // Some additional information that are available in StatusDetails returned by engine runtime
      runtimeStatusOpt: Option[DeploymentStatusDetails]
  ) {

    def scheduleName: ScheduleName = scheduleId.scheduleName

    def isWaitingForReschedule: Boolean = {
      processActive &&
      DeploymentStatusesToReschedule.contains(status) &&
      runtimeStatusOpt.exists(st => EngineStatusesToReschedule.contains(st.status))
    }

    def isCanceled: Boolean = {
      // We don't have Canceled status, because of that we base on tricky assumption. It can be wrong when we cancel
      // scenario just after it was marked as finished and is not rescheduled yet
      !processActive && status != PeriodicProcessDeploymentStatus.Finished
    }

  }

  object PeriodicDeploymentStatus {

    implicit val ordering: Ordering[PeriodicDeploymentStatus] =
      (self: PeriodicDeploymentStatus, that: PeriodicDeploymentStatus) => {
        self.runAt.compareTo(that.runAt) match {
          case 0 => self.createdAt.compareTo(that.createdAt)
          case a => a
        }
      }

  }

  private final case class FinishedScheduledExecutionMetadata(
      status: ScheduledExecutionStatus,
      dateCreated: Instant,
      dateDeployed: Option[Instant],
      dateFinished: Instant,
  )

}
