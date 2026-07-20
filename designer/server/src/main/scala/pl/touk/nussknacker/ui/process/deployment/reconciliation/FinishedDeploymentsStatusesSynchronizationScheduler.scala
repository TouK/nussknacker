package pl.touk.nussknacker.ui.process.deployment.reconciliation

import cats.effect.IO
import cats.effect.kernel.Resource
import com.typesafe.scalalogging.LazyLogging
import org.apache.pekko.actor.{ActorSystem, Cancellable}
import pl.touk.nussknacker.ui.ha.Leadership

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

object FinishedDeploymentsStatusesSynchronizationScheduler extends LazyLogging {

  def resource(
      actorSystem: ActorSystem,
      reconciler: ScenarioDeploymentReconciler,
      config: FinishedDeploymentsStatusesSynchronizationConfig,
      leadership: Leadership
  ): Resource[IO, Cancellable] = {
    import actorSystem.dispatcher

    Resource.make(IO {
      actorSystem.scheduler.scheduleAtFixedRate(0 seconds, config.delayBetweenSynchronizations) { () =>
        if (leadership.isLeader()) {
          Try(
            Await.result(reconciler.synchronizeEngineFinishedDeploymentsLocalStatuses(), config.synchronizationTimeout)
          ).failed.foreach { ex =>
            logger.error(
              s"Error during finished deployments statuses synchronization. Synchronization will be retried in ${config.delayBetweenSynchronizations}",
              ex
            )
          }
        } else {
          logger.debug("Skipping finished deployments statuses synchronization — not a leader")
        }
      }
    }) { scheduledJob =>
      IO {
        scheduledJob.cancel()
      }
    }
  }

}

final case class FinishedDeploymentsStatusesSynchronizationConfig(
    // This should be lower than time during which, all archived jobs on flink will be retained.
    // You can tweak this by configuring Flink's limit of jobs kept in history: web.history (default is 5 jobs limit)
    // and historyserver.archive.fs.refresh-interval (default is 10 seconds)
    delayBetweenSynchronizations: FiniteDuration = 5 minutes,
    synchronizationTimeout: FiniteDuration = 30 seconds
)
