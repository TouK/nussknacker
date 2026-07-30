package pl.touk.nussknacker.ui.process.periodic

import cats.data.OptionT
import cats.instances.future._
import com.typesafe.scalalogging.LazyLogging
import org.apache.pekko.actor.{Actor, Props, Timers}
import org.apache.pekko.pattern.pipe
import pl.touk.nussknacker.ui.process.periodic.model.PeriodicProcessDeployment

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object DeploymentActor {

  def props(
      service: PeriodicProcessService,
      lock: PeriodicDeploymentLock,
      interval: FiniteDuration
  ): Props = {
    props(service.findToBeDeployed, service.deploy, lock, interval)
  }

  private[periodic] def props(
      findToBeDeployed: => Future[Seq[PeriodicProcessDeployment]],
      deploy: PeriodicProcessDeployment => Future[Unit],
      lock: PeriodicDeploymentLock,
      interval: FiniteDuration
  ): Props = {
    Props(new DeploymentActor(findToBeDeployed, deploy, lock, interval))
  }

  private sealed trait Msg

  private object Msg {
    case object CheckToBeDeployed                                           extends Msg
    case class ReadyToDeploy(deployment: Option[PeriodicProcessDeployment]) extends Msg
    case object DeploymentCompleted                                         extends Msg
  }

}

class DeploymentActor(
    findToBeDeployed: => Future[Seq[PeriodicProcessDeployment]],
    deploy: PeriodicProcessDeployment => Future[Unit],
    lock: PeriodicDeploymentLock,
    interval: FiniteDuration
) extends Actor
    with Timers
    with LazyLogging {

  import context.dispatcher

  import DeploymentActor._
  import DeploymentActor.Msg._

  override def preStart(): Unit = {
    logger.info(s"Initializing with $interval interval")
    timers.startTimerAtFixedRate(key = "checkToBeDeployed", msg = CheckToBeDeployed, interval = interval)
  }

  override def receive: Receive = inState(handleIdle)

  private def inState(f: Msg => Unit): Receive = { case msg: Msg => f(msg) }

  private def becomeIdle(): Unit = context.become(inState(handleIdle))

  private def handleIdle(msg: Msg): Unit = msg match {
    case CheckToBeDeployed =>
      context.become(inState(handleChecking))
      prepareDeployment().map(ReadyToDeploy(_)).pipeTo(self)
    case ReadyToDeploy(_)    => ()
    case DeploymentCompleted => ()
  }

  private def handleChecking(msg: Msg): Unit = msg match {
    case CheckToBeDeployed => ()
    case ReadyToDeploy(None) =>
      becomeIdle()
    case ReadyToDeploy(Some(runDetails)) =>
      logger.info(s"Deploying ${runDetails.display}")
      context.become(inState(handleOngoingDeployment(runDetails)))
      deploy(runDetails)
        .map(_ => DeploymentCompleted)
        .recover { case ex =>
          logger.error(s"Deployment of ${runDetails.display} failed unexpectedly", ex)
          DeploymentCompleted
        }
        .pipeTo(self)
    case DeploymentCompleted => ()
  }

  private def handleOngoingDeployment(runDetails: PeriodicProcessDeployment)(msg: Msg): Unit = msg match {
    case CheckToBeDeployed =>
      // TODO: on lock loss, update deployment status in DB so other nodes don't re-deploy the same scenario
      lock.acquireOrRenew().onComplete {
        case Success(false)     => logger.warn(s"Lost deployment lock while deploying ${runDetails.display}")
        case Failure(exception) => logger.warn(s"Failed to renew deployment lock for ${runDetails.display}", exception)
        case _                  => ()
      }
    case ReadyToDeploy(_)    => ()
    case DeploymentCompleted => becomeIdle()
  }

  private def prepareDeployment(): Future[Option[PeriodicProcessDeployment]] = {
    Future(findDeployment().value).flatten.recover { case ex =>
      logger.error("Periodic deployment check failed unexpectedly", ex)
      None
    }
  }

  private def findDeployment(): OptionT[Future, PeriodicProcessDeployment] = {
    for {
      _          <- acquireLock("pre-find")
      runDetails <- findFirst()
      _          <- acquireLock("pre-deploy")
    } yield runDetails
  }

  private def acquireLock(step: String): OptionT[Future, Unit] = {
    OptionT(lock.acquireOrRenew().map { acquired =>
      if (!acquired) logger.trace(s"Deployment lock not acquired ($step), skipping")
      Option.when(acquired)(())
    })
  }

  private def findFirst(): OptionT[Future, PeriodicProcessDeployment] = {
    OptionT(findToBeDeployed.map { scenarios =>
      logger.debug(s"Found ${scenarios.size} to be deployed: ${scenarios.map(_.display)}")
      scenarios.headOption
    })
  }

}
