package pl.touk.nussknacker.engine

import com.typesafe.config.{Config, ConfigException}
import pl.touk.nussknacker.engine.ProcessingTypeConfig.{DeploymentManagerType, LimitsConfig}
import pl.touk.nussknacker.engine.ProcessingTypeConfig.LimitsConfig.ActiveScenariosLimit
import pl.touk.nussknacker.engine.deployment.EngineSetupName

import scala.util.{Failure, Success, Try}

case class ProcessingTypeConfig(
    deploymentManagerType: DeploymentManagerType,
    engineSetupName: Option[EngineSetupName],
    classPath: List[String],
    deploymentConfig: Config,
    modelConfig: ConfigWithUnresolvedVersion,
    category: String,
    limits: LimitsConfig
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
      readLimitsConfig(config.resolved)
    )
  }

  private def readLimitsConfig(config: Config) = {
    Try(config.getConfig("limits")) match {
      case Success(limitsConfig) =>
        LimitsConfig(
          limitsConfig.getAs[Int]("activeScenariosLimit").map(ActiveScenariosLimit.apply)
        )
      case Failure(_: ConfigException.Missing) =>
        LimitsConfig.default
      case Failure(ex) =>
        throw ex
    }
  }

  final case class DeploymentManagerType(value: String) extends AnyVal
  final case class LimitsConfig(activeScenariosLimit: Option[ActiveScenariosLimit])

  object LimitsConfig {
    final case class ActiveScenariosLimit(value: Int) extends AnyVal

    val default = LimitsConfig(activeScenariosLimit = None)
  }

}
