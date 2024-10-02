package pl.touk.nussknacker.engine.management.periodic

import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.component.NodesDeploymentData
import pl.touk.nussknacker.engine.api.deployment.StateStatus.StatusName
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.ProblemStateStatus
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessName}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{DeploymentData, DeploymentId}
import pl.touk.nussknacker.engine.management.periodic.PeriodicProcessService.{
  DeploymentStatus,
  EngineStatusesToReschedule,
  MaxDeploymentsStatus,
  PeriodicProcessStatus
}
import pl.touk.nussknacker.engine.management.periodic.PeriodicStateStatus.{ScheduledStatus, WaitingForScheduleStatus}
import pl.touk.nussknacker.engine.management.periodic.db.PeriodicProcessesRepository
import pl.touk.nussknacker.engine.management.periodic.model.PeriodicProcessDeploymentStatus.PeriodicProcessDeploymentStatus
import pl.touk.nussknacker.engine.management.periodic.model._
import pl.touk.nussknacker.engine.management.periodic.service._

import java.time.chrono.ChronoLocalDateTime
import java.time.temporal.ChronoUnit
import java.time.{Clock, LocalDateTime, ZoneOffset}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class PeriodicProcessService(
    delegateDeploymentManager: DeploymentManager,
    jarManager: JarManager,
    scheduledProcessesRepository: PeriodicProcessesRepository,
    periodicProcessListener: PeriodicProcessListener,
    additionalDeploymentDataProvider: AdditionalDeploymentDataProvider,
    deploymentRetryConfig: DeploymentRetryConfig,
    executionConfig: PeriodicExecutionConfig,
    processConfigEnricher: ProcessConfigEnricher,
    clock: Clock,
    actionService: ProcessingTypeActionService
)(implicit ec: ExecutionContext)
    extends LazyLogging {

  import cats.syntax.all._
  import scheduledProcessesRepository._
  private type RepositoryAction[T] = scheduledProcessesRepository.Action[T]
  private type Callback            = () => Future[Unit]
  private type NeedsReschedule     = Boolean

  private implicit class WithCallbacksSeq(result: RepositoryAction[List[Callback]]) {
    def runWithCallbacks: Future[Unit] =
      result.run.flatMap(callbacks => Future.sequence(callbacks.map(_()))).map(_ => ())
  }

  private val emptyCallback: Callback = () => Future.successful(())

  private implicit val localDateOrdering: Ordering[LocalDateTime] = Ordering.by(identity[ChronoLocalDateTime[_]])

  def getScenarioActivitiesSpecificToPeriodicProcess(
      processIdWithName: ProcessIdWithName
  ): Future[List[ScenarioActivity]] =
    scheduledProcessesRepository
      .getSchedulesState(processIdWithName.name)
      .run
      .map(_.groupedByPeriodicProcess)
      .map(_.flatMap(_.deployments))
      .map(_.map { deployment =>
        ScenarioActivity.PerformedScheduledExecution(
          scenarioId = ScenarioId(processIdWithName.id.value),
          scenarioActivityId = ScenarioActivityId.random,
          user = ScenarioUser(None, UserName("Nussknacker"), None, None),
          date = deployment.createdAt.toInstant(ZoneOffset.UTC),
          scenarioVersionId = Some(ScenarioVersionId(deployment.periodicProcess.processVersion.versionId.value)),
          dateFinished = deployment.state.completedAt.map(_.toInstant(ZoneOffset.UTC)),
          scheduleName = deployment.scheduleName.display,
          status = deployment.state.status.toString,
          nextRetryAt = deployment.nextRetryAt.map(_.toInstant(ZoneOffset.UTC)),
          retriesLeft = deployment.retriesLeft,
        )
      }.toList.sortBy(_.date))

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
      processActionId: ProcessActionId
  ): Future[Unit] = {
    logger.info("Scheduling periodic scenario: {} on {}", processVersion, scheduleDates)
    for {
      deploymentWithJarData <- jarManager.prepareDeploymentWithJar(processVersion, canonicalProcess)
      enrichedProcessConfig <- processConfigEnricher.onInitialSchedule(
        ProcessConfigEnricher.InitialScheduleData(
          deploymentWithJarData.process,
          deploymentWithJarData.inputConfigDuringExecutionJson
        )
      )
      enrichedDeploymentWithJarData = deploymentWithJarData.copy(inputConfigDuringExecutionJson =
        enrichedProcessConfig.inputConfigDuringExecutionJson
      )
      _ <- initialSchedule(scheduleProperty, scheduleDates, enrichedDeploymentWithJarData, processActionId)
    } yield ()
  }

  private def initialSchedule(
      scheduleMap: ScheduleProperty,
      scheduleDates: List[(ScheduleName, Option[LocalDateTime])],
      deploymentWithJarData: DeploymentWithJarData[CanonicalProcess],
      processActionId: ProcessActionId
  ): Future[Unit] = {
    scheduledProcessesRepository
      .create(deploymentWithJarData, scheduleMap, processActionId)
      .flatMap { process =>
        scheduleDates.collect {
          case (name, Some(date)) =>
            scheduledProcessesRepository
              .schedule(process.id, name, date, deploymentRetryConfig.deployMaxRetries)
              .flatMap { data =>
                handleEvent(ScheduledEvent(data, firstSchedule = true))
              }
          case (name, None) =>
            logger.warn(s"Schedule $name does not have date to schedule")
            monad.pure(())
        }.sequence
      }
      .run
      .map(_ => ())
  }

  def findToBeDeployed: Future[Seq[PeriodicProcessDeployment[CanonicalProcess]]] = {
    for {
      toBeDeployed <- scheduledProcessesRepository.findToBeDeployed.run.flatMap { toDeployList =>
        Future.sequence(toDeployList.map(checkIfNotRunning)).map(_.flatten)
      }
      // We retry scenarios that failed on deployment. Failure recovery of running scenarios should be handled by Flink's restart strategy
      toBeRetried <- scheduledProcessesRepository.findToBeRetried.run
      // We don't block scheduled deployments by retries
    } yield toBeDeployed.sortBy(d => (d.runAt, d.createdAt)) ++ toBeRetried.sortBy(d => (d.nextRetryAt, d.createdAt))
  }

  // Currently we don't allow simultaneous runs of one scenario - only sequential, so if other schedule kicks in, it'll have to wait
  // TODO: we show allow to deploy scenarios with different scheduleName to be deployed simultaneous
  private def checkIfNotRunning(
      toDeploy: PeriodicProcessDeployment[CanonicalProcess]
  ): Future[Option[PeriodicProcessDeployment[CanonicalProcess]]] = {
    delegateDeploymentManager
      .getProcessStates(toDeploy.periodicProcess.processVersion.processName)(DataFreshnessPolicy.Fresh)
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
                  if processScheduleData.existsDeployment(d => needRescheduleDeploymentIds.contains(d.id)) =>
                reschedule(processScheduleData, needRescheduleDeploymentIds)
            }
            .sequence
            .runWithCallbacks
        }

    for {
      schedules <- scheduledProcessesRepository
        .findActiveSchedulesForProcessesHavingDeploymentWithMatchingStatus(
          Set(PeriodicProcessDeploymentStatus.Deployed, PeriodicProcessDeploymentStatus.FailedOnDeploy)
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
      runtimeStatuses <- delegateDeploymentManager.getProcessStates(processName)(DataFreshnessPolicy.Fresh).map(_.value)
      _ = logger.debug(s"Process '$processName' runtime statuses: ${runtimeStatuses.map(_.toString)}")
      scheduleDeploymentsWithStatus = schedules.schedules.values.toList.flatMap(_.latestDeployments.map { deployment =>
        (deployment, runtimeStatuses.getStatus(deployment.id))
      })
      needRescheduleDeployments <- Future
        .sequence(scheduleDeploymentsWithStatus.map { case (deploymentData, statusOpt) =>
          synchronizeDeploymentState(deploymentData, statusOpt).run.map { needReschedule =>
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
  ): RepositoryAction[NeedsReschedule] = {
    implicit class RichRepositoryAction[Unit](a: RepositoryAction[Unit]) {
      def needsReschedule(value: Boolean): RepositoryAction[NeedsReschedule] = a.map(_ => value)
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
        scheduledProcessesRepository.monad.pure(()).needsReschedule(value = false)
    }
  }

  private def reschedule(
      processScheduleData: PeriodicProcessScheduleData,
      needRescheduleDeploymentIds: Set[PeriodicProcessDeploymentId]
  ): RepositoryAction[Callback] = {
    import processScheduleData._
    val scheduleActions = deployments.map { deployment =>
      if (needRescheduleDeploymentIds.contains(deployment.id))
        deployment.nextRunAt(clock) match {
          case Right(Some(futureDate)) =>
            logger.info(s"Rescheduling ${deployment.display} to $futureDate")
            val action = scheduledProcessesRepository
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
          .map(_ => scheduledProcessesRepository.monad.pure(()))

    }

    if (scheduleActions.forall(_.isEmpty)) {
      logger.info(s"No scheduled deployments for periodic process: ${process.id.value}. Deactivating")
      deactivateAction(process).flatMap { _ =>
        markProcessActionExecutionFinished(processScheduleData.process.processActionId)
      }

    } else
      scheduleActions.flatten.sequence.as(emptyCallback)
  }

  private def markFinished(deployment: ScheduleDeploymentData, state: Option[StatusDetails]): RepositoryAction[Unit] = {
    logger.info(s"Marking ${deployment.display} with status: ${deployment.state.status} as finished")
    for {
      _            <- scheduledProcessesRepository.markFinished(deployment.id)
      currentState <- scheduledProcessesRepository.findProcessData(deployment.id)
    } yield handleEvent(FinishedEvent(currentState, state))
  }

  private def handleFailedDeployment(
      deployment: PeriodicProcessDeployment[_],
      state: Option[StatusDetails]
  ): RepositoryAction[Unit] = {
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
      _ <- scheduledProcessesRepository.markFailedOnDeployWithStatus(deployment.id, status, retriesLeft, nextRetryAt)
      currentState <- scheduledProcessesRepository.findProcessData(deployment.id)
    } yield handleEvent(FailedOnDeployEvent(currentState, state))
  }

  private def markFailedAction(
      deployment: ScheduleDeploymentData,
      state: Option[StatusDetails]
  ): RepositoryAction[Unit] = {
    logger.info(s"Marking ${deployment.display} as failed.")
    for {
      _            <- scheduledProcessesRepository.markFailed(deployment.id)
      currentState <- scheduledProcessesRepository.findProcessData(deployment.id)
    } yield handleEvent(FailedOnRunEvent(currentState, state))
  }

  def deactivate(processName: ProcessName): Future[Iterable[DeploymentId]] =
    for {
      activeSchedules                     <- getLatestDeploymentsForActiveSchedules(processName)
      (runningDeploymentsForSchedules, _) <- synchronizeDeploymentsStates(processName, activeSchedules)
      _ <- activeSchedules.groupedByPeriodicProcess.map(p => deactivateAction(p.process)).sequence.runWithCallbacks
    } yield runningDeploymentsForSchedules.map(deployment => DeploymentId(deployment.toString))

  private def deactivateAction(process: PeriodicProcess[_]): RepositoryAction[Callback] = {
    logger.info(s"Deactivate periodic process id: ${process.id.value}")
    for {
      _ <- scheduledProcessesRepository.markInactive(process.id)
      // we want to delete jars only after we successfully mark process as inactive. It's better to leave jar garbage than
      // have process without jar
    } yield () => jarManager.deleteJar(process.deploymentData.jarFileName)
  }

  private def markProcessActionExecutionFinished(
      processActionIdOption: Option[ProcessActionId]
  ): RepositoryAction[Callback] =
    scheduledProcessesRepository.monad.pure { () =>
      processActionIdOption
        .map(actionService.markActionExecutionFinished)
        .sequence
        .map(_ => ())
    }

  def deploy(deployment: PeriodicProcessDeployment[CanonicalProcess]): Future[Unit] = {
    // TODO: set status before deployment?
    val id = deployment.id
    val deploymentData = DeploymentData(
      DeploymentId(id.toString),
      DeploymentData.systemUser,
      additionalDeploymentDataProvider.prepareAdditionalData(deployment),
      // TODO: in the future we could allow users to specify nodes data during schedule requesting
      NodesDeploymentData.empty
    )
    val deploymentWithJarData = deployment.periodicProcess.deploymentData
    val deploymentAction = for {
      _ <- Future.successful(
        logger.info("Deploying scenario {} for deployment id {}", deploymentWithJarData.processVersion, id)
      )
      enrichedProcessConfig <- processConfigEnricher.onDeploy(
        ProcessConfigEnricher.DeployData(
          deploymentWithJarData.process,
          deploymentWithJarData.inputConfigDuringExecutionJson,
          deployment
        )
      )
      enrichedDeploymentWithJarData = deploymentWithJarData.copy(inputConfigDuringExecutionJson =
        enrichedProcessConfig.inputConfigDuringExecutionJson
      )
      externalDeploymentId <- jarManager.deployWithJar(enrichedDeploymentWithJarData, deploymentData)
    } yield externalDeploymentId
    deploymentAction
      .flatMap { externalDeploymentId =>
        logger.info("Scenario has been deployed {} for deployment id {}", deploymentWithJarData.processVersion, id)
        // TODO: add externalDeploymentId??
        scheduledProcessesRepository
          .markDeployed(id)
          .flatMap(_ => scheduledProcessesRepository.findProcessData(id))
          .flatMap(afterChange => handleEvent(DeployedEvent(afterChange, externalDeploymentId)))
          .run
      }
      // We can recover since deployment actor watches only future completion.
      .recoverWith { case exception =>
        logger.error(s"Scenario deployment ${deployment.display} failed", exception)
        handleFailedDeployment(deployment, None).run
      }
  }

  // TODO: allow access to DB in listener?
  private def handleEvent(event: PeriodicProcessEvent): scheduledProcessesRepository.Action[Unit] = {
    scheduledProcessesRepository.monad.pure {
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

  private def mergeStatusWithDeployments(
      name: ProcessName,
      runtimeStatuses: List[StatusDetails]
  ): Future[StatusDetails] = {
    def toDeploymentStatuses(schedulesState: SchedulesState) = schedulesState.schedules.toList
      .flatMap { case (scheduleId, scheduleData) =>
        scheduleData.latestDeployments.map { deployment =>
          DeploymentStatus(
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
      .sorted(DeploymentStatus.ordering.reverse)

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

  def getLatestDeploymentsForActiveSchedules(
      processName: ProcessName,
      deploymentsPerScheduleMaxCount: Int = 1
  ): Future[SchedulesState] =
    scheduledProcessesRepository.getLatestDeploymentsForActiveSchedules(processName, deploymentsPerScheduleMaxCount).run

  def getLatestDeploymentsForLatestInactiveSchedules(
      processName: ProcessName,
      inactiveProcessesMaxCount: Int,
      deploymentsPerScheduleMaxCount: Int
  ): Future[SchedulesState] =
    scheduledProcessesRepository
      .getLatestDeploymentsForLatestInactiveSchedules(
        processName,
        inactiveProcessesMaxCount,
        deploymentsPerScheduleMaxCount
      )
      .run

  implicit class RuntimeStatusesExt(runtimeStatuses: List[StatusDetails]) {

    private val runtimeStatusesMap = runtimeStatuses.flatMap(status => status.deploymentId.map(_ -> status)).toMap
    def getStatus(deploymentId: PeriodicProcessDeploymentId): Option[StatusDetails] =
      runtimeStatusesMap.get(DeploymentId(deploymentId.toString))

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
      activeDeploymentsStatuses: List[DeploymentStatus],
      inactiveDeploymentsStatuses: List[DeploymentStatus]
  ) extends StateStatus
      with LazyLogging {

    def limitedAndSortedDeployments: List[DeploymentStatus] =
      (activeDeploymentsStatuses ++ inactiveDeploymentsStatuses.take(
        MaxDeploymentsStatus - activeDeploymentsStatuses.size
      )).sorted(DeploymentStatus.ordering.reverse)

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
    def pickMostImportantActiveDeployment: Option[DeploymentStatus] = {
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

    private def latestDeploymentForEachSchedule(deploymentsStatuses: List[DeploymentStatus]) = {
      deploymentsStatuses
        .groupBy(_.scheduleId)
        .values
        .toList
        .map(_.min(DeploymentStatus.ordering.reverse))
    }

  }

  case class DeploymentStatus( // Probably it is too much technical to present to users, but the only other alternative
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

  object DeploymentStatus {

    implicit val ordering: Ordering[DeploymentStatus] = (self: DeploymentStatus, that: DeploymentStatus) => {
      self.runAt.compareTo(that.runAt) match {
        case 0 => self.createdAt.compareTo(that.createdAt)
        case a => a
      }
    }

  }

}
