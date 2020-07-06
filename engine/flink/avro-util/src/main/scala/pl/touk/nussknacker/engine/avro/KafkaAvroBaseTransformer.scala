package pl.touk.nussknacker.engine.avro

import cats.Id
import cats.data.Validated.{Invalid, Valid}
import cats.data.{Writer, WriterT}
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, NodeId}
import pl.touk.nussknacker.engine.api.context.transformation.{NodeDependencyValue, SingleInputGenericNodeTransformation, TypedNodeDependencyValue}
import pl.touk.nussknacker.engine.api.definition.{FixedExpressionValue, FixedValuesParameterEditor, Parameter}
import pl.touk.nussknacker.engine.api.namespaces.{KafkaUsageKey, NamingContext}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.api.typed.CustomNodeValidationException
import pl.touk.nussknacker.engine.avro.schemaregistry.{SchemaRegistryClient, SchemaRegistryKafkaAvroProvider, SchemaRegistryProvider}
import pl.touk.nussknacker.engine.kafka.KafkaConfig

import scala.reflect.ClassTag

trait KafkaAvroBaseTransformer[T, Y] extends SingleInputGenericNodeTransformation[T] {

  type WithError[V] = Writer[List[ProcessCompilationError], V]

  def schemaRegistryProvider: SchemaRegistryProvider[Y]

  def processObjectDependencies: ProcessObjectDependencies

  @transient protected lazy val schemaRegistryClient: SchemaRegistryClient = schemaRegistryProvider.createSchemaRegistryClient

  protected val kafkaConfig: KafkaConfig = KafkaConfig.parseProcessObjectDependencies(processObjectDependencies)

  override type State = Nothing

  protected def topicParam(implicit nodeId: NodeId): WithError[Parameter] = {
    val topics = schemaRegistryClient.getAllTopics

    (topics match {
      case Valid(topics) => Writer[List[ProcessCompilationError], List[String]](Nil, topics)
      case Invalid(e) => Writer[List[ProcessCompilationError], List[String]](List(CustomNodeError(e.getMessage, Some(KafkaAvroFactory.TopicParamName))), Nil)
    }).map { topics =>
      Parameter[String](KafkaAvroFactory.TopicParamName).copy(editor = Some(FixedValuesParameterEditor(
        topics
          .flatMap(topic => processObjectDependencies.objectNaming.decodeName(topic, processObjectDependencies.config, new NamingContext(KafkaUsageKey)))
          .sorted
          .map(v => FixedExpressionValue(s"'$v'", v))
      )))
    }
  }

  protected def versionParam(topic: String)(implicit nodeId: NodeId): WithError[Parameter] = {
    val versions = schemaRegistryClient.getAllVersions(topic, isKey = false)
    (versions match {
      case Valid(versions) => Writer[List[ProcessCompilationError], List[Integer]](Nil, versions)
      case Invalid(e) => Writer[List[ProcessCompilationError], List[Integer]](List(CustomNodeError(e.getMessage, Some(KafkaAvroFactory.TopicParamName))), Nil)
    }).map(versionParam)
  }

  protected def versionParam(versions: List[Integer]): Parameter = {
    val versionValues = FixedExpressionValue("", "Latest version") :: versions.sorted.map(v => FixedExpressionValue(v.toString, v.toString))
    Parameter[java.lang.Integer](KafkaAvroFactory.SchemaVersionParamName).copy(editor = Some(FixedValuesParameterEditor(versionValues)), validators = Nil)
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

  protected def fetchSchema(topic: String, version: Option[Int])(implicit nodeId: NodeId): WriterT[Id, List[ProcessCompilationError], Option[Schema]] = {
    schemaRegistryProvider.createSchemaRegistryClient.getSchema(topic, version, isKey = false) match {
      case Valid(schema) => Writer[List[ProcessCompilationError], Option[Schema]](Nil, Some(schema))
      case Invalid(e) => Writer[List[ProcessCompilationError], Option[Schema]](List(CustomNodeError(e.getMessage, Some(KafkaAvroFactory.SchemaVersionParamName))), None)
    }
  }

  //edge case - for some reason Topic is not defined
  protected val fallbackVersionParam: NextParameters = NextParameters(List(versionParam(Nil)), Nil, None)

}
