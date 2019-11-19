package pl.touk.nussknacker.engine.kafka

import java.nio.charset.StandardCharsets

import org.apache.flink.streaming.api.functions.sink.SinkFunction
import org.apache.flink.streaming.util.serialization.KeyedSerializationSchema
import pl.touk.nussknacker.engine.api.process.{Sink, SinkFactory}
import pl.touk.nussknacker.engine.api.{MetaData, MethodToInvoke, ParamName}
import pl.touk.nussknacker.engine.flink.api.process.FlinkSink
import pl.touk.nussknacker.engine.kafka.KafkaSinkFactory._
import pl.touk.nussknacker.engine.kafka.serialization.{FixedSerializationSchemaFactory, SerializationSchemaFactory}

class KafkaSinkFactory(config: KafkaConfig,
                       schemaFactory: SerializationSchemaFactory[Any]) extends SinkFactory {

  def this(config: KafkaConfig,
           schema: KeyedSerializationSchema[Any]) =
    this(config, FixedSerializationSchemaFactory(schema))

  @MethodToInvoke
  def create(processMetaData: MetaData, @ParamName(`TopicParamName`) topic: String)(metaData: MetaData): Sink = {
    val serializationSchema = schemaFactory.create(topic, config)
    new KafkaSink(topic, serializationSchema, s"${metaData.id}-$topic")
  }

  class KafkaSink(topic: String,
                  serializationSchema: KeyedSerializationSchema[Any], clientId: String) extends FlinkSink with Serializable {
    override def toFlinkFunction: SinkFunction[Any] = {
      PartitionByKeyFlinkKafkaProducer011(config.kafkaAddress, topic, serializationSchema, clientId, config.kafkaProperties)
    }
    override def testDataOutput: Option[Any => String] = Option(value => new String(serializationSchema.serializeValue(value), StandardCharsets.UTF_8))
  }

}

object KafkaSinkFactory {

  final val TopicParamName = "topic"

}
