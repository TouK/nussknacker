package pl.touk.nussknacker.engine.flink.util.exception

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import net.ceedubs.ficus.Ficus.{mapValueReader, optionValueReader, stringValueReader, toFicusConfig}
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.exception.{NuExceptionInfo, NonTransientException}
import pl.touk.nussknacker.engine.flink.api.exception.{FlinkEspExceptionConsumer, FlinkEspExceptionConsumerProvider}
import pl.touk.nussknacker.engine.util.logging.LazyLoggingWithTraces


case class VerboselyLoggingExceptionConsumer(processMetaData: MetaData, params: Map[String, String] = Map.empty)
  extends FlinkEspExceptionConsumer
    with LazyLogging {
  override def consume(e: NuExceptionInfo[NonTransientException]): Unit = {
    logger.error(s"${processMetaData.id}: Exception during processing job, params: $params, context: ${e.context}", e.throwable)
  }
}

case class BrieflyLoggingExceptionConsumer(processMetaData: MetaData, params: Map[String, String] = Map.empty)
  extends FlinkEspExceptionConsumer
    with LazyLoggingWithTraces {
  override def consume(e: NuExceptionInfo[NonTransientException]): Unit = {
    warnWithDebugStack(s"${processMetaData.id}: Exception: ${e.throwable.getMessage} (${e.throwable.getClass.getName}), params: $params", e.throwable)
  }
}

class VerboselyLoggingExceptionConsumerProvider extends FlinkEspExceptionConsumerProvider {
  override def create(metaData: MetaData, additionalConfig: Config): FlinkEspExceptionConsumer =
    VerboselyLoggingExceptionConsumer(metaData, additionalConfig.getAs[Map[String, String]]("params").getOrElse(Map.empty))

  override val name: String = "VerboselyLogging"
}

class BrieflyLoggingExceptionConsumerProvider extends FlinkEspExceptionConsumerProvider {
  override def create(metaData: MetaData, additionalConfig: Config): FlinkEspExceptionConsumer =
    BrieflyLoggingExceptionConsumer(metaData, additionalConfig.getAs[Map[String, String]]("params").getOrElse(Map.empty))

  override val name: String = "BrieflyLogging"

}