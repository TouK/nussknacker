package pl.touk.nussknacker.engine.management.periodic

import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.inconsistency.InconsistentStateDetector
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.ProblemStateStatus
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{DeploymentData, DeploymentId, ExternalDeploymentId}
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

class PeriodicProcessService(delegateDeploymentManager: DeploymentManager,
                             jarManager: JarManager,
                             scheduledProcessesRepository: PeriodicProcessesRepository,
                             periodicProcessListener: PeriodicProcessListener,
                             additionalDeploymentDataProvider: AdditionalDeploymentDataProvider,
                             deploymentRetryConfig: DeploymentRetryConfig,
                             executionConfig: PeriodicExecutionConfig,
                             processConfigEnricher: ProcessConfigEnricher,
                             clock: Clock)
                            (implicit ec: ExecutionContext) extends LazyLogging {

  import cats.syntax.all._
  import scheduledProcessesRepository._
  private type RepositoryAction[T] = scheduledProcessesRepository.Action[T]
  private type Callback = () => Future[Unit]
  private type NeedsReschedule = Boolean

  private implicit class WithCallbacksSeq(result: RepositoryAction[List[Callback]]) {
    def runWithCallbacks: Future[Unit] = result.run.flatMap(callbacks => Future.sequence(callbacks.map(_()))).map(_ => ())
  }

  private implicit class EmptyCallback(result: RepositoryAction[Unit]) {
    def emptyCallback: RepositoryAction[Callback] = result.map(_ => () => Future.successful(()))
  }

  private implicit val localDateOrdering: Ordering[LocalDateTime] = Ordering.by(identity[ChronoLocalDateTime[_]])

  def schedule(schedule: ScheduleProperty,
               processVersion: ProcessVersion,
               canonicalProcess: CanonicalProcess,
               beforeSchedule: => Future[Unit] = Future.unit
              ): Future[Unit] = {
    prepareInitialScheduleDates(schedule) match {
      case Right(scheduleDates) =>
        beforeSchedule.flatMap(_ => scheduleWithInitialDates(schedule, processVersion, canonicalProcess, scheduleDates))
      case Left(error) =>
        Future.failed(error)
    }
  }

  def prepareInitialScheduleDates(schedule: ScheduleProperty): Either[PeriodicProcessException, List[(ScheduleName, Option[LocalDateTime])]] = {
    val schedules = schedule match {
      case MultipleScheduleProperty(schedules) => schedules.map { case (k, pp) =>
        pp.nextRunAt(clock).map(v => ScheduleName(Some(k)) -> v)
      }.toList.sequence
      case e: SingleScheduleProperty => e.nextRunAt(clock).map(t => List((ScheduleName(None), t)))
    }
    (schedules match {
      case Left(error) => Left(s"Failed to parse periodic property: $error")
      case Right(scheduleDates) if scheduleDates.forall(_._2.isEmpty) => Left(s"No future date determined by $schedule")
      case correctSchedules => correctSchedules
    }).left.map(new PeriodicProcessException(_))
  }

  private def scheduleWithInitialDates(scheduleProperty: ScheduleProperty, processVersion: ProcessVersion, canonicalProcess: CanonicalProcess, scheduleDates: List[(ScheduleName, Option[LocalDateTime])]): Future[Unit] = {
    logger.info("Scheduling periodic scenario: {} on {}", processVersion, scheduleDates)
    for {
      deploymentWithJarData <- jarManager.prepareDeploymentWithJar(processVersion, canonicalProcess)
      enrichedProcessConfig <- processConfigEnricher.onInitialSchedule(ProcessConfigEnricher.InitialScheduleData(deploymentWithJarData.canonicalProcess, deploymentWithJarData.inputConfigDuringExecutionJson))
      enrichedDeploymentWithJarData = deploymentWithJarData.copy(inputConfigDuringExecutionJson = enrichedProcessConfig.inputConfigDuringExecutionJson)
      _ <- initialSchedule(scheduleProperty, scheduleDates, enrichedDeploymentWithJarData)
    } yield ()
  }

  private def initialSchedule(scheduleMap: ScheduleProperty,
                              scheduleDates: List[(ScheduleName, Option[LocalDateTime])],
                              deploymentWithJarData: DeploymentWithJarData): Future[Unit] = {
    scheduledProcessesRepository.create(deploymentWithJarData, scheduleMap).flatMap { process =>
      scheduleDates.collect {
        case (name, Some(date)) =>
          scheduledProcessesRepository.schedule(process.id, name, date, deploymentRetryConfig.deployMaxRetries).flatMap { data =>
            handleEvent(ScheduledEvent(data, firstSchedule = true))
          }
        case (name, None) =>
          logger.warn(s"Schedule $name does not have date to schedule")
          monad.pure(())
      }.sequence
    }.run.map(_ => ())
  }

  def findToBeDeployed: Future[Seq[PeriodicProcessDeployment]] = {
    for {
      toBeDeployed <- scheduledProcessesRepository.findToBeDeployed.run.flatMap { toDeployList =>
        Future.sequence(toDeployList.map(checkIfNotRunning)).map(_.flatten)
      }
      // We retry scenarios that failed on deployment. Failure recovery of running scenarios should be handled by Flink's restart strategy
      toBeRetried <- scheduledProcessesRepository.findToBeRetried.run
      // We don't block scheduled deployments by retries
    } yield toBeDeployed.sortBy(_.runAt) ++ toBeRetried.sortBy(_.nextRetryAt)
  }

  // Currently we don't allow simultaneous runs of one scenario - only sequential, so if other schedule kicks in, it'll have to wait
  // TODO: we show allow to deploy scenarios with different scheduleName to be deployed simultaneous
  private def checkIfNotRunning(toDeploy: PeriodicProcessDeployment): Future[Option[PeriodicProcessDeployment]] = {
    delegateDeploymentManager.getProcessStates(toDeploy.periodicProcess.processVersion.processName)(DataFreshnessPolicy.Fresh)
      .map(_.value.map(_.status).find(IsFollowingDeployStatusDeterminer.isFollowingDeployStatus).map { _ =>
        logger.debug(s"Deferring run of ${toDeploy.display} as scenario is currently running")
        None
      }.getOrElse(Some(toDeploy)))
  }

  def handleFinished: Future[Unit] = {
    def handleSingleProcess(processName: ProcessName, schedules: SchedulesState): Future[Unit] = {
      synchronizeDeploymentsStates(processName, schedules).flatMap {
        case (_, needRescheduleDeploymentIds) =>
          schedules.groupedByPeriodicProcess.map { processScheduleData =>
            if (processScheduleData.existsDeployment(d => needRescheduleDeploymentIds.contains(d.id))) {
              reschedule(processScheduleData, needRescheduleDeploymentIds)
            } else {
              scheduledProcessesRepository.monad.pure(()).emptyCallback
            }
          }.sequence.runWithCallbacks
      }
    }

    for {
      schedules <- scheduledProcessesRepository.findActiveSchedulesForProcessesHavingDeploymentWithMatchingStatus(Set(PeriodicProcessDeploymentStatus.Deployed, PeriodicProcessDeploymentStatus.FailedOnDeploy)).run

      //we handle each job separately, if we fail at some point, we will continue on next handleFinished run
      _ <- Future.sequence(schedules.groupByProcessName.toList.map(handleSingleProcess _ tupled))
    } yield ()
  }

  // Returns tuple of lists:
  // - matching, active deployment ids on DM side
  // - deployment ids that need to be reschedules
  private def synchronizeDeploymentsStates(processName: ProcessName, schedules: SchedulesState): Future[(Set[PeriodicProcessDeploymentId], Set[PeriodicProcessDeploymentId])] =
    for {
      statusesByDeploymentId <- getDeploymentStatuses(processName)
      scheduleDeploymentsWithStatus = schedules.schedules.values.toList.flatMap(_.latestDeployments.map { deployment =>
        val deploymentId = DeploymentId(deployment.id.toString)
        val statusOpt = statusesByDeploymentId.get(deploymentId)
        (deployment, statusOpt)
      })
      needRescheduleDeployments <- Future.sequence(scheduleDeploymentsWithStatus.map {
        case (deploymentData, statusOpt) =>
          synchronizeDeploymentState(deploymentData, statusOpt).run.map { needReschedule =>
            Option(deploymentData.id).filter(_ => needReschedule)
          }
      }).map(_.flatten.toSet)
      followingDeployDeploymentsForSchedules = scheduleDeploymentsWithStatus.collect {
        case (deployment, Some(status)) if SimpleStateStatus.DefaultFollowingDeployStatuses.contains(status.status) => deployment.id
      }.toSet
    } yield (followingDeployDeploymentsForSchedules, needRescheduleDeployments)

  private def getDeploymentStatuses(processName: ProcessName) =
    delegateDeploymentManager
      .getProcessStates(processName)(DataFreshnessPolicy.Fresh)
      .map(withFreshness => toDeploymentStatuses(withFreshness.value))


  //We assume that this method leaves with data in consistent state
  private def synchronizeDeploymentState(deployment: ScheduleDeploymentData, processState: Option[StatusDetails]): RepositoryAction[NeedsReschedule] = {
    implicit class RichRepositoryAction[Unit](a: RepositoryAction[Unit]){
      def needsReschedule(value: Boolean): RepositoryAction[NeedsReschedule] = a.map(_ => value)
    }
    processState.map(_.status) match {
      case Some(status) if ProblemStateStatus.isProblemStatus(status) && deployment.state.status != PeriodicProcessDeploymentStatus.Failed =>
        markFailedAction(deployment, processState).needsReschedule(executionConfig.rescheduleOnFailure)
      case Some(status) if status == SimpleStateStatus.Finished && deployment.state.status != PeriodicProcessDeploymentStatus.Finished =>
        markFinished(deployment, processState).needsReschedule(value = true)
      case None if deployment.state.status == PeriodicProcessDeploymentStatus.Deployed =>
        markFinished(deployment, processState).needsReschedule(value = true)
      case _ =>
        scheduledProcessesRepository.monad.pure(()).needsReschedule(value = false)
    }
  }

  private def reschedule(processScheduleData: PeriodicProcessScheduleData, needRescheduleDeploymentIds: Set[PeriodicProcessDeploymentId]): RepositoryAction[Callback] = {
    import processScheduleData._
    val scheduleActions = deployments.map { deployment =>
      if (needRescheduleDeploymentIds.contains(deployment.id)) {
        process.nextRunAt(clock, deployment.scheduleName) match {
          case Right(Some(futureDate)) =>
            logger.info(s"Rescheduling ${deployment.display} to $futureDate")
            val action = scheduledProcessesRepository.schedule(process.id, deployment.scheduleName, futureDate, deploymentRetryConfig.deployMaxRetries).flatMap { data =>
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
      } else {
        Option(deployment)
          .filter(_.state.status == PeriodicProcessDeploymentStatus.Scheduled)
          .map(_ => scheduledProcessesRepository.monad.pure(()))
      }
    }
    if (!scheduleActions.exists(_.isDefined)) {
      logger.info(s"No scheduled deployments for periodic process: ${process.id.value}. Deactivating")
      deactivateAction(process)
    } else {
      scheduleActions.flatten.sequence.map(_ => ()).emptyCallback
    }
  }

  private def markFinished(deployment: ScheduleDeploymentData, state: Option[StatusDetails]): RepositoryAction[Unit] = {
    logger.info(s"Marking ${deployment.display} with status: ${deployment.state.status} as finished")
    for {
      _ <- scheduledProcessesRepository.markFinished(deployment.id)
      currentState <- scheduledProcessesRepository.findProcessData(deployment.id)
    } yield handleEvent(FinishedEvent(currentState, state))
  }

  private def handleFailedDeployment(deployment: PeriodicProcessDeployment, state: Option[StatusDetails]): RepositoryAction[Unit] = {
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

    logger.info(s"Marking ${deployment.display} as $status. Retries left: $retriesLeft. Next retry at: ${nextRetryAt.getOrElse("-")}")

    for {
      _ <- scheduledProcessesRepository.markFailedOnDeployWithStatus(deployment.id, status, retriesLeft, nextRetryAt)
      currentState <- scheduledProcessesRepository.findProcessData(deployment.id)
    } yield handleEvent(FailedOnDeployEvent(currentState, state))
  }

  private def markFailedAction(deployment: ScheduleDeploymentData, state: Option[StatusDetails]): RepositoryAction[Unit] = {
    logger.info(s"Marking ${deployment.display} as failed.")
    for {
      _ <- scheduledProcessesRepository.markFailed(deployment.id)
      currentState <- scheduledProcessesRepository.findProcessData(deployment.id)
    } yield handleEvent(FailedOnRunEvent(currentState, state))
  }

  def deactivate(processName: ProcessName): Future[Iterable[DeploymentId]] =
    for {
      activeSchedules <- getLatestDeploymentForActiveSchedules(processName)
      (runningDeploymentsForSchedules, _) <- synchronizeDeploymentsStates(processName, activeSchedules)
      _ <- activeSchedules.groupedByPeriodicProcess.map(p => deactivateAction(p.process)).sequence.runWithCallbacks
    } yield runningDeploymentsForSchedules.map(deployment => DeploymentId(deployment.toString))

  private def deactivateAction(process: PeriodicProcess): RepositoryAction[Callback] = {
    logger.info(s"Deactivate periodic process id: ${process.id.value}")
    for {
      _ <- scheduledProcessesRepository.markInactive(process.id)
      //we want to delete jars only after we successfully mark process as inactive. It's better to leave jar garbage than
      //have process without jar
    } yield () => jarManager.deleteJar(process.deploymentData.jarFileName)
  }

  def deploy(deployment: PeriodicProcessDeployment): Future[Unit] = {
    // TODO: set status before deployment?
    val id = deployment.id
    val deploymentData = DeploymentData(DeploymentId(id.value.toString), DeploymentData.systemUser,
      additionalDeploymentDataProvider.prepareAdditionalData(deployment))
    val deploymentWithJarData = deployment.periodicProcess.deploymentData
    val deploymentAction = for {
      _ <- Future.successful(logger.info("Deploying scenario {} for deployment id {}", deploymentWithJarData.processVersion, id))
      enrichedProcessConfig <- processConfigEnricher.onDeploy(
        ProcessConfigEnricher.DeployData(deploymentWithJarData.canonicalProcess, deploymentWithJarData.inputConfigDuringExecutionJson, deployment))
      enrichedDeploymentWithJarData = deploymentWithJarData.copy(inputConfigDuringExecutionJson = enrichedProcessConfig.inputConfigDuringExecutionJson)
      externalDeploymentId <- jarManager.deployWithJar(enrichedDeploymentWithJarData, deploymentData)
    } yield externalDeploymentId
    deploymentAction
      .flatMap { externalDeploymentId =>
        logger.info("Scenario has been deployed {} for deployment id {}", deploymentWithJarData.processVersion, id)
        //TODO: add externalDeploymentId??
        scheduledProcessesRepository.markDeployed(id)
          .flatMap(_ => scheduledProcessesRepository.findProcessData(id))
          .flatMap(afterChange => handleEvent(DeployedEvent(afterChange, externalDeploymentId))).run
      }
      // We can recover since deployment actor watches only future completion.
      .recoverWith { case exception =>
        logger.error(s"Scenario deployment ${deployment.display} failed", exception)
        handleFailedDeployment(deployment, None).run
      }
  }

  //TODO: allow access to DB in listener?
  private def handleEvent(event: PeriodicProcessEvent): scheduledProcessesRepository.Action[Unit] = {
    scheduledProcessesRepository.monad.pure {
      try {
        periodicProcessListener.onPeriodicProcessEvent.applyOrElse(event, (_:PeriodicProcessEvent) => ())
      } catch {
        case NonFatal(e) => throw new PeriodicProcessException("Failed to invoke listener", e)
      }
    }
  }

  private def now(): LocalDateTime = LocalDateTime.now(clock)

  def mergeStatusWithDeployments(name: ProcessName, originalStatuses: List[StatusDetails]): Future[StatusDetails] = {
    getLatestDeploymentForActiveSchedulesOld(name).map { maybeProcessDeployment =>
      maybeProcessDeployment.map { processDeployment =>
        processDeployment.state.status match {
          case PeriodicProcessDeploymentStatus.Scheduled => createScheduledProcessState(processDeployment)
          case PeriodicProcessDeploymentStatus.Failed => createFailedProcessState(processDeployment)
          case PeriodicProcessDeploymentStatus.Deployed | PeriodicProcessDeploymentStatus.Finished =>
            extractDeploymentState(processDeployment, originalStatuses).map { o =>
              if (o.status == SimpleStateStatus.Finished || o.status == SimpleStateStatus.Canceled) {
                logger.debug(s"Periodic scenario: $name in ${processDeployment.state.status} local state, and ${o.status} remote state. Rewriting to $WaitingForScheduleStatus state")
                o.copy(status = WaitingForScheduleStatus)
              } else {
                o
              }
            }.getOrElse {
              // TODO: or finished?
              StatusDetails(SimpleStateStatus.Canceled, None)
            }
        }
      }.getOrElse {
        // FIXME
        InconsistentStateDetector.extractAtMostOneStatus(originalStatuses).map { o =>
          if (ProblemStateStatus.isProblemStatus(o.status)) {
            logger.debug(s"Periodic scenario: $name with not scheduled local state, and ${o.status} remote state. Rewriting to ${SimpleStateStatus.Canceled} state")
            // TODO: or not deployed
            o.copy(status = SimpleStateStatus.Canceled)
          } else {
            o
          }
        }.getOrElse {
          // TODO: or canceled or finished?
          StatusDetails(SimpleStateStatus.NotDeployed, None)
        }
      }
    }
  }

  private def toDeploymentStatuses(statusesList: List[StatusDetails]) = {
    statusesList.collect {
      case status@StatusDetails(_, Some(deploymentId), _, _, _, _, _) =>
        deploymentId -> status
    }.toMap
  }

  private def createScheduledProcessState(processDeployment: PeriodicProcessDeployment): StatusDetails =
    StatusDetails(
      status = ScheduledStatus(processDeployment.runAt),
      Some(DeploymentId(processDeployment.id.value.toString)),
      Some(ExternalDeploymentId("future")),
      version = Option(processDeployment.periodicProcess.processVersion),
      //TODO: this date should be passed/handled through attributes
      startTime = Option(processDeployment.runAt.toEpochSecond(ZoneOffset.UTC)),
      attributes = Option.empty,
      errors = List.empty
    )

  private def createFailedProcessState(processDeployment: PeriodicProcessDeployment): StatusDetails =
    StatusDetails(
      status = ProblemStateStatus.Failed,
      Some(DeploymentId(processDeployment.id.value.toString)),
      Some(ExternalDeploymentId("future")),
      version = Option(processDeployment.periodicProcess.processVersion),
      startTime = Option.empty,
      attributes = Option.empty,
      errors = List.empty
    )

  // TODO: what about the legacy statuses without deploymentId?
  private def extractDeploymentState(processDeployment: PeriodicProcessDeployment, statuses: List[StatusDetails]): Option[StatusDetails] = {
    statuses.find(_.deploymentId.contains(DeploymentId(processDeployment.id.toString)))
  }

  // TODO: use it for process state displaying
  def getLatestDeploymentsForLatestSchedules(processName: ProcessName, inactiveProcessesMaxCount: Int, deploymentsPerScheduleMaxCount: Int): Future[SchedulesState] = {
    (for {
      activeSchedulesResult <- scheduledProcessesRepository.getLatestDeploymentsForActiveSchedules(processName, deploymentsPerScheduleMaxCount)
      inactiveSchedulesResult <- scheduledProcessesRepository.getLatestDeploymentsForLatestInactiveSchedules(processName, inactiveProcessesMaxCount, deploymentsPerScheduleMaxCount)
    } yield activeSchedulesResult.addAll(inactiveSchedulesResult)).run
  }

  // FIXME: This method shouldn't be used - latest active schedule is important only for presentation results
  def getLatestDeploymentForActiveSchedulesOld(processName: ProcessName): Future[Option[PeriodicProcessDeployment]] = {
    getLatestDeploymentForActiveSchedules(processName).map(pickMostImportantDeployment)
  }

  def getLatestDeploymentForActiveSchedules(processName: ProcessName): Future[SchedulesState] =
    scheduledProcessesRepository.getLatestDeploymentsForActiveSchedules(processName, deploymentsPerScheduleMaxCount = 1).run

  def getLatestDeploymentForLatestInactiveSchedules(processName: ProcessName, inactiveProcessesMaxCount: Int, deploymentsPerScheduleMaxCount: Int): Future[SchedulesState] =
    scheduledProcessesRepository.getLatestDeploymentsForLatestInactiveSchedules(processName, inactiveProcessesMaxCount, deploymentsPerScheduleMaxCount).run

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
  def pickMostImportantDeployment(schedules: SchedulesState): Option[PeriodicProcessDeployment] = {
    val deployments = schedules.schedules.toList.flatMap {
      case (scheduleId, scheduleData) =>
        scheduleData.latestDeployments.map(_.toFullDeploymentData(scheduleData.process, scheduleId.scheduleName))
    }.sortBy(_.runAt)
    logger.debug("Found deployments: {}", deployments.map(_.display))

    def first(status: PeriodicProcessDeploymentStatus) =
      deployments.find(_.state.status == status)

    def last(status: PeriodicProcessDeploymentStatus) =
      deployments.reverse.find(_.state.status == status)

    first(PeriodicProcessDeploymentStatus.Deployed)
      .orElse(last(PeriodicProcessDeploymentStatus.Failed))
      .orElse(first(PeriodicProcessDeploymentStatus.Scheduled))
      .orElse(last(PeriodicProcessDeploymentStatus.Finished))
  }

}