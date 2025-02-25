package pl.touk.nussknacker.ui.process.newdeployment.synchronize

import akka.actor.{ActorSystem, Cancellable}
import cats.effect.IO
import cats.effect.kernel.Resource
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

// TODO: Properly handle HA setup: synchronizeAll() should be invoked only on one instance of designer in a time
object DeploymentsStatusesSynchronizationScheduler extends LazyLogging {

  def resource(
      actorSystem: ActorSystem,
      synchronizer: DeploymentsStatusesSynchronizer,
      config: DeploymentsStatusesSynchronizationConfig
  ): Resource[IO, Cancellable] = {

    import actorSystem.dispatcher

    Resource.make(IO {
      actorSystem.scheduler.scheduleAtFixedRate(0 seconds, config.delayBetweenSynchronizations) { () =>
        Try(Await.result(synchronizer.synchronizeAll(), config.synchronizationTimeout)).failed.foreach { ex =>
          logger.error(
            s"Error during deployments statuses synchronization. Synchronization will be retried in ${config.delayBetweenSynchronizations}",
            ex
          )
        }
      }
    }) { scheduledJob =>
      IO {
        scheduledJob.cancel()
      }
    }
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
