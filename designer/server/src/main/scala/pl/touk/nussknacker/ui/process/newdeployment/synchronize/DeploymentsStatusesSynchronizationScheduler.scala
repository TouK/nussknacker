package pl.touk.nussknacker.ui.process.newdeployment.synchronize

import org.apache.pekko.actor.{ActorSystem, Cancellable}
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

// TODO: Properly handle HA setup: synchronizeAll() should be invoked only on one instance of designer in a time
class DeploymentsStatusesSynchronizationScheduler(
    actorSystem: ActorSystem,
    synchronizer: DeploymentsStatusesSynchronizer,
    config: DeploymentsStatusesSynchronizationConfig
) extends AutoCloseable
    with LazyLogging {

  @volatile private var scheduledJob: Option[Cancellable] = None

  import actorSystem.dispatcher

  def start(): Unit = {
    scheduledJob = Some(
      actorSystem.scheduler.scheduleAtFixedRate(0 seconds, config.delayBetweenSynchronizations) { () =>
        Try(Await.result(synchronizer.synchronizeAll(), config.synchronizationTimeout)).failed.foreach { ex =>
          logger.error(
            s"Error during deployments statuses synchronization. Synchronization will be retried in ${config.delayBetweenSynchronizations}",
            ex
          )
        }
      }
    )
  }

  override def close(): Unit = {
    scheduledJob.map(_.cancel())
  }

}

final case class DeploymentsStatusesSynchronizationConfig(
    delayBetweenSynchronizations: FiniteDuration = 1 second,
    synchronizationTimeout: FiniteDuration = 10 seconds
)

object DeploymentsStatusesSynchronizationConfig {

  val ConfigPath = "deploymentStatusesSynchronization"

  def parse(config: Config): DeploymentsStatusesSynchronizationConfig =
    config
      .getAs[DeploymentsStatusesSynchronizationConfig](ConfigPath)
      .getOrElse(DeploymentsStatusesSynchronizationConfig())

}
