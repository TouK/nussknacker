package pl.touk.nussknacker.engine.kafka

import java.nio.charset.StandardCharsets

import javax.validation.constraints.NotBlank
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import org.apache.flink.streaming.connectors.kafka.KafkaSerializationSchema
import pl.touk.nussknacker.engine.api.editor.{DualEditor, DualEditorMode, SimpleEditor, SimpleEditorType}
import pl.touk.nussknacker.engine.api.namespaces.{KafkaUsageKey, NamingContext}
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, Sink, SinkFactory}
import pl.touk.nussknacker.engine.api.{MetaData, MethodToInvoke, ParamName}
import pl.touk.nussknacker.engine.flink.api.process.BasicFlinkSink
import pl.touk.nussknacker.engine.kafka.KafkaSinkFactory._
import pl.touk.nussknacker.engine.kafka.serialization.{FixedSerializationSchemaFactory, SerializationSchemaFactory}

class KafkaSinkFactory(schemaFactory: SerializationSchemaFactory[Any],
                       processObjectDependencies: ProcessObjectDependencies) extends SinkFactory {

  def this(schema: String => KafkaSerializationSchema[Any],
           processObjectDependencies: ProcessObjectDependencies) =
    this(FixedSerializationSchemaFactory(schema), processObjectDependencies)

  val kafkaConfig: KafkaConfig = processObjectDependencies.config.as[KafkaConfig]("kafka")

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
    val preparedTopic = processObjectDependencies.objectNaming.prepareName(topic,
      processObjectDependencies.config,
      new NamingContext(KafkaUsageKey))
    val serializationSchema = schemaFactory.create(preparedTopic, kafkaConfig)
    new KafkaSink(preparedTopic, serializationSchema, s"${metaData.id}-${preparedTopic}")
  }

  class KafkaSink(topic: String, serializationSchema: KafkaSerializationSchema[Any], clientId: String) extends BasicFlinkSink with Serializable {
    override def toFlinkFunction: SinkFunction[Any] = {
      PartitionByKeyFlinkKafkaProducer(kafkaConfig, topic, serializationSchema, clientId)
    }
    override def testDataOutput: Option[Any => String] = Option(value =>
      new String(serializationSchema.serialize(value, System.currentTimeMillis()).value(), StandardCharsets.UTF_8))
  }

}

object KafkaSinkFactory {

  final val TopicParamName = "topic"

}
