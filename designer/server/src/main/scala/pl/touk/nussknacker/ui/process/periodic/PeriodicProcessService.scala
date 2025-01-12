package pl.touk.nussknacker.ui.process.periodic

import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.component.{
  ComponentAdditionalConfig,
  DesignerWideComponentId,
  NodesDeploymentData
}
import pl.touk.nussknacker.engine.api.deployment.periodic.PeriodicDeploymentEngineHandler.DeploymentWithRuntimeParams
import pl.touk.nussknacker.engine.api.deployment.StateStatus.StatusName
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.periodic.PeriodicDeploymentEngineHandler
import pl.touk.nussknacker.ui.process.periodic.model.PeriodicProcessDeploymentStatus.PeriodicProcessDeploymentStatus
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.ProblemStateStatus
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessName}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{AdditionalModelConfigs, DeploymentData, DeploymentId}
import pl.touk.nussknacker.engine.util.AdditionalComponentConfigsForRuntimeExtractor
import pl.touk.nussknacker.ui.process.periodic.PeriodicProcessService._
import pl.touk.nussknacker.ui.process.periodic.PeriodicStateStatus._
import pl.touk.nussknacker.ui.process.periodic.model.{
  PeriodicProcess,
  PeriodicProcessDeployment,
  PeriodicProcessDeploymentId,
  PeriodicProcessDeploymentStatus,
  PeriodicProcessScheduleData,
  ScheduleDeploymentData,
  ScheduleId,
  ScheduleName,
  SchedulesState
}
import pl.touk.nussknacker.ui.process.periodic.service.{
  AdditionalDeploymentDataProvider,
  DeployedEvent,
  FailedOnDeployEvent,
  FailedOnRunEvent,
  FinishedEvent,
  PeriodicProcessEvent,
  PeriodicProcessListener,
  ProcessConfigEnricher,
  ScheduledEvent
}
import pl.touk.nussknacker.ui.process.periodic.utils.DeterministicUUIDFromLong

