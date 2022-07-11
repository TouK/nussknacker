package pl.touk.nussknacker.engine.kafka

import com.github.ghik.silencer.silent
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer
import pl.touk.nussknacker.engine.kafka.serialization.FlinkSerializationSchemaConversions.wrapToFlinkSerializationSchema

@silent("deprecated")
object PartitionByKeyFlinkKafkaProducer {

  def apply[T](config: KafkaConfig,
               topic: String,
               serializationSchema: serialization.KafkaSerializationSchema[T],
               clientId: String,
               semantic: FlinkKafkaProducer.Semantic = FlinkKafkaProducer.Semantic.AT_LEAST_ONCE): FlinkKafkaProducer[T] = {
    val props = KafkaUtils.toProducerProperties(config, clientId)
    //we set default to 10min, as FlinkKafkaProducer logs warn if not set
    props.setProperty("transaction.timeout.ms", "600000")
    new FlinkKafkaProducer[T](topic, wrapToFlinkSerializationSchema(serializationSchema), props, semantic)
  }

}
