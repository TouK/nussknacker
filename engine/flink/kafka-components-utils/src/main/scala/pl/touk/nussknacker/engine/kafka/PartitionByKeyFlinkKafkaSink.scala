package pl.touk.nussknacker.engine.kafka

import org.apache.flink.api.connector.sink2.Sink
import org.apache.flink.connector.base.DeliveryGuarantee
import org.apache.flink.connector.kafka.sink.KafkaSink
import org.apache.kafka.clients.producer.ProducerConfig
import pl.touk.nussknacker.engine.kafka.serialization.FlinkSerializationSchemaConversions

object PartitionByKeyFlinkKafkaSink {

  def apply[T](
      kafkaComponentsConfig: KafkaComponentsConfig,
      serializationSchema: serialization.KafkaSerializationSchema[T],
      clientId: String
  ): Sink[T] = {
    val props = KafkaUtils.toProducerProperties(kafkaComponentsConfig, clientId)
    // we set default to 10min, as FlinkKafkaProducer logs warn if not set
    props.putIfAbsent(ProducerConfig.TRANSACTION_TIMEOUT_CONFIG, "600000")
    val deliveryGuarantee = kafkaComponentsConfig.sinkDeliveryGuarantee match {
      case Some(value) =>
        value match {
          case SinkDeliveryGuarantee.ExactlyOnce => DeliveryGuarantee.EXACTLY_ONCE
          case SinkDeliveryGuarantee.AtLeastOnce => DeliveryGuarantee.AT_LEAST_ONCE
          case SinkDeliveryGuarantee.None        => DeliveryGuarantee.NONE
        }
      // AT_LEAST_ONCE is default
      case None => DeliveryGuarantee.AT_LEAST_ONCE
    }
    KafkaSink
      .builder[T]
      .setKafkaProducerConfig(props)
      .setRecordSerializer(FlinkSerializationSchemaConversions.wrapToFlinkSerializationSchema(serializationSchema))
      .setDeliveryGuarantee(deliveryGuarantee)
      .build()
  }

}
