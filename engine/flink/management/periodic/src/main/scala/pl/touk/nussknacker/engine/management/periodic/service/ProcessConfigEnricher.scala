package pl.touk.nussknacker.engine.management.periodic.service

import com.typesafe.config.Config
import com.typesafe.sslconfig.util.EnrichedConfig
import pl.touk.nussknacker.engine.api.deployment.DeploymentData
import pl.touk.nussknacker.engine.management.periodic.model.PeriodicProcessDeployment
import pl.touk.nussknacker.engine.management.periodic.service.ProcessConfigEnricher.{DeployData, EnrichedProcessConfig, InitialScheduleData}
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.{ExecutionContext, Future}

/**
 * Use to enrich scenario config e.g. with a schedule name that can be later exposed as a global variable.
 * Other use case could be fetching some data specific for a scenario to be used later when starting job on Flink cluster.
 *
 * Config enriched on initial schedule is passed to onDeploy method.
 */
trait ProcessConfigEnricher {
  def onInitialSchedule(initialScheduleData: InitialScheduleData): Future[EnrichedProcessConfig]
  def onDeploy(deployData: DeployData): Future[EnrichedProcessConfig]
}

object ProcessConfigEnricher {

  case class InitialScheduleData(processJson: String, modelConfigJson: String)

  case class DeployData(processJson: String, modelConfigJson: String, deployment: PeriodicProcessDeployment)

  case class EnrichedProcessConfig(configJson: String)

  def identity: ProcessConfigEnricher = new ProcessConfigEnricher {
    override def onInitialSchedule(initialScheduleData: InitialScheduleData): Future[EnrichedProcessConfig] = Future.successful(EnrichedProcessConfig(initialScheduleData.modelConfigJson))

    override def onDeploy(deployData: DeployData): Future[EnrichedProcessConfig] = Future.successful(EnrichedProcessConfig(deployData.modelConfigJson))
  }
}

trait ProcessConfigEnricherFactory {
  def apply(config: Config)(implicit backend: SttpBackend[Future, Nothing, NothingT], ec: ExecutionContext): ProcessConfigEnricher
}

object ProcessConfigEnricherFactory {
  def noOp: ProcessConfigEnricherFactory = new ProcessConfigEnricherFactory {
    override def apply(config: Config)(implicit backend: SttpBackend[Future, Nothing, NothingT], ec: ExecutionContext): ProcessConfigEnricher = {
      ProcessConfigEnricher.identity
    }
  }
}
