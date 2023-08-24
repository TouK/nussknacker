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
import pl.touk.nussknacker.engine.management.periodic.model.PeriodicProcessDeploymentStatus.{Deployed, FailedOnDeploy, PeriodicProcessDeploymentStatus, RetryingDeploy}
import pl.touk.nussknacker.engine.management.periodic.model.{DeploymentWithJarData, PeriodicProcessDeployment, PeriodicProcessDeploymentStatus}
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

  private implicit class WithCallbacks(result: RepositoryAction[Callback]) {
    def runWithCallbacks: Future[Unit] = result.run.flatMap(_())
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

  def prepareInitialScheduleDates(schedule: ScheduleProperty): Either[PeriodicProcessException, List[(Option[String], Option[LocalDateTime])]] = {
    val schedules = schedule match {
      case MultipleScheduleProperty(schedules) => schedules.map { case (k, pp) =>
        pp.nextRunAt(clock).map(v => Some(k) -> v)
      }.toList.sequence
      case e: SingleScheduleProperty => e.nextRunAt(clock).map(t => List((None, t)))
    }
    (schedules match {
      case Left(error) => Left(s"Failed to parse periodic property: $error")
      case Right(scheduleDates) if scheduleDates.forall(_._2.isEmpty) => Left(s"No future date determined by $schedule")
      case correctSchedules => correctSchedules
    }).left.map(new PeriodicProcessException(_))
  }

  private def scheduleWithInitialDates(scheduleProperty: ScheduleProperty, processVersion: ProcessVersion, canonicalProcess: CanonicalProcess, scheduleDates: List[(Option[String], Option[LocalDateTime])]): Future[Unit] = {
    logger.info("Scheduling periodic scenario: {} on {}", processVersion, scheduleDates)
    for {
      deploymentWithJarData <- jarManager.prepareDeploymentWithJar(processVersion, canonicalProcess)
      enrichedProcessConfig <- processConfigEnricher.onInitialSchedule(ProcessConfigEnricher.InitialScheduleData(deploymentWithJarData.canonicalProcess, deploymentWithJarData.inputConfigDuringExecutionJson))
      enrichedDeploymentWithJarData = deploymentWithJarData.copy(inputConfigDuringExecutionJson = enrichedProcessConfig.inputConfigDuringExecutionJson)
      _ <- initialSchedule(scheduleProperty, scheduleDates, enrichedDeploymentWithJarData)
    } yield ()
  }

  private def initialSchedule(scheduleMap: ScheduleProperty,
                              scheduleDates: List[(Option[String], Option[LocalDateTime])],
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

  //Currently we don't allow simultaneous runs of one scenario - only sequential, so if other schedule kicks in, it'll have to wait
  private def checkIfNotRunning(toDeploy: PeriodicProcessDeployment): Future[Option[PeriodicProcessDeployment]] = {
    delegateDeploymentManager.getProcessStates(toDeploy.periodicProcess.processVersion.processName)(DataFreshnessPolicy.Fresh)
      .map(_.value)
      .map(InconsistentStateDetector.extractAtMostOneStatus)
      .map {
        case Some(state) if IsFollowingDeployStatusDeterminer.isFollowingDeployStatus(state.status) =>
          logger.debug(s"Deferring run of ${toDeploy.display} as scenario is currently running")
          None
        case _ => Some(toDeploy)
      }
  }

  def handleFinished: Future[Unit] = {

    def handleSingleProcess(deployedProcess: PeriodicProcessDeployment): Future[Unit] = {
      val processName = deployedProcess.periodicProcess.processVersion.processName
      delegateDeploymentManager.getProcessStates(processName)(DataFreshnessPolicy.Fresh)
        .map(_.value)
        .map(InconsistentStateDetector.extractAtMostOneStatus)
        .flatMap { state =>
          handleFinishedAction(deployedProcess, state)
            .flatMap { needsReschedule =>
              if (needsReschedule) reschedule(deployedProcess) else scheduledProcessesRepository.monad.pure(()).emptyCallback
            }.runWithCallbacks
        }
    }

    for {
      executed <- scheduledProcessesRepository.findDeployedOrFailedOnDeploy.run
      //we handle each job separately, if we fail at some point, we will continue on next handleFinished run
      _ <- Future.sequence(executed.map(handleSingleProcess))
    } yield ()
  }

  //We assume that this method leaves with data in consistent state
  private def handleFinishedAction(deployedProcess: PeriodicProcessDeployment, processState: Option[StatusDetails]): RepositoryAction[NeedsReschedule] = {
    implicit class RichRepositoryAction[Unit](a: RepositoryAction[Unit]){
      def needsReschedule(value: Boolean): RepositoryAction[NeedsReschedule] = a.map(_ => value)
    }
    processState match {
      case Some(js) if ProblemStateStatus.isProblemStatus(js.status) => markFailedAction(deployedProcess, processState).needsReschedule(executionConfig.rescheduleOnFailure)
      case Some(js) if js.status == SimpleStateStatus.Finished => markFinished(deployedProcess, processState).needsReschedule(value = true)
      case None if deployedProcess.state.status == Deployed => markFinished(deployedProcess, processState).needsReschedule(value = true)
      case _ => scheduledProcessesRepository.monad.pure(()).needsReschedule(value = false)
    }
  }

  //Mark process as Finished and reschedule - we do it transactionally
  private def reschedule(deployment: PeriodicProcessDeployment): RepositoryAction[Callback] = {
    logger.info(s"Rescheduling ${deployment.display}")
    val process = deployment.periodicProcess
    for {
      callback <- deployment.nextRunAt(clock) match {
        case Right(Some(futureDate)) =>
          logger.info(s"Rescheduling ${deployment.display} to $futureDate")
          scheduledProcessesRepository.schedule(process.id, deployment.scheduleName, futureDate, deploymentRetryConfig.deployMaxRetries).flatMap { data =>
            handleEvent(ScheduledEvent(data, firstSchedule = false))
          }.emptyCallback
        case Right(None) =>
          scheduledProcessesRepository.findScheduled(deployment.periodicProcess.id).flatMap { scheduledDeployments =>
            if (scheduledDeployments.isEmpty) {
              logger.info(s"No next run of ${deployment.display}. Deactivating")
              deactivateAction(process.processVersion.processName)
            } else {
              logger.info(s"No next run of ${deployment.display} but there are still ${scheduledDeployments.size} scheduled deployments: ${scheduledDeployments.map(_.display)}")
              scheduledProcessesRepository.monad.pure(()).emptyCallback
            }
          }
        case Left(error) =>
          handleInvalidSchedule(deployment, error)
          deactivateAction(process.processVersion.processName)
      }
    } yield callback
  }

  private def markFinished(deployment: PeriodicProcessDeployment, state: Option[StatusDetails]): RepositoryAction[Unit] = {
    logger.info(s"Marking ${deployment.display} as finished")
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
        (None, FailedOnDeploy)
      else
        (Some(calculateNextRetryAt), RetryingDeploy)

    logger.info(s"Marking ${deployment.display} as $status. Retries left: $retriesLeft. Next retry at: ${nextRetryAt.getOrElse("-")}")

    for {
      _ <- scheduledProcessesRepository.markFailedOnDeployWithStatus(deployment.id, status, retriesLeft, nextRetryAt)
      currentState <- scheduledProcessesRepository.findProcessData(deployment.id)
    } yield handleEvent(FailedOnDeployEvent(currentState, state))
  }

  private def markFailedAction(deployment: PeriodicProcessDeployment, state: Option[StatusDetails]): RepositoryAction[Unit] = {
    logger.info(s"Marking ${deployment.display} as failed.")
    for {
      _ <- scheduledProcessesRepository.markFailed(deployment.id)
      currentState <- scheduledProcessesRepository.findProcessData(deployment.id)
    } yield handleEvent(FailedOnRunEvent(currentState, state))
  }

  def deactivate(processName: ProcessName): Future[Unit] = for {
    statuses <- delegateDeploymentManager.getProcessStates(processName)(DataFreshnessPolicy.Fresh)
    status = InconsistentStateDetector.extractAtMostOneStatus(statuses.value)
    maybePeriodicDeployment <- getLatestDeploymentForActiveSchedules(processName)
    actionResult <- maybePeriodicDeployment match {
      case Some(periodicDeployment) => handleFinishedAction(periodicDeployment, status)
        .flatMap(_ => deactivateAction(processName))
        .runWithCallbacks
      case None => deactivateAction(processName).runWithCallbacks
    }
  } yield actionResult

  private def deactivateAction(processName: ProcessName): RepositoryAction[Callback] = {
    logger.info(s"Deactivate $processName")
    for {
      // Order does matter. We need to find process data for *active* process and then
      // it can be safely marked as inactive. Otherwise we would not be able to find the data
      // and leave unused jars.
      processData <- scheduledProcessesRepository.findProcessData(processName)
      _ <- scheduledProcessesRepository.markInactive(processName)
      //we want to delete jars only after we successfully mark process as inactive. It's better to leave jar garbage than
      //have process without jar
    } yield () => Future.sequence(processData.map(_.deploymentData.jarFileName).map(jarManager.deleteJar)).map(_ => ())
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

  // This case should not happen. It would mean periodic property was valid when scheduling a process
  // but was made invalid when rescheduling again.
  private def handleInvalidSchedule(deployment: PeriodicProcessDeployment, error: String) = {
    logger.error(s"Wrong periodic property, error: $error. Deactivating ${deployment.display}")
    deactivateAction(deployment.periodicProcess.processVersion.processName)
  }

  def mergeStatusWithDeployments(name: ProcessName, originalStatuses: List[StatusDetails]): Future[StatusDetails] = {
    getLatestDeploymentForActiveSchedules(name).map { maybeProcessDeployment =>
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


  /**
   * @Important: this method has to be public because it can be used by dedicated process manager
   *
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
  def getLatestDeploymentForActiveSchedules(processName: ProcessName): Future[Option[PeriodicProcessDeployment]] = {
    scheduledProcessesRepository.getLatestDeploymentForActiveSchedules(processName)
      .map(_.sortBy(_.runAt)).run
      .map { deployments =>
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

}
