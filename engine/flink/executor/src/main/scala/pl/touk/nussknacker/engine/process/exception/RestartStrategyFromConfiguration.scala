package pl.touk.nussknacker.engine.process.exception

import com.typesafe.config.{Config, ConfigValue, ConfigValueType}
import org.apache.commons.lang3.StringUtils
import org.apache.flink.configuration.{Configuration, RestartStrategyOptions}
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.util.MetaDataExtractor
import pl.touk.nussknacker.engine.util.config.CustomFicusInstances._

import scala.jdk.CollectionConverters._

object RestartStrategyFromConfiguration {

  val restartStrategyConfigPath = "restartStrategy"

  val scenarioPropertyPath = "scenarioProperty"

  val defaultStrategyPath = "default"

  val strategyPath = "strategy"

  /*
    restartStrategy {
      //if scenarioProperty is configured, strategy name will be used from this category: restartType = for-important, etc.
      //probably scenarioProperty should be configured with FixedValuesEditor
      scenarioProperty: restartType. For simple cases one needs to configure only default strategy
      default: {
        strategy: fixed-delay
        attempts: 10
      }
      for-important {
        strategy: fixed-delay
        attempts: 30
      }
      for-very-important {
        strategy: fixed-delay
        attempts: 50
      }
    }
   */
  def readFromConfiguration(config: Config, metaData: MetaData): Configuration = {
    val restartConfig = config.getConfig(restartStrategyConfigPath)
    val restartConfigName = (for {
      property <- restartConfig.getAs[String](scenarioPropertyPath)
      value    <- MetaDataExtractor.extractProperty(metaData, property)
      if StringUtils.isNotBlank(value)
    } yield value).getOrElse(defaultStrategyPath)
    readFromConfig(restartConfig.getConfig(restartConfigName))
  }

  private def readFromConfig(config: Config): Configuration = {
    val flinkConfig = new Configuration
    val strategy    = config.getString(strategyPath)
    flinkConfig.setString(s"${RestartStrategyOptions.RESTART_STRATEGY.key()}", strategy)
    config.entrySet().asScala.filter(e => e.getKey != strategyPath).foreach { entry =>
      // for example: restart-strategy.fixed-delay.attempts
      flinkConfig.setString(
        s"${RestartStrategyOptions.RESTART_STRATEGY_CONFIG_PREFIX}.$strategy.${entry.getKey}",
        toString(entry.getValue)
      )
    }
    flinkConfig
  }

  private def toString(configValue: ConfigValue): String =
    if (configValue.valueType() == ConfigValueType.STRING)
      configValue.unwrapped().toString
    else
      configValue.render()

}
