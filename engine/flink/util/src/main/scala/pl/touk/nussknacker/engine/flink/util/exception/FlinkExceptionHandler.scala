package pl.touk.nussknacker.engine.flink.util.exception

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus.{booleanValueReader, optionValueReader, stringValueReader, toFicusConfig}
import org.apache.flink.api.common.restartstrategy.RestartStrategies
import pl.touk.nussknacker.engine.api.{MetaData, ProcessListener}
import pl.touk.nussknacker.engine.flink.api.exception.{FlinkEspExceptionConsumer, FlinkEspExceptionConsumerProvider, FlinkEspExceptionHandler}
import pl.touk.nussknacker.engine.flink.util.exception.FlinkExceptionHandler._
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader


/*
  exceptionHandler {
    type: BrieflyLogging
    withRateMeter: true
    toAdd1: value1
  }
 */
object FlinkExceptionHandler {

  val exceptionHandlerConfigPath = "exceptionHandler"
  val typeConfigPath = "type"
  val withRateMeterPath = "withRateMeter"

}

class FlinkExceptionHandler(metaData: MetaData,
                            config: Config,
                            classLoader: ClassLoader,
                            listeners: Seq[ProcessListener]) extends FlinkEspExceptionHandler with ConsumingNonTransientExceptions {

  override def restartStrategy: RestartStrategies.RestartStrategyConfiguration =
    RestartStrategyFromConfiguration.readFromConfiguration(config, metaData)

  override protected val consumer: FlinkEspExceptionConsumer = {
    val baseConfig = config.getConfig(exceptionHandlerConfigPath)
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