package pl.touk.nussknacker.engine.management.periodic

import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.management.periodic.db.PeriodicProcessesRepository
import pl.touk.nussknacker.engine.management.periodic.model.PeriodicProcessDeploymentStatus.{Deployed, PeriodicProcessDeploymentStatus}
import pl.touk.nussknacker.engine.management.periodic.model.{DeploymentWithJarData, PeriodicProcessDeployment, PeriodicProcessDeploymentStatus}
import pl.touk.nussknacker.engine.management.periodic.service._

import java.time.chrono.ChronoLocalDateTime
import java.time.{Clock, LocalDateTime}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class PeriodicProcessService(delegateDeploymentManager: DeploymentManager,
                             jarManager: JarManager,
                             scheduledProcessesRepository: PeriodicProcessesRepository,
                             periodicProcessListener: PeriodicProcessListener,
                             additionalDeploymentDataProvider: AdditionalDeploymentDataProvider,
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

  private implicit class RichPeriodicProcessDeployment(periodicDeployment: PeriodicProcessDeployment) {
    def display: String = s"${periodicDeployment.periodicProcess.processVersion} with ${periodicDeployment
        .scheduleName.map(sn => s"schedule=$sn and ").getOrElse("")}deploymentId=${periodicDeployment.periodicProcess.id}"
  }

  def schedule(schedule: ScheduleProperty,
               processVersion: ProcessVersion,
               processJson: String): Future[Unit] = {
    findInitialScheduleDates(schedule) match {
      case Right(scheduleDates) if scheduleDates.forall(_._2.isEmpty) =>
        Future.failed(new PeriodicProcessException(s"No future date determined by $schedule"))
      case Right(scheduleDates) =>
        scheduleWithInitialDates(schedule, processVersion, processJson, scheduleDates)
      case Left(error) =>
        Future.failed(new PeriodicProcessException(s"Failed to parse periodic property: $error"))
    }
  }

  private def findInitialScheduleDates(schedule: ScheduleProperty) = {
    schedule match {
      case MultipleScheduleProperty(schedules) => schedules.map { case (k, pp) =>
        pp.nextRunAt(clock).map(v => Some(k) -> v)
      }.toList.sequence
      case e: SingleScheduleProperty => e.nextRunAt(clock).right.map(t => List((None, t)))
    }
  }

  private def scheduleWithInitialDates(scheduleProperty: ScheduleProperty, processVersion: ProcessVersion, processJson: String, scheduleDates: List[(Option[String], Option[LocalDateTime])]) = {
    logger.info("Scheduling periodic scenario: {} on {}", processVersion, scheduleDates)
    for {
      deploymentWithJarData <- jarManager.prepareDeploymentWithJar(processVersion, processJson)
      enrichedProcessConfig <- processConfigEnricher.onInitialSchedule(ProcessConfigEnricher.InitialScheduleData(deploymentWithJarData.processJson, deploymentWithJarData.inputConfigDuringExecutionJson))
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
          scheduledProcessesRepository.schedule(process.id, name, date).flatMap { data =>
            handleEvent(ScheduledEvent(data, firstSchedule = true))
          }
        case (name, None) =>
          logger.warn(s"Schedule $name does not have date to schedule")
          monad.pure(())
      }.sequence
    }.run.map(_ => ())
  }

  def findToBeDeployed: Future[Seq[PeriodicProcessDeployment]] = {
    scheduledProcessesRepository.findToBeDeployed.run.flatMap { toDeployList =>
      Future.sequence(toDeployList.map(checkIfNotRunning)).map(_.flatten)
    }
  }

  //Currently we don't allow simultaneous runs of one scenario - only sequential, so if other schedule kicks in, it'll have to wait
  private def checkIfNotRunning(toDeploy: PeriodicProcessDeployment): Future[Option[PeriodicProcessDeployment]] = {
    delegateDeploymentManager.findJobStatus(toDeploy.periodicProcess.processVersion.processName).map {
      case Some(state) if state.isDeployed =>
        logger.debug(s"Deferring run of ${toDeploy.display} as scenario is currently running")
        None
      case _ => Some(toDeploy)
    }
  }

  def handleFinished: Future[Unit] = {

    def handleSingleProcess(deployedProcess: PeriodicProcessDeployment): Future[Unit] = {
      delegateDeploymentManager.findJobStatus(deployedProcess.periodicProcess.processVersion.processName).flatMap { state =>
        handleFinishedAction(deployedProcess, state)
          .flatMap { needsReschedule =>
            if (needsReschedule) reschedule(deployedProcess) else scheduledProcessesRepository.monad.pure(()).emptyCallback
          }.runWithCallbacks
      }
    }

    for {
      deployed <- scheduledProcessesRepository.findDeployed.run
      //we handle each job separately, if we fail at some point, we will continue on next handleFinished run
      handled <- Future.sequence(deployed.map(handleSingleProcess))
    } yield handled.map(_ -> {})
  }

  //We assume that this method leaves with data in consistent state
  private def handleFinishedAction(deployedProcess: PeriodicProcessDeployment, processState: Option[ProcessState]): RepositoryAction[NeedsReschedule] = {
    implicit class RichRepositoryAction[Unit](a: RepositoryAction[Unit]){
      def needsReschedule(value: Boolean): RepositoryAction[NeedsReschedule] = a.map(_ => value)
    }
    processState match {
      case Some(js) if js.status.isFailed => markFailedAction(deployedProcess, processState).needsReschedule(value = false)
      case Some(js) if js.status.isFinished => markFinished(deployedProcess, processState).needsReschedule(value = true)
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
          scheduledProcessesRepository.schedule(process.id, deployment.scheduleName, futureDate).flatMap { data =>
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
          // This case should not happen. It would mean periodic property was valid when scheduling a process
          // but was made invalid when rescheduling again.
          logger.error(s"Wrong periodic property, error: $error. Deactivating ${deployment.display}")
          deactivateAction(process.processVersion.processName)
      }
    } yield callback
  }

  private def markFinished(deployment: PeriodicProcessDeployment, state: Option[ProcessState]): RepositoryAction[Unit] = {
    logger.info(s"Marking ${deployment.display} as finished")
    for {
      _ <- scheduledProcessesRepository.markFinished(deployment.id)
      currentState <- scheduledProcessesRepository.findProcessData(deployment.id)
    } yield handleEvent(FinishedEvent(currentState, state))
  }

  private def markFailedAction(deployment: PeriodicProcessDeployment, state: Option[ProcessState]): RepositoryAction[Unit] = {
    logger.info(s"Marking ${deployment.display} as failed")
    for {
      _ <- scheduledProcessesRepository.markFailed(deployment.id)
      currentState <- scheduledProcessesRepository.findProcessData(deployment.id)
    } yield handleEvent(FailedEvent(currentState, state))
  }

  def deactivate(processName: ProcessName): Future[Unit] = for {
    status <- delegateDeploymentManager.findJobStatus(processName)
    maybePeriodicDeployment <- getLatestDeployment(processName)
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
  def getLatestDeployment(processName: ProcessName): Future[Option[PeriodicProcessDeployment]] = {
    implicit val localDateOrdering: Ordering[LocalDateTime] = Ordering.by(identity[ChronoLocalDateTime[_]])

    scheduledProcessesRepository.getLatestDeploymentForEachSchedule(processName)
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

  def deploy(deployment: PeriodicProcessDeployment): Future[Unit] = {
    // TODO: set status before deployment?
    val id = deployment.id
    val deploymentData = DeploymentData(DeploymentId(id.value.toString), DeploymentData.systemUser,
      additionalDeploymentDataProvider.prepareAdditionalData(deployment))
    val deploymentWithJarData = deployment.periodicProcess.deploymentData
    val deploymentAction = for {
      _ <- Future.successful(logger.info("Deploying scenario {} for deployment id {}", deploymentWithJarData.processVersion, id))
      enrichedProcessConfig <- processConfigEnricher.onDeploy(
        ProcessConfigEnricher.DeployData(deploymentWithJarData.processJson, deploymentWithJarData.inputConfigDuringExecutionJson, deployment))
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
        markFailedAction(deployment, None).run
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
}
