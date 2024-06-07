package pl.touk.nussknacker.ui.process.newdeployment.synchronize

import akka.actor.{ActorSystem, Cancellable}
import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._

import scala.concurrent.Await
import scala.concurrent.duration._

// TODO: Properly handle HA setup: synchronizeAll() should be invoked only on one instance of designer in a time
class DeploymentsStatusesSynchronizationScheduler(
    actorSystem: ActorSystem,
    synchronizer: DeploymentsStatusesSynchronizer,
    config: DeploymentsStatusesSynchronizationConfig
) extends AutoCloseable {

  @volatile private var scheduledJob: Option[Cancellable] = None

  import actorSystem.dispatcher

  def run(): Unit = {
    scheduledJob = Some(
      actorSystem.scheduler.scheduleAtFixedRate(0 seconds, config.delayBetweenSynchronizations) { () =>
        Await.result(synchronizer.synchronizeAll(), config.synchronizationTimeout)
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

  def parse(config: Config): DeploymentsStatusesSynchronizationConfig =
    config
      .getAs[DeploymentsStatusesSynchronizationConfig]("deploymentStatusesSynchronization")
      .getOrElse(DeploymentsStatusesSynchronizationConfig())

}
