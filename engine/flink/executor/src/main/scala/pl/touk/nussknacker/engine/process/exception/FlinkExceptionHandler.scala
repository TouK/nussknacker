package pl.touk.nussknacker.engine.process.exception

import com.github.ghik.silencer.silent
import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus.{booleanValueReader, optionValueReader, stringValueReader, toFicusConfig}
import org.apache.flink.api.common.restartstrategy.RestartStrategies
import pl.touk.nussknacker.engine.api.component.NodeComponentInfo
import pl.touk.nussknacker.engine.api.exception.{NuExceptionInfo, WithExceptionExtractor}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.api.{Context, MetaData, ProcessListener}
import pl.touk.nussknacker.engine.flink.api.exception.{
  ExceptionHandler,
  FlinkEspExceptionConsumer,
  FlinkEspExceptionConsumerProvider
}
import pl.touk.nussknacker.engine.process.exception.FlinkExceptionHandler.{
  exceptionHandlerConfigPath,
  extractorConfigPath,
  typeConfigPath,
  withRateMeterConfigPath
}
import pl.touk.nussknacker.engine.util.exception.DefaultWithExceptionExtractor
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
  val typeConfigPath             = "type"
  val withRateMeterConfigPath    = "withRateMeter"
  val extractorConfigPath        = "exceptionExtractor"

}

class FlinkExceptionHandler(
    metaData: MetaData,
    modelDependencies: ProcessObjectDependencies,
    listeners: Seq[ProcessListener],
    classLoader: ClassLoader
) extends ExceptionHandler {

  @silent("deprecated")
  def restartStrategy: RestartStrategies.RestartStrategyConfiguration =
    RestartStrategyFromConfiguration.readFromConfiguration(modelDependencies.config, metaData)

  private val baseConfig = modelDependencies.config.getConfig(exceptionHandlerConfigPath)

  protected val consumer: FlinkEspExceptionConsumer = {
    val baseConsumer: FlinkEspExceptionConsumer = extractBaseConsumer(baseConfig)
    if (baseConfig.getAs[Boolean](withRateMeterConfigPath).getOrElse(true)) {
      new RateMeterExceptionConsumer(baseConsumer)
    } else {
      baseConsumer
    }
  }

  protected val extractor: WithExceptionExtractor = extractExceptionExtractor(baseConfig)

  def handle(exceptionInfo: NuExceptionInfo[_ <: Throwable]): Unit = {
    listeners.foreach(_.exceptionThrown(exceptionInfo))
    consumer.consume(extractor.extractOrThrow(exceptionInfo))
  }

  override def handling[T](nodeComponentInfo: Option[NodeComponentInfo], context: Context)(
      action: => T
  ): Option[T] =
    try {
      Some(action)
    } catch {
      case NonFatal(e) =>
        handle(NuExceptionInfo(nodeComponentInfo, e, context))
        None
    }

  private def extractBaseConsumer(baseConfig: Config): FlinkEspExceptionConsumer = {
    val providerName = baseConfig.as[String](typeConfigPath)
    ScalaServiceLoader
      .loadNamed[FlinkEspExceptionConsumerProvider](providerName, classLoader)
      .create(metaData, baseConfig)
  }

  private def extractExceptionExtractor(baseConfig: Config): WithExceptionExtractor = {
    val extractorName = baseConfig.getOrElse[String](extractorConfigPath, DefaultWithExceptionExtractor.name)
    ScalaServiceLoader.loadNamed[WithExceptionExtractor](extractorName, classLoader)
  }

  override def open(context: EngineRuntimeContext): Unit = {
    consumer.open(context)
  }

  override def close(): Unit = {
    consumer.close()
  }

}
