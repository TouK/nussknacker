package pl.touk.nussknacker.engine.avro

import pl.touk.nussknacker.engine.api.context.transformation.{NodeDependencyValue, SingleInputGenericNodeTransformation, TypedNodeDependencyValue}
import pl.touk.nussknacker.engine.api.definition.{FixedExpressionValue, FixedValuesParameterEditor, Parameter}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.api.typed.CustomNodeValidationException
import pl.touk.nussknacker.engine.avro.schemaregistry.{SchemaRegistryClient, SchemaRegistryKafkaAvroProvider, SchemaRegistryProvider}
import pl.touk.nussknacker.engine.kafka.KafkaConfig

import scala.reflect.ClassTag

trait KafkaAvroBaseTransformer[T, Y] extends SingleInputGenericNodeTransformation[T] {

  def schemaRegistryProvider: SchemaRegistryProvider[Y]

  def processObjectDependencies: ProcessObjectDependencies

  @transient protected lazy val schemaRegistryClient: SchemaRegistryClient = schemaRegistryProvider.createSchemaRegistryClient

  protected val kafkaConfig: KafkaConfig = KafkaConfig.parseProcessObjectDependencies(processObjectDependencies)

  override type State = Nothing

  protected def topicParam: Parameter = Parameter[String](KafkaAvroFactory.TopicParamName).copy(editor = Some(FixedValuesParameterEditor(
    schemaRegistryClient.getAllTopics.getOrElse(Nil).sorted.map(v => FixedExpressionValue(s"'$v'", v))
  )))

  protected def versionParam(topic: String): Parameter = {
    val versions = schemaRegistryClient.getAllVersions(topic, isKey = false).getOrElse(Nil).sorted
    val versionValues = FixedExpressionValue("", "Latest version") :: versions.map(v => FixedExpressionValue(v.toString, v.toString))
    Parameter[Integer](KafkaAvroFactory.SchemaVersionParamName).copy(editor = Some(FixedValuesParameterEditor(versionValues)), validators = Nil
   )
  }

  protected def typedDependency[C:ClassTag](list: List[NodeDependencyValue]): C = list.collectFirst {
    case TypedNodeDependencyValue(value:C) => value
  }.getOrElse(throw new CustomNodeValidationException(s"No node dependency: ${implicitly[ClassTag[C]].runtimeClass}", None, null))

  protected def topic(params: Map[String, Any]): String = params(KafkaAvroFactory.TopicParamName).asInstanceOf[String]

  protected def createSchemaRegistryProvider(params: Map[String, Any]): SchemaRegistryKafkaAvroProvider[Y] = {
    val topicName = topic(params)
    val version = params(KafkaAvroFactory.SchemaVersionParamName).asInstanceOf[Integer]
    createSchemaRegistryProvider(topicName, version)
  }

  protected def createSchemaRegistryProvider(topicName: String, version: Integer): SchemaRegistryKafkaAvroProvider[Y] = {
    SchemaRegistryKafkaAvroProvider(schemaRegistryProvider, kafkaConfig, topicName, version)
  }
}
