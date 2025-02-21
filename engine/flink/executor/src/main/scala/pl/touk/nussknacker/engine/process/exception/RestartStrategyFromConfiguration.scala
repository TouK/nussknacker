package pl.touk.nussknacker.engine.process.exception

import com.github.ghik.silencer.silent
import com.typesafe.config.{Config, ConfigValue, ConfigValueType}
import org.apache.commons.lang3.StringUtils
import org.apache.flink.api.common.restartstrategy.RestartStrategies
import org.apache.flink.api.common.restartstrategy.RestartStrategies.RestartStrategyConfiguration
import org.apache.flink.configuration.Configuration
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.util.MetaDataExtractor
import pl.touk.nussknacker.engine.util.config.CustomFicusInstances._

import scala.compat.java8.OptionConverters.RichOptionalGeneric
import scala.jdk.CollectionConverters._

object RestartStrategyFromConfiguration {

  val restartStrategyConfigPath = "restartStrategy"

  val scenarioPropertyPath = "scenarioProperty"

  val defaultStrategyPath = "default"

  val strategyPath = "strategy"

  private val restartStrategyFlinkConfigPrefix = "restart-strategy"

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
  def readFromConfiguration(config: Config, metaData: MetaData): RestartStrategyConfiguration = {
    val restartConfig = config.getConfig(restartStrategyConfigPath)
    val restartConfigName = (for {
      property <- restartConfig.getAs[String](scenarioPropertyPath)
      value    <- MetaDataExtractor.extractProperty(metaData, property)
      if StringUtils.isNotBlank(value)
    } yield value).getOrElse(defaultStrategyPath)
    readFromConfig(restartConfig.getConfig(restartConfigName))
  }

  // We convert HOCON to Flink configuration, so that we can use Flink parsing mechanisms
  // https://ci.apache.org/projects/flink/flink-docs-release-1.13/docs/dev/execution/task_failure_recovery/
  @silent("deprecated")
  private def readFromConfig(config: Config): RestartStrategyConfiguration = {
    val flinkConfig = new Configuration
    // restart-strategy.fixed-delay.attempts
    val strategy = config.getString(strategyPath)
    flinkConfig.setString(s"$restartStrategyFlinkConfigPrefix.type", strategy)
    config.entrySet().asScala.foreach { entry =>
      flinkConfig.setString(s"$restartStrategyFlinkConfigPrefix.$strategy.${entry.getKey}", toString(entry.getValue))
    }
    RestartStrategies
      .fromConfiguration(flinkConfig)
      .asScala
      .getOrElse(throw new IllegalArgumentException(s"Failed to find configured restart strategy: $strategy"))
  }

  private def toString(configValue: ConfigValue): String =
    if (configValue.valueType() == ConfigValueType.STRING)
      configValue.unwrapped().toString
    else
      configValue.render()

}
