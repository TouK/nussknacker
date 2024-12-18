package pl.touk.nussknacker.engine.common.periodic.service

import com.typesafe.config.{Config, ConfigFactory}
import pl.touk.nussknacker.engine.api.deployment.periodic.model.PeriodicProcessDeployment
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.management.periodic.model.DeploymentWithJarData.WithCanonicalProcess
import pl.touk.nussknacker.engine.common.periodic.service.ProcessConfigEnricher.{
  DeployData,
  EnrichedProcessConfig,
  InitialScheduleData
}
import pl.touk.nussknacker.engine.modelconfig.InputConfigDuringExecution
import sttp.client3.SttpBackend

import scala.concurrent.{ExecutionContext, Future}

/**
 * Use to enrich scenario config e.g. with a schedule name that can be later exposed as a global variable.
 * Other use case could be fetching some data specific for a scenario to be used later when starting job on Flink cluster.
 *
 * Please note config to enrich is not final scenario config. This config is only passed to Flink during deployment.
 * Consult [[pl.touk.nussknacker.engine.modelconfig.InputConfigDuringExecution]] and
 * [[pl.touk.nussknacker.engine.modelconfig.ModelConfigLoader]] for details.
 *
 * Config enriched on initial schedule is passed to onDeploy method.
 */
trait ProcessConfigEnricher {
  def onInitialSchedule(initialScheduleData: InitialScheduleData): Future[EnrichedProcessConfig]
  def onDeploy(deployData: DeployData): Future[EnrichedProcessConfig]
}

object ProcessConfigEnricher {

  trait ProcessConfigEnricherInputData {
    def inputConfigDuringExecutionJson: String

    def inputConfigDuringExecution: Config = {
      ConfigFactory.parseString(inputConfigDuringExecutionJson)
    }

  }

  case class InitialScheduleData(inputConfigDuringExecutionJson: String) extends ProcessConfigEnricherInputData

  case class DeployData(
      inputConfigDuringExecutionJson: String,
      deployment: PeriodicProcessDeployment
  ) extends ProcessConfigEnricherInputData

  case class EnrichedProcessConfig(inputConfigDuringExecutionJson: String)

  object EnrichedProcessConfig {

    def apply(config: Config): EnrichedProcessConfig = {
      EnrichedProcessConfig(InputConfigDuringExecution.serialize(config))
    }

  }

  def identity: ProcessConfigEnricher = new ProcessConfigEnricher {
    override def onInitialSchedule(initialScheduleData: InitialScheduleData): Future[EnrichedProcessConfig] =
      Future.successful(EnrichedProcessConfig(initialScheduleData.inputConfigDuringExecutionJson))

    override def onDeploy(deployData: DeployData): Future[EnrichedProcessConfig] =
      Future.successful(EnrichedProcessConfig(deployData.inputConfigDuringExecutionJson))
  }

}

trait ProcessConfigEnricherFactory {
  def apply(config: Config)(implicit backend: SttpBackend[Future, Any], ec: ExecutionContext): ProcessConfigEnricher
}

object ProcessConfigEnricherFactory {

  def noOp: ProcessConfigEnricherFactory = new ProcessConfigEnricherFactory {

    override def apply(
        config: Config
    )(implicit backend: SttpBackend[Future, Any], ec: ExecutionContext): ProcessConfigEnricher = {
      ProcessConfigEnricher.identity
    }

  }

}
