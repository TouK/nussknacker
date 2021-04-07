package pl.touk.nussknacker.engine.management.periodic

import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.management.periodic.db.PeriodicProcessesRepository
import pl.touk.nussknacker.engine.management.periodic.model.PeriodicProcessDeploymentStatus.Deployed
import pl.touk.nussknacker.engine.management.periodic.model.{DeploymentWithJarData, PeriodicProcessDeployment}
import pl.touk.nussknacker.engine.management.periodic.service._

import java.time.chrono.ChronoLocalDateTime
import java.time.{Clock, LocalDateTime}
import scala.concurrent.{ExecutionContext, Future}

class PeriodicProcessService(delegateProcessManager: ProcessManager,
                             jarManager: JarManager,
                             scheduledProcessesRepository: PeriodicProcessesRepository,
                             periodicProcessListener: PeriodicProcessListener,
                             additionalDeploymentDataProvider: AdditionalDeploymentDataProvider,
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

  def schedule(schedule: BasePeriodicProperty,
               processVersion: ProcessVersion,
               processJson: String): Future[Unit] = {
    findInitialScheduleDates(schedule) match {
      case Right(scheduleDates) if scheduleDates.forall(_._2.isEmpty) =>
        Future.failed(new PeriodicProcessException(s"No future date determined by $schedule"))
      case Right(scheduleDates) =>
        logger.info("Scheduling periodic process: {} on {}", processVersion, scheduleDates)
        jarManager.prepareDeploymentWithJar(processVersion, processJson).flatMap { deploymentWithJarData =>
          initialSchedule(schedule, scheduleDates, deploymentWithJarData)
        }
      case Left(error) =>
        Future.failed(new PeriodicProcessException(s"Failed to parse periodic property: $error"))
    }
  }

  private def findInitialScheduleDates(schedule: BasePeriodicProperty) = {
    schedule match {
      case ComplexPeriodicProperty(schedules) => schedules.map { case (k, pp) =>
        pp.nextRunAt(clock).map(v => Some(k) -> v)
      }.toList.sequence
      case e: PeriodicProperty => e.nextRunAt(clock).right.map(t => List((None, t)))
    }
  }

  private def initialSchedule(scheduleMap: BasePeriodicProperty,
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
    delegateProcessManager.findJobStatus(toDeploy.periodicProcess.processVersion.processName).map {
      case Some(state) if state.isDeployed =>
        logger.debug(s"Deferring run of ${toDeploy.periodicProcess} with name ${toDeploy.scheduleName} as process is currently running")
        None
      case _ => Some(toDeploy)
    }
  }

  def handleFinished: Future[Unit] = {

    def handleSingleProcess(deployedProcess: PeriodicProcessDeployment): Future[Unit] = {
      delegateProcessManager.findJobStatus(deployedProcess.periodicProcess.processVersion.processName).flatMap { state =>
        handleFinishedAction(deployedProcess, state)
          .flatMap { needsReschedule =>
            if (needsReschedule) reschedule(deployedProcess, state) else scheduledProcessesRepository.monad.pure(()).emptyCallback
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
  private def reschedule(deployment: PeriodicProcessDeployment, state: Option[ProcessState]): RepositoryAction[Callback] = {
    logger.info(s"Rescheduling process $deployment")
    val process = deployment.periodicProcess
    for {
      callback <- deployment.nextRunAt(clock) match {
        case Right(Some(futureDate)) =>
          logger.info(s"Rescheduling process $deployment to $futureDate")
          scheduledProcessesRepository.schedule(process.id, deployment.scheduleName, futureDate).flatMap { data =>
            handleEvent(ScheduledEvent(data, firstSchedule = false))
          }.emptyCallback
        case Right(None) =>
          logger.info(s"No next run of $deployment. Deactivating")
          deactivateAction(process.processVersion.processName)
        case Left(error) =>
          // This case should not happen. It would mean periodic property was valid when scheduling a process
          // but was made invalid when rescheduling again.
          logger.error(s"Wrong periodic property, error: $error. Deactivating $deployment")
          deactivateAction(process.processVersion.processName)
      }
    } yield callback
  }

  private def markFinished(deployment: PeriodicProcessDeployment, state: Option[ProcessState]): RepositoryAction[Unit] = {
    logger.info(s"Marking process $deployment as finished")
    for {
      _ <- scheduledProcessesRepository.markFinished(deployment.id)
      currentState <- scheduledProcessesRepository.findProcessData(deployment.id)
    } yield handleEvent(FinishedEvent(currentState, state))
  }

  private def markFailedAction(deployment: PeriodicProcessDeployment, state: Option[ProcessState]): RepositoryAction[Unit] = {
    logger.info(s"Marking process $deployment as failed")
    for {
      _ <- scheduledProcessesRepository.markFailed(deployment.id)
      currentState <- scheduledProcessesRepository.findProcessData(deployment.id)
    } yield handleEvent(FailedEvent(currentState, state))
  }

  def deactivate(processName: ProcessName): Future[Unit] = for {
    status <- delegateProcessManager.findJobStatus(processName)
    maybePeriodicDeployment <- getNextScheduledDeployment(processName)
  } yield maybePeriodicDeployment match {
    case Some(periodicDeployment) => handleFinishedAction(periodicDeployment, status)
      .flatMap(_ => deactivateAction(processName))
      .runWithCallbacks
    case None => deactivateAction(processName).runWithCallbacks
  }

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

  def getNextScheduledDeployment(processName: ProcessName): Future[Option[PeriodicProcessDeployment]] = {
    implicit val localDateOrdering: Ordering[LocalDateTime] = Ordering.by(identity[ChronoLocalDateTime[_]])
    //TODO: so far we return only one
    scheduledProcessesRepository.getNextScheduledRunForSchedule(processName)
      .map(_.sortBy(_.runAt).headOption).run
  }

  def deploy(deployment: PeriodicProcessDeployment): Future[Unit] = {
    // TODO: set status before deployment?
    val id = deployment.id
    val deploymentData = DeploymentData(DeploymentId(id.value.toString), DeploymentData.systemUser,
      additionalDeploymentDataProvider.prepareAdditionalData(deployment))
    val deploymentWithJarData = deployment.periodicProcess.deploymentData
    val deploymentAction = for {
      _ <- Future.successful(logger.info("Deploying process {} for deployment id {}", deploymentWithJarData.processVersion, id))
      externalDeploymentId <- jarManager.deployWithJar(deploymentWithJarData, deploymentData)
    } yield externalDeploymentId
    deploymentAction
      .flatMap { externalDeploymentId =>
        logger.info("Process has been deployed {} for deployment id {}", deploymentWithJarData.processVersion, id)
        //TODO: add externalDeploymentId??
        scheduledProcessesRepository.markDeployed(id)
          .flatMap(_ => scheduledProcessesRepository.findProcessData(id))
          .flatMap(afterChange => handleEvent(DeployedEvent(afterChange, externalDeploymentId))).run
      }
      // We can recover since deployment actor watches only future completion.
      .recoverWith { case exception =>
        logger.error(s"Process deployment failed for deployment id $id", exception)
        markFailedAction(deployment, None).run
      }
  }

  //TODO: allow access to DB in listener?
  private def handleEvent(event: PeriodicProcessEvent): scheduledProcessesRepository.Action[Unit] = {
    scheduledProcessesRepository.monad.pure(
      periodicProcessListener.onPeriodicProcessEvent.applyOrElse(event, (_:PeriodicProcessEvent) => ()))
  }

}
