package pl.touk.nussknacker.engine.flink.util.exception

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus.{booleanValueReader, mapValueReader, optionValueReader, stringValueReader, toFicusConfig}
import net.ceedubs.ficus.readers.EnumerationReader.enumerationValueReader
import org.apache.flink.api.common.restartstrategy.RestartStrategies
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.flink.api.exception.{FlinkEspExceptionConsumer, FlinkEspExceptionHandler}
import pl.touk.nussknacker.engine.flink.util.exception.ConfigurableExceptionHandler._

object ConfigurableExceptionHandlerFactory {

  def apply(processObjectDependencies: ProcessObjectDependencies): ExceptionHandlerFactory =
    ExceptionHandlerFactory.noParams(new ConfigurableExceptionHandler(_, processObjectDependencies))

}

/*
  exceptionHandler {
    type: BrieflyLogging
    withRateMeter: true
    params {
      toAdd1: value1
    }
  }
 */
object ConfigurableExceptionHandler {

  val exceptionHandlerConfigPath = "exceptionHandler"
  val typeConfigPath = "type"
  val withRateMeterPath = "withRateMeter"

  val paramsConfigPath = "params"
  object ConsumerType extends Enumeration {
    val BrieflyLogging, VerboselyLogging = Value
  }

}

class ConfigurableExceptionHandler(metaData: MetaData,
                                   processObjectDependencies: ProcessObjectDependencies) extends FlinkEspExceptionHandler with ConsumingNonTransientExceptions {

  override def restartStrategy: RestartStrategies.RestartStrategyConfiguration =
    RestartStrategyFromConfiguration.readFromConfiguration(processObjectDependencies.config, metaData)

  override protected val consumer: FlinkEspExceptionConsumer = {
    val baseConfig = processObjectDependencies.config.getConfig(exceptionHandlerConfigPath)
    val baseConsumer: FlinkEspExceptionConsumer  = extractBaseConsumer(baseConfig)
    if (baseConfig.getAs[Boolean](withRateMeterPath).getOrElse(true)) {
      new RateMeterExceptionConsumer(baseConsumer)
    } else {
      baseConsumer
    }
  }

  //TODO: ServiceLoader mechanism?
  private def extractBaseConsumer(baseConfig: Config): FlinkEspExceptionConsumer = {
    val params = baseConfig.getAs[Map[String, String]](paramsConfigPath).getOrElse(Map.empty)
    baseConfig.as[ConfigurableExceptionHandler.ConsumerType.Value](typeConfigPath) match {
      case ConsumerType.BrieflyLogging => BrieflyLoggingExceptionConsumer(metaData, params)
      case ConsumerType.VerboselyLogging => VerboselyLoggingExceptionConsumer(metaData, params)
    }
  }
}
