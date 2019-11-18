package pl.touk.nussknacker.engine.kafka

import java.util.{Optional, Properties}

import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer011
import org.apache.flink.streaming.connectors.kafka.partitioner.FlinkKafkaPartitioner
import org.apache.flink.streaming.util.serialization.KeyedSerializationSchema

object PartitionByKeyFlinkKafkaProducer011 {

  import scala.collection.JavaConverters._

  def apply[T](kafkaAddress: String,
               topic: String,
               serializationSchema: KeyedSerializationSchema[T],
               clientId: String,
               kafkaProperties: Option[Map[String, String]] = None): FlinkKafkaProducer011[T] = {
    val props = new Properties()
    props.setProperty("bootstrap.servers", kafkaAddress)
    KafkaEspUtils.setClientId(props, clientId)
    kafkaProperties.map(_.asJava).foreach(props.putAll)
    //we give null as partitioner to use default kafka partition behaviour...
    //default behaviour should partition by key
    new FlinkKafkaProducer011[T](
      topic, serializationSchema, props, Optional.empty(): Optional[FlinkKafkaPartitioner[T]]
    )
  }

}