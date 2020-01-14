package pl.touk.nussknacker.engine.kafka

import java.util.{Optional, Properties}

import org.apache.flink.streaming.connectors.kafka.{FlinkKafkaProducer, KafkaSerializationSchema}
import org.apache.flink.streaming.connectors.kafka.partitioner.FlinkKafkaPartitioner

object PartitionByKeyFlinkKafkaProducer {

  import scala.collection.JavaConverters._

  def apply[T](kafkaAddress: String,
               topic: String,
               serializationSchema: KafkaSerializationSchema[T],
               clientId: String,
               kafkaProperties: Option[Map[String, String]] = None,
               semantic: FlinkKafkaProducer.Semantic = FlinkKafkaProducer.Semantic.AT_LEAST_ONCE): FlinkKafkaProducer[T] = {
    val props = new Properties()
    props.setProperty("bootstrap.servers", kafkaAddress)
    props.setProperty("client.id", clientId)
    kafkaProperties.map(_.asJava).foreach(props.putAll)
    new FlinkKafkaProducer[T](topic, serializationSchema, props, semantic)
  }

}