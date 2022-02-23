package pl.touk.nussknacker.engine.kafka

import com.github.ghik.silencer.silent
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer
import pl.touk.nussknacker.engine.kafka.KafkaUtils.withPropertiesFromConfig
import pl.touk.nussknacker.engine.kafka.serialization.FlinkSerializationSchemaConversions.wrapToFlinkSerializationSchema

import java.util.Properties
import scala.annotation.nowarn

@silent("deprecated")
@nowarn("cat=deprecation")
object PartitionByKeyFlinkKafkaProducer {

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
    new FlinkKafkaProducer[T](topic, wrapToFlinkSerializationSchema(serializationSchema), props, semantic)
  }

}
