package pl.touk.nussknacker.engine.flink.util.exception

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus.{booleanValueReader, optionValueReader, stringValueReader, toFicusConfig}
import org.apache.flink.api.common.restartstrategy.RestartStrategies
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.flink.api.exception.{FlinkEspExceptionConsumer, FlinkEspExceptionConsumerProvider, FlinkEspExceptionHandler}
import pl.touk.nussknacker.engine.flink.util.exception.ConfigurableExceptionHandler._
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader

object ConfigurableExceptionHandlerFactory {

  def apply(processObjectDependencies: ProcessObjectDependencies): ExceptionHandlerFactory =
    ExceptionHandlerFactory.noParams(new ConfigurableExceptionHandler(_, processObjectDependencies, Thread.currentThread().getContextClassLoader))

}

/*
  exceptionHandler {
    type: BrieflyLogging
    withRateMeter: true
    toAdd1: value1
  }
 */
object ConfigurableExceptionHandler {

  val exceptionHandlerConfigPath = "exceptionHandler"
  val typeConfigPath = "type"
  val withRateMeterPath = "withRateMeter"

}

class ConfigurableExceptionHandler(metaData: MetaData,
                                   processObjectDependencies: ProcessObjectDependencies, classLoader: ClassLoader) extends FlinkEspExceptionHandler with ConsumingNonTransientExceptions {

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

  private def extractBaseConsumer(baseConfig: Config): FlinkEspExceptionConsumer = {
    val providerName = baseConfig.as[String](typeConfigPath)
    ScalaServiceLoader.loadNamed[FlinkEspExceptionConsumerProvider](providerName, classLoader).create(metaData, baseConfig)
  }
}