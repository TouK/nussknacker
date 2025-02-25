package pl.touk.nussknacker.ui.process.deployment.reconciliation

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
object FinishedDeploymentsStatusesSynchronizationScheduler extends LazyLogging {

  def resource(
      actorSystem: ActorSystem,
      reconciler: ScenarioDeploymentReconciler,
      config: FinishedDeploymentsStatusesSynchronizationConfig
  ): Resource[IO, Cancellable] = {
    import actorSystem.dispatcher

    Resource.make(IO {
      actorSystem.scheduler.scheduleAtFixedRate(0 seconds, config.delayBetweenSynchronizations) { () =>
        Try(
          Await.result(reconciler.synchronizeEngineFinishedDeploymentsLocalStatuses(), config.synchronizationTimeout)
        ).failed.foreach { ex =>
          logger.error(
            s"Error during finished deployments statuses synchronization. Synchronization will be retried in ${config.delayBetweenSynchronizations}",
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

final case class FinishedDeploymentsStatusesSynchronizationConfig(
    // This should be lower than time during which, all archived jobs on flink will be retained.
    // You can tweak this by configuring Flink's limit of jobs kept in history: web.history (default is 5 jobs limit)
    // and historyserver.archive.fs.refresh-interval (default is 10 seconds)
    delayBetweenSynchronizations: FiniteDuration = 5 minutes,
    synchronizationTimeout: FiniteDuration = 30 seconds
)

object FinishedDeploymentsStatusesSynchronizationConfig {

  val ConfigPath = "finishedDeploymentStatusesSynchronization"

  def parse(config: Config): FinishedDeploymentsStatusesSynchronizationConfig =
    config
      .getAs[FinishedDeploymentsStatusesSynchronizationConfig](ConfigPath)
      .getOrElse(FinishedDeploymentsStatusesSynchronizationConfig())

}
