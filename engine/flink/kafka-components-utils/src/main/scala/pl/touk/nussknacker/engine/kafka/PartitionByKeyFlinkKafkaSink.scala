package pl.touk.nussknacker.engine.kafka

import org.apache.flink.api.connector.sink2.Sink
import org.apache.flink.connector.base.DeliveryGuarantee
import org.apache.flink.connector.kafka.sink.KafkaSink
import org.apache.kafka.clients.producer.ProducerConfig
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.kafka.serialization.FlinkSerializationSchemaConversions

object PartitionByKeyFlinkKafkaSink {

  def apply[T](
      kafkaComponentsConfig: KafkaComponentsConfig,
      serializationSchema: serialization.KafkaSerializationSchema[T],
      clientId: String,
      nodeId: NodeId
  ): Sink[T] = {
    val props = KafkaUtils.toProducerProperties(kafkaComponentsConfig, clientId, includeSerializerClassConfig = false)
    // we set default to 10min, KafkaSink defaults to 1 hour
    props.putIfAbsent(ProducerConfig.TRANSACTION_TIMEOUT_CONFIG, "600000")

    val sinkBuilder = KafkaSink
      .builder[T]
      .setKafkaProducerConfig(props)
      .setRecordSerializer(FlinkSerializationSchemaConversions.wrapToFlinkSerializationSchema(serializationSchema))

    kafkaComponentsConfig.sinkDeliveryGuarantee match {
      case Some(value) =>
        value match {
          case SinkDeliveryGuarantee.ExactlyOnce =>
            sinkBuilder
              .setDeliveryGuarantee(DeliveryGuarantee.EXACTLY_ONCE)
              .setTransactionalIdPrefix(s"$clientId-${nodeId.value}")
          case SinkDeliveryGuarantee.AtLeastOnce =>
            sinkBuilder.setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE)
          case SinkDeliveryGuarantee.None =>
            sinkBuilder.setDeliveryGuarantee(DeliveryGuarantee.NONE)
        }
      // AT_LEAST_ONCE is our default (Flink defaults to NONE)
      case None => sinkBuilder.setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE)
    }

    sinkBuilder.build()
  }

}
