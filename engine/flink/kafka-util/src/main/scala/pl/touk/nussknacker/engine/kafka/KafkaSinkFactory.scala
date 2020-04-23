package pl.touk.nussknacker.engine.kafka

import java.nio.charset.StandardCharsets

import javax.validation.constraints.NotBlank
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import org.apache.flink.streaming.connectors.kafka.KafkaSerializationSchema
import pl.touk.nussknacker.engine.api.editor.{DualEditor, DualEditorMode, SimpleEditor, SimpleEditorType}
import pl.touk.nussknacker.engine.api.process.{Sink, SinkFactory}
import pl.touk.nussknacker.engine.api.{MetaData, MethodToInvoke, ParamName}
import pl.touk.nussknacker.engine.flink.api.process.{BasicFlinkSink, FlinkSink}
import pl.touk.nussknacker.engine.kafka.KafkaSinkFactory._
import pl.touk.nussknacker.engine.kafka.serialization.{FixedSerializationSchemaFactory, SerializationSchemaFactory}

class KafkaSinkFactory(config: KafkaConfig,
                       schemaFactory: SerializationSchemaFactory[Any]) extends SinkFactory {

  def this(config: KafkaConfig,
           schema: String => KafkaSerializationSchema[Any]) =
    this(config, FixedSerializationSchemaFactory(schema))

  @MethodToInvoke
  def create(processMetaData: MetaData,
             @ParamName(`TopicParamName`)
             @DualEditor(
               simpleEditor = new SimpleEditor(`type` = SimpleEditorType.STRING_EDITOR),
               defaultMode = DualEditorMode.RAW
             )
             @NotBlank
             topic: String
            )(metaData: MetaData): Sink = {
    val serializationSchema = schemaFactory.create(topic, config)
    new KafkaSink(topic, serializationSchema, s"${metaData.id}-$topic")
  }

  class KafkaSink(topic: String, serializationSchema: KafkaSerializationSchema[Any], clientId: String) extends BasicFlinkSink with Serializable {
    override def toFlinkFunction: SinkFunction[Any] = {
      PartitionByKeyFlinkKafkaProducer(config, topic, serializationSchema, clientId)
    }
    override def testDataOutput: Option[Any => String] = Option(value =>
      new String(serializationSchema.serialize(value, System.currentTimeMillis()).value(), StandardCharsets.UTF_8))
  }

}

object KafkaSinkFactory {

  final val TopicParamName = "topic"

}
