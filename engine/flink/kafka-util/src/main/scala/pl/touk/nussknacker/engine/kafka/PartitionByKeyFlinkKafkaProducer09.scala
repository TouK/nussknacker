package pl.touk.nussknacker.engine.kafka

import java.util.Properties

import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer09
import org.apache.flink.streaming.connectors.kafka.partitioner.FlinkKafkaPartitioner
import org.apache.flink.streaming.util.serialization.KeyedSerializationSchema

object PartitionByKeyFlinkKafkaProducer09 {

  import scala.collection.JavaConverters._

  def apply[T](kafkaAddress: String,
               topic: String,
               serializationSchema: KeyedSerializationSchema[T],
               kafkaProperties: Option[Map[String, String]] = None): FlinkKafkaProducer09[T] = {
    val props = new Properties()
    props.setProperty("bootstrap.servers", kafkaAddress)
    kafkaProperties.map(_.asJava).foreach(props.putAll)
    //we give null as partitioner to use default kafka partition behaviour...
    //default behaviour should partition by key
    new FlinkKafkaProducer09[T](
      topic, serializationSchema, props, null: FlinkKafkaPartitioner[T]
    )
  }

}