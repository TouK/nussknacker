package pl.touk.nussknacker.engine

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.deployment.EngineSetupName

case class ProcessingTypeConfig(
    deploymentManagerType: String,
    supportsPeriodicExecution: Boolean,
    engineSetupName: Option[EngineSetupName],
    classPath: List[String],
    deploymentConfig: Config,
    modelConfig: ConfigWithUnresolvedVersion,
    category: String
)

object ProcessingTypeConfig {

  import net.ceedubs.ficus.Ficus._
  import pl.touk.nussknacker.engine.util.config.FicusReaders._

  def read(config: ConfigWithUnresolvedVersion): ProcessingTypeConfig = {
    val supportsPeriodicExecution = if (config.resolved.hasPath("deploymentConfig.supportsPeriodicExecution")) {
      config.resolved.getBoolean("deploymentConfig.supportsPeriodicExecution")
    } else {
      false
    }
    ProcessingTypeConfig(
      config.resolved.getString("deploymentConfig.type"),
      supportsPeriodicExecution,
      config.resolved.getAs[EngineSetupName]("deploymentConfig.engineSetupName"),
      config.resolved.as[List[String]]("modelConfig.classPath"),
      config.resolved.getConfig("deploymentConfig"),
      config.getConfig("modelConfig"),
      config.resolved.as[String]("category")
    )
  }

}
