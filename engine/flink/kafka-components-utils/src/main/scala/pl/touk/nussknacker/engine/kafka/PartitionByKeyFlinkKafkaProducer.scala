package pl.touk.nussknacker.engine.kafka

import com.github.ghik.silencer.silent
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import pl.touk.nussknacker.engine.api.process.TopicName
import pl.touk.nussknacker.engine.kafka.serialization.FlinkSerializationSchemaConversions.wrapToFlinkSerializationSchema

@silent("deprecated")
object PartitionByKeyFlinkKafkaProducer {

  def apply[T](
      config: KafkaConfig,
      topic: TopicName.ForSink,
      serializationSchema: serialization.KafkaSerializationSchema[T],
      clientId: String
  ): FlinkKafkaProducer[T] = {
    val props = KafkaUtils.toProducerProperties(config, clientId)
    // we set default to 10min, as FlinkKafkaProducer logs warn if not set
    props.putIfAbsent(ProducerConfig.TRANSACTION_TIMEOUT_CONFIG, "600000")
    val semantic = config.sinkDeliveryGuarantee match {
      case Some(value) =>
        value match {
          case SinkDeliveryGuarantee.ExactlyOnce => FlinkKafkaProducer.Semantic.EXACTLY_ONCE
          case SinkDeliveryGuarantee.AtLeastOnce => FlinkKafkaProducer.Semantic.AT_LEAST_ONCE
          case SinkDeliveryGuarantee.None        => FlinkKafkaProducer.Semantic.NONE
        }
      // AT_LEAST_ONCE is default
      case None => FlinkKafkaProducer.Semantic.AT_LEAST_ONCE
    }
    new FlinkKafkaProducer[T](topic.name, wrapToFlinkSerializationSchema(serializationSchema), props, semantic)
  }

}
