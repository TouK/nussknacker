package pl.touk.nussknacker.engine.flink.util.exception

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import net.ceedubs.ficus.Ficus.{mapValueReader, optionValueReader, stringValueReader, toFicusConfig}
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.exception.{NonTransientException, NuExceptionInfo}
import pl.touk.nussknacker.engine.flink.api.exception.{FlinkEspExceptionConsumer, FlinkEspExceptionConsumerProvider}
import pl.touk.nussknacker.engine.util.logging.LazyLoggingWithTraces

case class VerboselyLoggingExceptionConsumer(processMetaData: MetaData, params: Map[String, String] = Map.empty)
    extends FlinkEspExceptionConsumer
    with LazyLogging {

  override def consume(e: NuExceptionInfo[NonTransientException]): Unit = {
    logger.error(
      s"${processMetaData.name}: Exception during processing job, params: $params, context: ${e.context}",
      e.throwable
    )
  }

}

case class BrieflyLoggingExceptionConsumer(processMetaData: MetaData, params: Map[String, String] = Map.empty)
    extends FlinkEspExceptionConsumer
    with LazyLoggingWithTraces {

  override def consume(e: NuExceptionInfo[NonTransientException]): Unit = {
    logger.warnWithDebugStack(
      s"${processMetaData.name}: Exception: ${e.throwable.getMessage} (${e.throwable.getClass.getName}), params: $params",
      e.throwable
    )
  }

}

class VerboselyLoggingExceptionConsumerProvider extends FlinkEspExceptionConsumerProvider {

  override def create(metaData: MetaData, exceptionHandlerConfig: Config): FlinkEspExceptionConsumer =
    VerboselyLoggingExceptionConsumer(
      metaData,
      exceptionHandlerConfig.getAs[Map[String, String]]("params").getOrElse(Map.empty)
    )

  override val name: String = "VerboselyLogging"
}

class BrieflyLoggingExceptionConsumerProvider extends FlinkEspExceptionConsumerProvider {

  override def create(metaData: MetaData, exceptionHandlerConfig: Config): FlinkEspExceptionConsumer =
    BrieflyLoggingExceptionConsumer(
      metaData,
      exceptionHandlerConfig.getAs[Map[String, String]]("params").getOrElse(Map.empty)
    )

  override val name: String = "BrieflyLogging"

}
