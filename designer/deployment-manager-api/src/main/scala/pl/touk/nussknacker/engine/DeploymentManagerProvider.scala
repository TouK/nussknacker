package pl.touk.nussknacker.engine

import akka.actor.ActorSystem
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.MetaDataInitializer.MetadataType
import pl.touk.nussknacker.engine.api.component.AdditionalPropertyConfig
import pl.touk.nussknacker.engine.api.deployment.{DeploymentManager, ProcessingTypeDeploymentService}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.{MetaData, NamedServiceProvider, ProcessAdditionalFields}
import sttp.client3.SttpBackend

import scala.concurrent.{ExecutionContext, Future}

// If you are adding a new DeploymentManagerProvider available in the public distribution, please remember
// to add it's type to UsageStatisticsHtmlSnippet.knownDeploymentManagerTypes
trait DeploymentManagerProvider extends NamedServiceProvider {

  def createDeploymentManager(modelData: BaseModelData, config: Config)
                             (implicit ec: ExecutionContext, actorSystem: ActorSystem,
                              sttpBackend: SttpBackend[Future, Any],
                              deploymentService: ProcessingTypeDeploymentService): DeploymentManager

  def metaDataInitializer(config: Config): MetaDataInitializer

  def additionalPropertiesConfig(config: Config): Map[String, AdditionalPropertyConfig] = Map.empty

  def additionalValidators(config: Config): List[CustomProcessValidator] = Nil

}

/**
 * This class contains the logic of overriding defaults set through the standard mechanism - defaultValue field in
 * AdditionalPropertyConfig. These initial values have to be overwritten because some initial values cannot be statically
 * defined (like slug in request-response).
 * This currently also requires the DeploymentManagerProvider to provide its metaDataType.
 * TODO: set the defaults in one place without overriding
 */
final case class MetaDataInitializer(metadataType: MetadataType,
                                     overrideDefaultProperties: ProcessName => Map[String, String] = _ => Map.empty) {
  def create(name: ProcessName, initialProperties: Map[String, String]): MetaData =
    MetaData(name.value, ProcessAdditionalFields(None, initialProperties ++ overrideDefaultProperties(name), metadataType))
}

object MetaDataInitializer {
  type MetadataType = String
  def apply(metadataType: MetadataType, overridingProperties: Map[String, String]): MetaDataInitializer = MetaDataInitializer(metadataType, _ => overridingProperties)
}
