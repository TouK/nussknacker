package pl.touk.nussknacker.engine.kafka.serialization

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.serialization.DeserializationSchema
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.typeutils.ResultTypeQueryable
import org.apache.flink.streaming.connectors.kafka
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import pl.touk.nussknacker.engine.api.{Context, NodeId}
import pl.touk.nussknacker.engine.api.component.{ComponentType, NodeComponentInfo}
import pl.touk.nussknacker.engine.api.runtimecontext.ContextIdGenerator
import pl.touk.nussknacker.engine.flink.api.exception.ExceptionHandler
import pl.touk.nussknacker.engine.kafka.serialization

import java.lang
import scala.reflect.classTag

object FlinkSerializationSchemaConversions extends LazyLogging {

  def wrapToFlinkDeserializationSchema[T](
      deserializationSchema: serialization.KafkaDeserializationSchema[T]
  ): FlinkDeserializationSchemaWrapper[T] =
    new FlinkDeserializationSchemaWrapper[T](deserializationSchema)

  class FlinkDeserializationSchemaWrapper[T](deserializationSchema: serialization.KafkaDeserializationSchema[T])
      extends kafka.KafkaDeserializationSchema[T] {

    protected var exceptionHandlingData: (ExceptionHandler, ContextIdGenerator, NodeId) = _

    // We pass exception handler from SourceFunction instead of init it in open because KafkaDeserializationSchema has no close() method
    private[kafka] def setExceptionHandlingData(
        exceptionHandler: ExceptionHandler,
        contextIdGenerator: ContextIdGenerator,
        nodeId: NodeId
    ): Unit = {
      this.exceptionHandlingData = (exceptionHandler, contextIdGenerator, nodeId)
    }

    override def getProducedType: TypeInformation[T] = {
      Option(deserializationSchema)
        .collect { case withProducedType: ResultTypeQueryable[T @unchecked] =>
          withProducedType.getProducedType
        }
        .getOrElse {
          logger.debug(
            s"Used KafkaDeserializationSchema: ${deserializationSchema.getClass} not implementing ResultTypeQueryable - will be used class tag based produced type"
          )
          val clazz = classTag.runtimeClass.asInstanceOf[Class[T]]
          TypeInformation.of(clazz)
        }
    }

    override def isEndOfStream(nextElement: T): Boolean = deserializationSchema.isEndOfStream(nextElement)

    override def deserialize(record: ConsumerRecord[Array[Byte], Array[Byte]]): T = {
      require(
        exceptionHandlingData != null,
        "exceptionHandlingData is null - FlinkDeserializationSchemaWrapper not opened correctly"
      )
      val (exceptionHandler, contextIdGenerator, nodeId) = exceptionHandlingData
      exceptionHandler
        .handling(
          Some(NodeComponentInfo(nodeId.id, ComponentType.Source, "unknown")),
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
  ): kafka.KafkaSerializationSchema[T] = new kafka.KafkaSerializationSchema[T] {
    override def serialize(element: T, timestamp: lang.Long): ProducerRecord[Array[Byte], Array[Byte]] =
      serializationSchema.serialize(element, timestamp)
  }

  def wrapToNuDeserializationSchema[T](deserializationSchema: DeserializationSchema[T]): KafkaDeserializationSchema[T] =
    new KafkaDeserializationSchema[T] with ResultTypeQueryable[T] {
      override def isEndOfStream(nextElement: T): Boolean = deserializationSchema.isEndOfStream(nextElement)

      override def deserialize(record: ConsumerRecord[Array[Byte], Array[Byte]]): T =
        deserializationSchema.deserialize(record.value())

      override def getProducedType: TypeInformation[T] = deserializationSchema.getProducedType
    }

}
