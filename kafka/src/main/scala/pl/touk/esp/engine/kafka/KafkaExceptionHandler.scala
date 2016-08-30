package pl.touk.esp.engine.kafka

import java.util.Properties

import org.apache.flink.api.common.functions.RuntimeContext
import org.apache.flink.streaming.util.serialization.SerializationSchema
import org.apache.kafka.clients.producer.internals.ErrorLoggingCallback
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import org.apache.kafka.common.serialization.ByteArraySerializer
import pl.touk.esp.engine.api.{EspExceptionHandler, EspExceptionInfo}
import scala.collection.JavaConversions._

class KafkaExceptionHandler(kafkaAddress: String,
                            topicChoice: EspExceptionInfo=>String,
                            serializationSchema: SerializationSchema[EspExceptionInfo],
                            properties: Properties = new Properties()) extends EspExceptionHandler {
  lazy val producer = new KafkaProducer[Array[Byte], Array[Byte]](propertiesForKafka())

  override def open(runtimeContext: RuntimeContext): Unit = {

  }

  override def handle(exceptionInfo: EspExceptionInfo): Unit = {
    val toSend = serializationSchema.serialize(exceptionInfo)
    val topic = topicChoice(exceptionInfo)
    val logAsString = true
    val errorLoggingCallback = new ErrorLoggingCallback(topic, Array.empty, toSend, logAsString)
    producer.send(new ProducerRecord(topic, toSend), errorLoggingCallback)
  }

  override def close(): Unit = {
    producer.close()
  }

  private def propertiesForKafka(): Properties = {
    val props: Properties = new Properties
    props.setProperty("bootstrap.servers", kafkaAddress)
    props.setProperty("key.serializer", classOf[ByteArraySerializer].getCanonicalName)
    props.setProperty("value.serializer", classOf[ByteArraySerializer].getCanonicalName)
    props.setProperty("acks", "all")
    props.setProperty("retries", "0")
    props.setProperty("batch.size", "16384")
    props.setProperty("linger.ms", "1")
    props.setProperty("buffer.memory", "33554432")
    properties.toMap.foreach { case (k, v) =>
      props.setProperty(k, v)
    }
    props
  }
}
