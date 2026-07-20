package pl.touk.nussknacker.ui.process.newdeployment.synchronize

import cats.effect.IO
import cats.effect.kernel.Resource
import com.typesafe.scalalogging.LazyLogging
import org.apache.pekko.actor.{ActorSystem, Cancellable}
import pl.touk.nussknacker.ui.ha.Leadership

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

object DeploymentsStatusesSynchronizationScheduler extends LazyLogging {

  def resource(
      actorSystem: ActorSystem,
      synchronizer: DeploymentsStatusesSynchronizer,
      config: DeploymentsStatusesSynchronizationConfig,
      leadership: Leadership
  ): Resource[IO, Cancellable] = {

    import actorSystem.dispatcher

    Resource.make(IO {
      actorSystem.scheduler.scheduleAtFixedRate(0 seconds, config.delayBetweenSynchronizations) { () =>
        if (leadership.isLeader()) {
          Try(Await.result(synchronizer.synchronizeAll(), config.synchronizationTimeout)).failed.foreach { ex =>
            logger.error(
              s"Error during deployments statuses synchronization. Synchronization will be retried in ${config.delayBetweenSynchronizations}",
              ex
            )
          }
        } else {
          logger.debug("Skipping deployments statuses synchronization — not a leader")
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
