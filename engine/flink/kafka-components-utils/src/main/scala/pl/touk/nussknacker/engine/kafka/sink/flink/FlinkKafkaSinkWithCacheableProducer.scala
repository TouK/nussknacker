package pl.touk.nussknacker.engine.kafka.sink.flink

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.java.ClosureCleaner
import org.apache.flink.configuration.Configuration
import org.apache.flink.runtime.state.{FunctionInitializationContext, FunctionSnapshotContext}
import org.apache.flink.streaming.api.checkpoint.CheckpointedFunction
import org.apache.flink.streaming.api.functions.sink.{RichSinkFunction, SinkFunction}
import org.apache.flink.streaming.connectors.kafka.{FlinkKafkaErrorCode, FlinkKafkaException, KafkaSerializationSchema}
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.kafka.KafkaProducerCreator
import pl.touk.nussknacker.engine.kafka.sharedproducer.{LoggingSharedKafkaProducer, LoggingSharedKafkaProducerHolder}
import pl.touk.nussknacker.engine.util.SynchronousExecutionContext

import scala.concurrent.ExecutionContext

class FlinkKafkaSinkWithCacheableProducer[IN](kafkaProducerCreator: KafkaProducerCreator.Binary,
                                              metaData: MetaData,
                                              kafkaSerializationSchema: KafkaSerializationSchema[IN])
  extends RichSinkFunction[IN] with CheckpointedFunction with LazyLogging {
  // Here we use raw LoggingSharedKafkaProducer instead of KafkaSharedProducer because we need an access to flush and close.
  private var cachedProducer: LoggingSharedKafkaProducer = _

  @transient
  @volatile private var asyncException: Exception = _

  ClosureCleaner.clean(kafkaSerializationSchema, ExecutionConfig.ClosureCleanerLevel.RECURSIVE, true)

  override def snapshotState(context: FunctionSnapshotContext): Unit = {
    logger.debug(s"Flushing kafka producer on checkpoint")
    cachedProducer.flush()
    propagateAsyncExceptionIfNeeded()
  }

  override def initializeState(context: FunctionInitializationContext): Unit = {}

  override def invoke(value: IN, context: SinkFunction.Context): Unit = {
    implicit val ec: ExecutionContext = SynchronousExecutionContext.ctx
    logger.trace(s"Invoke called on kafka sink with element: $value")
    propagateAsyncExceptionIfNeeded()
    val record = kafkaSerializationSchema.serialize(value, context.timestamp())
    cachedProducer.sendToKafka(record)
  }

  override def open(parameters: Configuration): Unit = {
    cachedProducer = LoggingSharedKafkaProducerHolder.retrieveService(kafkaProducerCreator)(metaData)
  }

  override def close(): Unit = {
    if (cachedProducer != null) {
      logger.debug(s"Flushing kafka producer on sink close")
      cachedProducer.flush()
      cachedProducer.close()
    }
    propagateAsyncExceptionIfNeeded()
  }

  private def propagateAsyncExceptionIfNeeded(): Unit = {
    val e = asyncException
    if (e != null) {
      asyncException = null
      logger.error("Async exception occurred", e)
      throw new FlinkKafkaException(
        FlinkKafkaErrorCode.EXTERNAL_ERROR,
        "Failed to send data to Kafka: " + e.getMessage,
        e
      )
    }
  }
}
