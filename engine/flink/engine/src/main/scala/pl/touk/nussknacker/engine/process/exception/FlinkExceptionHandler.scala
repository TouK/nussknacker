package pl.touk.nussknacker.engine.process.exception

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus.{booleanValueReader, optionValueReader, stringValueReader, toFicusConfig}
import org.apache.flink.api.common.restartstrategy.RestartStrategies
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.api.{Context, MetaData, ProcessListener}
import pl.touk.nussknacker.engine.flink.api.exception.{ExceptionHandler, FlinkEspExceptionConsumer, FlinkEspExceptionConsumerProvider, RateMeterExceptionConsumer}
import pl.touk.nussknacker.engine.process.exception.FlinkExceptionHandler.{exceptionHandlerConfigPath, typeConfigPath, withRateMeterPath}
import pl.touk.nussknacker.engine.util.exception.WithExceptionExtractor
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader

import scala.util.control.NonFatal

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
                            processObjectDependencies: ProcessObjectDependencies,
                            listeners: Seq[ProcessListener],
                            classLoader: ClassLoader) extends ExceptionHandler with WithExceptionExtractor {

  def restartStrategy: RestartStrategies.RestartStrategyConfiguration =
    RestartStrategyFromConfiguration.readFromConfiguration(processObjectDependencies.config, metaData)

  protected val consumer: FlinkEspExceptionConsumer = {
    val baseConfig = processObjectDependencies.config.getConfig(exceptionHandlerConfigPath)
    val baseConsumer: FlinkEspExceptionConsumer  = extractBaseConsumer(baseConfig)
    if (baseConfig.getAs[Boolean](withRateMeterPath).getOrElse(true)) {
      new RateMeterExceptionConsumer(baseConsumer)
    } else {
      baseConsumer
    }
  }

  def handle(exceptionInfo: NuExceptionInfo[_ <: Throwable]): Unit = {
    listeners.foreach(_.exceptionThrown(exceptionInfo))
    consumer.consume(extractOrThrow(exceptionInfo))
  }

  override def handling[T](nodeId: Option[String], context: Context)(action: => T): Option[T] =
    try {
      Some(action)
    } catch {
      case NonFatal(e) => handle(NuExceptionInfo(nodeId, e, context))
        None
    }

  private def extractBaseConsumer(baseConfig: Config): FlinkEspExceptionConsumer = {
    val providerName = baseConfig.as[String](typeConfigPath)
    ScalaServiceLoader.loadNamed[FlinkEspExceptionConsumerProvider](providerName, classLoader).create(metaData, baseConfig)
  }

  override def open(context: EngineRuntimeContext): Unit = {
    consumer.open(context)
  }

  override def close(): Unit = {
    consumer.close()
  }
}