package pl.touk.nussknacker.engine.kafka

import java.util.{Optional, Properties}

import org.apache.flink.streaming.connectors.kafka.{FlinkKafkaProducer, KafkaSerializationSchema}
import org.apache.flink.streaming.connectors.kafka.partitioner.FlinkKafkaPartitioner

object PartitionByKeyFlinkKafkaProducer {

  import scala.collection.JavaConverters._

  def apply[T](kafkaAddress: String,
               topic: String,
               serializationSchema: KafkaSerializationSchema[T],
               kafkaProperties: Option[Map[String, String]] = None): FlinkKafkaProducer[T] = {
    val props = new Properties()
    props.setProperty("bootstrap.servers", kafkaAddress)
    kafkaProperties.map(_.asJava).foreach(props.putAll)
    //we give null as partitioner to use default kafka partition behaviour...
    //default behaviour should partition by key
    //FIXME???
    new FlinkKafkaProducer[T](
      topic, serializationSchema, props, FlinkKafkaProducer.Semantic.AT_LEAST_ONCE
    )
  }

}