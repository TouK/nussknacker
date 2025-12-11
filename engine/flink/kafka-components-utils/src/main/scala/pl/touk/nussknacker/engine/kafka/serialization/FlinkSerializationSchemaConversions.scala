package pl.touk.nussknacker.engine.kafka.serialization

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema
import org.apache.flink.streaming.connectors.kafka
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import pl.touk.nussknacker.engine.api.{Context, NodeId}
import pl.touk.nussknacker.engine.api.component.{ComponentType, NodeComponentInfo}
import pl.touk.nussknacker.engine.api.runtimecontext.ContextIdGenerator
import pl.touk.nussknacker.engine.flink.api.exception.ExceptionHandler
import pl.touk.nussknacker.engine.kafka.serialization

import java.lang
import scala.annotation.nowarn

object FlinkSerializationSchemaConversions extends LazyLogging {

  def wrapToFlinkDeserializationSchema[T](
      deserializationSchema: serialization.KafkaDeserializationSchema[T],
      typeInformation: TypeInformation[T]
  ): FlinkDeserializationSchemaWrapper[T] =
    new FlinkDeserializationSchemaWrapper[T](deserializationSchema, typeInformation)

  @nowarn("cat=deprecation")
  class FlinkDeserializationSchemaWrapper[T](
      deserializationSchema: serialization.KafkaDeserializationSchema[T],
      typeInformation: TypeInformation[T]
  ) extends kafka.KafkaDeserializationSchema[T] {

    private var exceptionHandlingData: (ExceptionHandler, ContextIdGenerator, NodeId) = _

    // We pass exception handler from SourceFunction instead of init it in open because KafkaDeserializationSchema has no close() method
    private[kafka] def setExceptionHandlingData(
        exceptionHandler: ExceptionHandler,
        contextIdGenerator: ContextIdGenerator,
        nodeId: NodeId
    ): Unit = {
      this.exceptionHandlingData = (exceptionHandler, contextIdGenerator, nodeId)
    }

    override def getProducedType: TypeInformation[T] = typeInformation

    override def isEndOfStream(nextElement: T): Boolean = deserializationSchema.isEndOfStream(nextElement)

    override def deserialize(record: ConsumerRecord[Array[Byte], Array[Byte]]): T = {
      require(
        exceptionHandlingData != null,
        "exceptionHandlingData is null - FlinkDeserializationSchemaWrapper not opened correctly"
      )
      val (exceptionHandler, contextIdGenerator, nodeId) = exceptionHandlingData
      exceptionHandler
        .handling(
          Some(NodeComponentInfo(nodeId, ComponentType.Source, "unknown")),
          Context(contextIdGenerator.nextContextId())
        ) {
          deserializationSchema.deserialize(record)
        }
        .getOrElse(
          null.asInstanceOf[T]
        ) // null is not passed to collector in KafkaDeserializationSchema.deserialize // TODO: add ensurance that Null<:<T and use orNull instead
    }

  }

  def wrapToFlinkSerializationSchema[T](
      serializationSchema: serialization.KafkaSerializationSchema[T]
  ): KafkaRecordSerializationSchema[T] =
    (element: T, _: KafkaRecordSerializationSchema.KafkaSinkContext, timestamp: lang.Long) =>
      serializationSchema.serialize(element, timestamp)

}
