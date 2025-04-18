package pl.touk.nussknacker.engine

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.ProcessingTypeConfig.{ActiveScenariosLimit, DeploymentManagerType}
import pl.touk.nussknacker.engine.deployment.EngineSetupName

case class ProcessingTypeConfig(
    deploymentManagerType: DeploymentManagerType,
    engineSetupName: Option[EngineSetupName],
    classPath: List[String],
    deploymentConfig: Config,
    modelConfig: ConfigWithUnresolvedVersion,
    category: String,
    activeScenariosLimit: Option[ActiveScenariosLimit]
)

object ProcessingTypeConfig {

  import net.ceedubs.ficus.Ficus._

  def read(config: ConfigWithUnresolvedVersion): ProcessingTypeConfig = {
    ProcessingTypeConfig(
      DeploymentManagerType(config.resolved.getString("deploymentConfig.type")),
      config.resolved.getAs[EngineSetupName]("deploymentConfig.engineSetupName"),
      config.resolved.as[List[String]]("modelConfig.classPath"),
      config.resolved.getConfig("deploymentConfig"),
      config.getConfig("modelConfig"),
      config.resolved.as[String]("category"),
      config.resolved.getAs[Int]("activeScenariosLimit").map(ActiveScenariosLimit.apply),
    )
  }

  final case class DeploymentManagerType(value: String) extends AnyVal
  final case class ActiveScenariosLimit(value: Int)     extends AnyVal

}
