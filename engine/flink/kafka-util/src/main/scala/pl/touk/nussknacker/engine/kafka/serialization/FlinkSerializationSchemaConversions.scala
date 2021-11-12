package pl.touk.nussknacker.engine.kafka.serialization

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.serialization.DeserializationSchema
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.typeutils.ResultTypeQueryable
import org.apache.flink.streaming.connectors.kafka
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import pl.touk.nussknacker.engine.kafka.serialization

import java.lang
import scala.reflect.classTag

object FlinkSerializationSchemaConversions extends LazyLogging {

  def wrapToFlinkDeserializationSchema[T](deserializationSchema: serialization.KafkaDeserializationSchema[T]): kafka.KafkaDeserializationSchema[T] = {
    new kafka.KafkaDeserializationSchema[T] {

      override def getProducedType: TypeInformation[T] = {
        Option(deserializationSchema).collect {
          case withProducedType: ResultTypeQueryable[T@unchecked] => withProducedType.getProducedType
        }.getOrElse {
          logger.debug("Used KafkaDeserializationSchema not implementing ResultTypeQueryable - will be used class tag based produced type")
          val clazz = classTag.runtimeClass.asInstanceOf[Class[T]]
          TypeInformation.of(clazz)
        }
      }

      override def isEndOfStream(nextElement: T): Boolean = deserializationSchema.isEndOfStream(nextElement)

      override def deserialize(record: ConsumerRecord[Array[Byte], Array[Byte]]): T = deserializationSchema.deserialize(record)
    }
  }

  def wrapToFlinkSerializationSchema[T](serializationSchema: serialization.KafkaSerializationSchema[T]): kafka.KafkaSerializationSchema[T] = new kafka.KafkaSerializationSchema[T] {
    override def serialize(element: T, timestamp: lang.Long): ProducerRecord[Array[Byte], Array[Byte]] = serializationSchema.serialize(element, timestamp)
  }

  def wrapToNuDeserializationSchema[T](deserializationSchema: DeserializationSchema[T]): KafkaDeserializationSchema[T] = new KafkaDeserializationSchema[T] with ResultTypeQueryable[T] {
    override def isEndOfStream(nextElement: T): Boolean = deserializationSchema.isEndOfStream(nextElement)

    override def deserialize(record: ConsumerRecord[Array[Byte], Array[Byte]]): T = deserializationSchema.deserialize(record.value())

    override def getProducedType: TypeInformation[T] = deserializationSchema.getProducedType
  }

}
