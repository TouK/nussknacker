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
    val unwrapped = unwrap(e.throwable)
    logger.error(
      s"${processMetaData.name} > ${e.nodeComponentInfo}: Exception during processing job, params: $params, context: ${e.context}",
      unwrapped
    )
  }

}

case class BrieflyLoggingExceptionConsumer(processMetaData: MetaData, params: Map[String, String] = Map.empty)
    extends FlinkEspExceptionConsumer
    with LazyLoggingWithTraces {

  override def consume(e: NuExceptionInfo[NonTransientException]): Unit = {
    val unwrapped = unwrap(e.throwable)
    logger.warnWithDebugStack(
      s"${processMetaData.name} > ${e.nodeComponentInfo.getOrElse("<missing nodeComponentInfo>")} > ${e.context.id}: " +
        s"${unwrapped.toString}${Option(unwrapped.getCause).map(", caused by: " + _).getOrElse("")}, params: $params",
      unwrapped
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
