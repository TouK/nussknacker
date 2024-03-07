package pl.touk.nussknacker.engine

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.deployment.EngineSetupName

case class ProcessingTypeConfig(
    deploymentManagerType: String,
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
    ProcessingTypeConfig(
      config.resolved.getString("deploymentConfig.type"),
      config.resolved.getAs[String]("deploymentConfig.engineSetupName").map(EngineSetupName(_)),
      config.resolved.as[List[String]]("modelConfig.classPath"),
      config.resolved.getConfig("deploymentConfig"),
      config.getConfig("modelConfig"),
      config.resolved.as[String]("category")
    )
  }

}
