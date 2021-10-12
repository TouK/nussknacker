package pl.touk.nussknacker.engine.kafka

import com.github.ghik.silencer.silent
import org.apache.flink.streaming.connectors.kafka.{FlinkKafkaProducer, KafkaSerializationSchema}
import org.apache.kafka.clients.producer.ProducerRecord
import pl.touk.nussknacker.engine.kafka.KafkaUtils.withPropertiesFromConfig

import java.lang
import java.util.Properties
import scala.annotation.nowarn

@silent("deprecated")
@nowarn("cat=deprecation")
object PartitionByKeyFlinkKafkaProducer {

  def wrap[T](serializationSchema: serialization.KafkaSerializationSchema[T]) = new KafkaSerializationSchema[T] {
    override def serialize(element: T, timestamp: lang.Long): ProducerRecord[Array[Byte], Array[Byte]] = serializationSchema.serialize(element, timestamp)
  }

  def apply[T](config: KafkaConfig,
               topic: String,
               serializationSchema: serialization.KafkaSerializationSchema[T],
               clientId: String,
               semantic: FlinkKafkaProducer.Semantic = FlinkKafkaProducer.Semantic.AT_LEAST_ONCE): FlinkKafkaProducer[T] = {
    val props = new Properties()
    props.setProperty("bootstrap.servers", config.kafkaAddress)
    props.setProperty("client.id", clientId)
    //we set default to 10min, as FlinkKafkaProducer logs warn if not set
    props.setProperty("transaction.timeout.ms", "600000")
    withPropertiesFromConfig(props, config)
    new FlinkKafkaProducer[T](topic, wrap(serializationSchema), props, semantic)
  }

}
