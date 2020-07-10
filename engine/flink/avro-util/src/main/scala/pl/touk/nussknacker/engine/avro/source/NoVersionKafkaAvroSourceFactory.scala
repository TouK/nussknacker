package pl.touk.nussknacker.engine.avro.source

import javax.validation.constraints.NotBlank
import org.apache.flink.streaming.api.functions.TimestampAssigner
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.editor.{SimpleEditor, SimpleEditorType}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.api.typed.ReturningType
import pl.touk.nussknacker.engine.api.{MetaData, MethodToInvoke, ParamName}
import pl.touk.nussknacker.engine.avro.schemaregistry.{SchemaRegistryKafkaAvroProvider, SchemaRegistryProvider}
import pl.touk.nussknacker.engine.kafka.KafkaFactory._
import pl.touk.nussknacker.engine.kafka.source.KafkaSource
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaUtils}

import scala.reflect.ClassTag

class NoVersionKafkaAvroSourceFactory[T: ClassTag](schemaRegistryProvider: SchemaRegistryProvider[T], processObjectDependencies: ProcessObjectDependencies, timestampAssigner: Option[TimestampAssigner[T]])
  extends BaseKafkaAvroSourceFactory[T](processObjectDependencies, timestampAssigner) {

  @MethodToInvoke
  def createSource(processMetaData: MetaData,
                   @ParamName(`TopicParamName`) @NotBlank @SimpleEditor(`type` = SimpleEditorType.STRING_EDITOR) topic: String)
                  (implicit nodeId: NodeId): KafkaSource[T] with ReturningType = {
    val kafkaConfig = KafkaConfig.parseProcessObjectDependencies(processObjectDependencies)
    val preparedTopic = KafkaUtils.prepareKafkaTopic(topic, processObjectDependencies)
    val schemaProvider = SchemaRegistryKafkaAvroProvider[T](schemaRegistryProvider, kafkaConfig, preparedTopic.prepared, null)
    createSource(preparedTopic, kafkaConfig, schemaProvider, processMetaData, nodeId)
  }

}