package pl.touk.nussknacker.engine.avro.kryo

import org.apache.kafka.common.annotation.InterfaceStability.Evolving
import pl.touk.nussknacker.engine.kafka.KafkaConfig

object KryoGenericRecordSchemaIdSerializationSupport {

  @Evolving // default behaviour will be switched to true in some future
  def schemaIdSerializationEnabled(kafkaConfig: KafkaConfig): Boolean =
    Option(kafkaConfig)
      .flatMap(_.avroKryoGenericRecordSchemaIdSerialization)
      .getOrElse(false)

}
