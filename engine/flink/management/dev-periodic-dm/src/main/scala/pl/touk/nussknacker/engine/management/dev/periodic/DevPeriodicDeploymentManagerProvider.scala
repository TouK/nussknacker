package pl.touk.nussknacker.engine.management.dev.periodic

import akka.actor.ActorSystem
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.StreamMetaData
import pl.touk.nussknacker.engine.api.component.AdditionalPropertyConfig
import pl.touk.nussknacker.engine.api.definition.MandatoryParameterValidator
import pl.touk.nussknacker.engine.api.deployment.{DeploymentManager, ProcessingTypeDeploymentService}
import pl.touk.nussknacker.engine.management.FlinkStreamingDeploymentManagerProvider
import pl.touk.nussknacker.engine.management.periodic.PeriodicDeploymentManagerProvider
import pl.touk.nussknacker.engine.management.periodic.cron.CronParameterValidator
import pl.touk.nussknacker.engine.{BaseModelData, DeploymentManagerProvider, TypeSpecificInitialData}
import sttp.client3.SttpBackend

import scala.concurrent.{ExecutionContext, Future}

class DevPeriodicDeploymentManagerProvider extends DeploymentManagerProvider {
  override def createDeploymentManager(modelData: BaseModelData, config: Config)(implicit ec: ExecutionContext,
                                                                                 actorSystem: ActorSystem,
                                                                                 sttpBackend: SttpBackend[Future, Any],
                                                                                 deploymentService: ProcessingTypeDeploymentService): DeploymentManager = {
    // TODO: make possible to use PeriodicDeploymentManagerProvider with non-flink DMs like embedded or lite-k8s
    new PeriodicDeploymentManagerProvider(new FlinkStreamingDeploymentManagerProvider())
      .createDeploymentManager(modelData, config)
  }

  override def typeSpecificInitialData(config: Config): TypeSpecificInitialData =
    TypeSpecificInitialData(StreamMetaData(Some(1)))

  // TODO: move it to PeriodicDeploymentManagerProvider with ability to override
  override def additionalPropertiesConfig(config: Config): Map[String, AdditionalPropertyConfig] = Map(
    "cron" -> AdditionalPropertyConfig(
      defaultValue = None, // TODO: Maybe once a day at 0:00 ?
      editor = None,
      validators = Some(List(MandatoryParameterValidator, CronParameterValidator.delegate)),
      label = None
    )
  )

  override def name: String = "dev-periodic"

}