import java.time.chrono.ChronoLocalDateTime
import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant, LocalDateTime}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class PeriodicProcessService(
    delegateDeploymentManager: DeploymentManager,
    engineHandler: PeriodicDeploymentEngineHandler,
    periodicProcessesManager: PeriodicProcessesManager,
    periodicProcessListener: PeriodicProcessListener,
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
    schedulesState <- periodicProcessesManager.getSchedulesState(
      processIdWithName.name,
      after.map(localDateTimeAtSystemDefaultZone)
    )
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
      case Left(error) => Left(s"Failed to parse periodic property: $error")
      case Right(scheduleDates) if scheduleDates.forall(_._2.isEmpty) => Left(s"No future date determined by $schedule")
      case correctSchedules                                           => correctSchedules
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
      inputConfigDuringExecution <- engineHandler.provideInputConfigDuringExecutionJson()
      deploymentWithJarData <- engineHandler.prepareDeploymentWithRuntimeParams(
        processVersion,
      )
      enrichedProcessConfig <- processConfigEnricher.onInitialSchedule(
        ProcessConfigEnricher.InitialScheduleData(inputConfigDuringExecution.serialized)
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
    periodicProcessesManager
      .create(
        deploymentWithJarData,
        inputConfigDuringExecutionJson,
        canonicalProcess,
        scheduleMap,
        processActionId
      )
      .flatMap { process =>
        scheduleDates.collect {
          case (name, Some(date)) =>
            periodicProcessesManager
              .schedule(process.id, name, date, deploymentRetryConfig.deployMaxRetries)
              .flatMap { data =>
                handleEvent(ScheduledEvent(data, firstSchedule = true))
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
      toBeDeployed <- periodicProcessesManager.findToBeDeployed.flatMap { toDeployList =>
        Future.sequence(toDeployList.map(checkIfNotRunning)).map(_.flatten)
      }
      // We retry scenarios that failed on deployment. Failure recovery of running scenarios should be handled by Flink's restart strategy
      toBeRetried <- periodicProcessesManager.findToBeRetried
      // We don't block scheduled deployments by retries
    } yield toBeDeployed.sortBy(d => (d.runAt, d.createdAt)) ++ toBeRetried.sortBy(d => (d.nextRetryAt, d.createdAt))
  }

  // Currently we don't allow simultaneous runs of one scenario - only sequential, so if other schedule kicks in, it'll have to wait
  // TODO: we show allow to deploy scenarios with different scheduleName to be deployed simultaneous
  private def checkIfNotRunning(
      toDeploy: PeriodicProcessDeployment
  ): Future[Option[PeriodicProcessDeployment]] = {
    delegateDeploymentManager
      .getProcessStates(toDeploy.periodicProcess.deploymentData.processName)(DataFreshnessPolicy.Fresh)
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
      schedules <- periodicProcessesManager
        .findActiveSchedulesForProcessesHavingDeploymentWithMatchingStatus(
          Set(PeriodicProcessDeploymentStatus.Deployed, PeriodicProcessDeploymentStatus.FailedOnDeploy),
        )
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
      runtimeStatuses <- delegateDeploymentManager.getProcessStates(processName)(DataFreshnessPolicy.Fresh).map(_.value)
      _ = logger.debug(s"Process '$processName' runtime statuses: ${runtimeStatuses.map(_.toString)}")
      scheduleDeploymentsWithStatus = schedules.schedules.values.toList.flatMap { scheduleData =>
        logger.debug(
          s"Process '$processName' latest deployment ids: ${scheduleData.latestDeployments.map(_.id.toString)}"
        )
        scheduleData.latestDeployments.map { deployment =>
          (deployment, runtimeStatuses.getStatus(deployment.id))
        }
      }
      _ = logger.debug(
        s"Process '$processName' schedule deployments with status: ${scheduleDeploymentsWithStatus.map(_.toString)}"
      )
      needRescheduleDeployments <- Future
        .sequence(scheduleDeploymentsWithStatus.map { case (deploymentData, statusOpt) =>
          synchronizeDeploymentState(deploymentData, statusOpt).map { needReschedule =>
            Option(deploymentData.id).filter(_ => needReschedule)
          }
        })
        .map(_.flatten.toSet)
      followingDeployDeploymentsForSchedules = scheduleDeploymentsWithStatus.collect {
        case (deployment, Some(status)) if SimpleStateStatus.DefaultFollowingDeployStatuses.contains(status.status) =>
          deployment.id
      }.toSet
    } yield (followingDeployDeploymentsForSchedules, needRescheduleDeployments)

  // We assume that this method leaves with data in consistent state
  private def synchronizeDeploymentState(
      deployment: ScheduleDeploymentData,
      processState: Option[StatusDetails]
  ): Future[NeedsReschedule] = {
    implicit class RichFuture[Unit](a: Future[Unit]) {
      def needsReschedule(value: Boolean): Future[NeedsReschedule] = a.map(_ => value)
    }
    processState.map(_.status) match {
      case Some(status)
          if ProblemStateStatus.isProblemStatus(
            status
          ) && deployment.state.status != PeriodicProcessDeploymentStatus.Failed =>
        markFailedAction(deployment, processState).needsReschedule(executionConfig.rescheduleOnFailure)
      case Some(status)
          if EngineStatusesToReschedule.contains(
            status
          ) && deployment.state.status != PeriodicProcessDeploymentStatus.Finished =>
        markFinished(deployment, processState).needsReschedule(value = true)
      case None
          if deployment.state.status == PeriodicProcessDeploymentStatus.Deployed
            && deployment.deployedAt.exists(_.isBefore(LocalDateTime.now().minusMinutes(5))) =>
        // status is None if DeploymentManager isn't aware of a job that was just deployed
        // this can be caused by a race in e.g. FlinkRestManager
        // (because /jobs/overview used in getProcessStates isn't instantly aware of submitted jobs)
        // so freshly deployed deployments aren't considered
        markFinished(deployment, processState).needsReschedule(value = true)
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
            val action = periodicProcessesManager
              .schedule(process.id, deployment.scheduleName, futureDate, deploymentRetryConfig.deployMaxRetries)
              .flatMap { data =>
                handleEvent(ScheduledEvent(data, firstSchedule = false))
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

  private def markFinished(deployment: ScheduleDeploymentData, state: Option[StatusDetails]): Future[Unit] = {
    logger.info(s"Marking ${deployment.display} with status: ${deployment.state.status} as finished")
    for {
      _            <- periodicProcessesManager.markFinished(deployment.id)
      currentState <- periodicProcessesManager.findProcessData(deployment.id)
    } yield handleEvent(FinishedEvent(currentState, state))
  }

  private def handleFailedDeployment(
      deployment: PeriodicProcessDeployment,
      state: Option[StatusDetails]
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
      _ <- periodicProcessesManager.markFailedOnDeployWithStatus(deployment.id, status, retriesLeft, nextRetryAt)
      currentState <- periodicProcessesManager.findProcessData(deployment.id)
    } yield handleEvent(FailedOnDeployEvent(currentState, state))
  }

  private def markFailedAction(
      deployment: ScheduleDeploymentData,
      state: Option[StatusDetails]
  ): Future[Unit] = {
    logger.info(s"Marking ${deployment.display} as failed.")
    for {
      _            <- periodicProcessesManager.markFailed(deployment.id)
      currentState <- periodicProcessesManager.findProcessData(deployment.id)
    } yield handleEvent(FailedOnRunEvent(currentState, state))
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
      _ <- periodicProcessesManager.markInactive(process.id)
      // we want to delete jars only after we successfully mark process as inactive. It's better to leave jar garbage than
      // have process without jar
    } yield () => engineHandler.cleanAfterDeployment(process.deploymentData.runtimeParams)
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
      additionalDeploymentDataProvider.prepareAdditionalData(deployment),
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
      canonicalProcessWithVersionOpt <- periodicProcessesManager.fetchCanonicalProcessWithVersion(
        processName,
        versionId
      )
      canonicalProcessWithVersion = canonicalProcessWithVersionOpt.getOrElse {
        throw new PeriodicProcessException(
          s"Could not fetch CanonicalProcess with ProcessVersion for processName=$processName, versionId=$versionId"
        )
      }
      inputConfigDuringExecutionJsonOpt <- periodicProcessesManager.fetchInputConfigDuringExecutionJson(
        processName,
        versionId,
      )
      inputConfigDuringExecutionJson = inputConfigDuringExecutionJsonOpt.getOrElse {
        throw new PeriodicProcessException(
          s"Could not fetch inputConfigDuringExecutionJson for processName=${processName}, versionId=${versionId}"
        )
      }
      enrichedProcessConfig <- processConfigEnricher.onDeploy(
        ProcessConfigEnricher.DeployData(inputConfigDuringExecutionJson, deployment)
      )
      externalDeploymentId <- engineHandler.deployWithRuntimeParams(
        deploymentWithJarData,
        enrichedProcessConfig.inputConfigDuringExecutionJson,
        deploymentData,
        canonicalProcessWithVersion._1,
        canonicalProcessWithVersion._2,
      )
    } yield externalDeploymentId
    deploymentAction
      .flatMap { externalDeploymentId =>
        logger.info("Scenario has been deployed {} for deployment id {}", deploymentWithJarData, id)
        // TODO: add externalDeploymentId??
        periodicProcessesManager
          .markDeployed(id)
          .flatMap(_ => periodicProcessesManager.findProcessData(id))
          .flatMap(afterChange => handleEvent(DeployedEvent(afterChange, externalDeploymentId)))
      }
      // We can recover since deployment actor watches only future completion.
      .recoverWith { case exception =>
        logger.error(s"Scenario deployment ${deployment.display} failed", exception)
        handleFailedDeployment(deployment, None)
      }
  }

  // TODO: allow access to DB in listener?
  private def handleEvent(event: PeriodicProcessEvent): Future[Unit] = {
    Future.successful {
      try {
        periodicProcessListener.onPeriodicProcessEvent.applyOrElse(event, (_: PeriodicProcessEvent) => ())
      } catch {
        case NonFatal(e) => throw new PeriodicProcessException("Failed to invoke listener", e)
      }
    }
  }

  private def now(): LocalDateTime = LocalDateTime.now(clock)

  def getStatusDetails(
      name: ProcessName
  )(implicit freshnessPolicy: DataFreshnessPolicy): Future[WithDataFreshnessStatus[StatusDetails]] = {
    delegateDeploymentManager.getProcessStates(name).flatMap { statusesWithFreshness =>
      logger.debug(s"Statuses for $name: $statusesWithFreshness")
      mergeStatusWithDeployments(name, statusesWithFreshness.value).map { statusDetails =>
        statusesWithFreshness.copy(value = statusDetails)
      }
    }
  }

  def stateQueryForAllScenariosSupport: StateQueryForAllScenariosSupport =
    delegateDeploymentManager.stateQueryForAllScenariosSupport match {
      case supported: StateQueryForAllScenariosSupported =>
        new StateQueryForAllScenariosSupported {

          override def getAllProcessesStates()(
              implicit freshnessPolicy: DataFreshnessPolicy
          ): Future[WithDataFreshnessStatus[Map[ProcessName, List[StatusDetails]]]] = {
            for {
              allStatusDetailsInDelegate <- supported.getAllProcessesStates()
              allStatusDetailsInPeriodic <- mergeStatusWithDeployments(allStatusDetailsInDelegate.value)
              result = allStatusDetailsInPeriodic.map { case (name, status) => (name, List(status)) }
            } yield allStatusDetailsInDelegate.map(_ => result)
          }

        }

      case NoStateQueryForAllScenariosSupport =>
        NoStateQueryForAllScenariosSupport
    }

  private def mergeStatusWithDeployments(
      name: ProcessName,
      runtimeStatuses: List[StatusDetails]
  ): Future[StatusDetails] = {
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
      status.mergedStatusDetails.copy(status = status)
    }
  }

  private def mergeStatusWithDeployments(
      runtimeStatuses: Map[ProcessName, List[StatusDetails]]
  ): Future[Map[ProcessName, StatusDetails]] = {
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
        val mergedStatus = status.mergedStatusDetails.copy(status = status)
        (processName, mergedStatus)
      }.toMap
    }
  }

  def getLatestDeploymentsForActiveSchedules(
      processName: ProcessName,
      deploymentsPerScheduleMaxCount: Int = 1
  ): Future[SchedulesState] =
    periodicProcessesManager.getLatestDeploymentsForActiveSchedules(
      processName,
      deploymentsPerScheduleMaxCount,
    )

  def getLatestDeploymentsForActiveSchedules(
      deploymentsPerScheduleMaxCount: Int
  ): Future[Map[ProcessName, SchedulesState]] =
    periodicProcessesManager.getLatestDeploymentsForActiveSchedules(deploymentsPerScheduleMaxCount)

  def getLatestDeploymentsForLatestInactiveSchedules(
      processName: ProcessName,
      inactiveProcessesMaxCount: Int,
      deploymentsPerScheduleMaxCount: Int
  ): Future[SchedulesState] =
    periodicProcessesManager.getLatestDeploymentsForLatestInactiveSchedules(
      processName,
      inactiveProcessesMaxCount,
      deploymentsPerScheduleMaxCount,
    )

  def getLatestDeploymentsForLatestInactiveSchedules(
      inactiveProcessesMaxCount: Int,
      deploymentsPerScheduleMaxCount: Int
  ): Future[Map[ProcessName, SchedulesState]] =
    periodicProcessesManager.getLatestDeploymentsForLatestInactiveSchedules(
      inactiveProcessesMaxCount,
      deploymentsPerScheduleMaxCount
    )

  implicit class RuntimeStatusesExt(runtimeStatuses: List[StatusDetails]) {

    private val runtimeStatusesMap = runtimeStatuses.flatMap(status => status.deploymentId.map(_ -> status)).toMap
    def getStatus(deploymentId: PeriodicProcessDeploymentId): Option[StatusDetails] =
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

  private implicit val localDateOrdering: Ordering[LocalDateTime] = Ordering.by(identity[ChronoLocalDateTime[_]])

  // TODO: some configuration?
  private val MaxDeploymentsStatus = 5

  private val DeploymentStatusesToReschedule =
    Set(PeriodicProcessDeploymentStatus.Deployed, PeriodicProcessDeploymentStatus.Failed)

  // Should we reschedule canceled status? It can be useful in case when job hang, we cancel it and want to be rescheduled
  private val EngineStatusesToReschedule = Set(SimpleStateStatus.Finished, SimpleStateStatus.Canceled)

  // This class represents status of all schedules for given scenario. It is designed to contain simple representation of statuses
  // for each historical and active deployments. mergedStatusDetails and methods below are for purpose of presentation
  // of single, merged status similar to this available for streaming job. This merged status should be a straightforward derivative
  // of these deployments statuses so it will be easy to figure out it by user.
  case class PeriodicProcessStatus(
      activeDeploymentsStatuses: List[PeriodicDeploymentStatus],
      inactiveDeploymentsStatuses: List[PeriodicDeploymentStatus]
  ) extends StateStatus
      with LazyLogging {

    def limitedAndSortedDeployments: List[PeriodicDeploymentStatus] =
      (activeDeploymentsStatuses ++ inactiveDeploymentsStatuses.take(
        MaxDeploymentsStatus - activeDeploymentsStatuses.size
      )).sorted(PeriodicDeploymentStatus.ordering.reverse)

    // We present merged name to be possible to filter scenario by status
    override def name: StatusName = mergedStatusDetails.status.name

    // Currently we don't present deployments - theirs statuses are available only in tooltip - because of that we have to pick
    // one "merged" status that will be presented to users
    def mergedStatusDetails: StatusDetails = {
      pickMostImportantActiveDeployment
        .map { deploymentStatus =>
          def createStatusDetails(status: StateStatus) = StatusDetails(
            status = status,
            deploymentId = Some(DeploymentId(deploymentStatus.deploymentId.toString)),
          )
          if (deploymentStatus.isWaitingForReschedule) {
            deploymentStatus.runtimeStatusOpt
              .map(_.copy(status = WaitingForScheduleStatus))
              .getOrElse(createStatusDetails(WaitingForScheduleStatus))
          } else if (deploymentStatus.status == PeriodicProcessDeploymentStatus.Scheduled) {
            createStatusDetails(ScheduledStatus(deploymentStatus.runAt))
          } else if (Set(PeriodicProcessDeploymentStatus.Failed, PeriodicProcessDeploymentStatus.FailedOnDeploy)
              .contains(deploymentStatus.status)) {
            createStatusDetails(ProblemStateStatus.Failed)
          } else if (deploymentStatus.status == PeriodicProcessDeploymentStatus.RetryingDeploy) {
            createStatusDetails(SimpleStateStatus.DuringDeploy)
          } else {
            deploymentStatus.runtimeStatusOpt.getOrElse {
              createStatusDetails(WaitingForScheduleStatus)
            }
          }
        }
        .getOrElse {
          if (inactiveDeploymentsStatuses.isEmpty) {
            StatusDetails(SimpleStateStatus.NotDeployed, None)
          } else {
            val latestInactiveProcessId =
              inactiveDeploymentsStatuses.maxBy(_.scheduleId.processId.value).scheduleId.processId
            val latestDeploymentsForEachScheduleOfLatestProcessId = latestDeploymentForEachSchedule(
              inactiveDeploymentsStatuses.filter(_.scheduleId.processId == latestInactiveProcessId)
            )

            if (latestDeploymentsForEachScheduleOfLatestProcessId.forall(
                _.status == PeriodicProcessDeploymentStatus.Finished
              )) {
              StatusDetails(SimpleStateStatus.Finished, None)
            } else {
              StatusDetails(SimpleStateStatus.Canceled, None)
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
    def pickMostImportantActiveDeployment: Option[PeriodicDeploymentStatus] = {
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
      runtimeStatusOpt: Option[StatusDetails]
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
