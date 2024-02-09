package pl.touk.nussknacker.engine

import akka.actor.ActorSystem
import cats.data.Validated.valid
import cats.data.ValidatedNel
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.MetaDataInitializer.MetadataType
import pl.touk.nussknacker.engine.api.component.ScenarioPropertyConfig
import pl.touk.nussknacker.engine.api.deployment.{DeploymentManager, ProcessingTypeDeploymentService}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.{MetaData, NamedServiceProvider, ProcessAdditionalFields}
import pl.touk.nussknacker.engine.deployment.EngineSetupName
import pl.touk.nussknacker.engine.util.IdToTitleConverter
import sttp.client3.SttpBackend

import scala.concurrent.{ExecutionContext, Future}

// If you are adding a new DeploymentManagerProvider available in the public distribution, please remember
// to add it's type to UsageStatisticsHtmlSnippet.knownDeploymentManagerTypes
trait DeploymentManagerProvider extends NamedServiceProvider {

  def createDeploymentManager(
      modelData: BaseModelData,
      dependencies: DeploymentManagerDependencies,
      deploymentConfig: Config
  ): ValidatedNel[String, DeploymentManager] =
    // TODO: remove default implementation after removing the legacy method below
    valid(
      createDeploymentManager(modelData, deploymentConfig)(
        dependencies.executionContext,
        dependencies.actorSystem,
        dependencies.sttpBackend,
        dependencies.deploymentService
      )
    )

  // Exceptions returned by this method won't cause designer's exit. Instead, they will be catched and messages will
  // be shown to the user.
  // TODO: This method is deprecated. It will be removed in 1.15 versions. It is not implemented by design, because for
  //       a new DMs it won't be used - would be used version with DeploymentManagerDependencies
  protected def createDeploymentManager(modelData: BaseModelData, config: Config)(
      implicit ec: ExecutionContext,
      actorSystem: ActorSystem,
      sttpBackend: SttpBackend[Future, Any],
      deploymentService: ProcessingTypeDeploymentService
  ): DeploymentManager = ???

  def metaDataInitializer(config: Config): MetaDataInitializer

  def scenarioPropertiesConfig(config: Config): Map[String, ScenarioPropertyConfig] = Map.empty

  def additionalValidators(config: Config): List[CustomProcessValidator] = Nil

  // It is the default name in case if user not specified it in the configuration. We have a default value
  // to have the convention over the code approach.
  def defaultEngineSetupName: EngineSetupName = EngineSetupName(IdToTitleConverter.toTitle(name))

  // This identity is required because we have a simple, linear configuration of scenario types that combines
  // deployment with model and we don't use references to deployment setups.
  // So when smb want to have two the same engines used with two variants of model, he/she has to duplicate deployment
  // configuration.
  // For the backward compatibility it returns unit, but in the real implementation you should
  // rather return here things like url of your execution engine next to it.
  // TODO: remove the default value
  // TODO: replace scenario types by the separate lists of deployments and of models
  def engineSetupIdentity(config: Config): Any = ()

}

/**
 * This class contains the logic of overriding defaults set through the standard mechanism - defaultValue field in
 * ScenarioPropertyConfig. These initial values have to be overwritten because some initial values cannot be statically
 * defined (like slug in request-response).
 * This currently also requires the DeploymentManagerProvider to provide its metaDataType.
 * TODO: set the defaults in one place without overriding
 */
final case class MetaDataInitializer(
    metadataType: MetadataType,
    overrideDefaultProperties: ProcessName => Map[String, String] = _ => Map.empty
) {

  def create(name: ProcessName, initialProperties: Map[String, String]): MetaData =
    MetaData(
      name.value,
      ProcessAdditionalFields(None, initialProperties ++ overrideDefaultProperties(name), metadataType)
    )

}

object MetaDataInitializer {
  type MetadataType = String
  def apply(metadataType: MetadataType, overridingProperties: Map[String, String]): MetaDataInitializer =
    MetaDataInitializer(metadataType, _ => overridingProperties)
}
