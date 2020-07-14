package pl.touk.nussknacker.engine.avro

import cats.Id
import cats.data.Validated.{Invalid, Valid}
import cats.data.{Writer, WriterT}
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, NodeId}
import pl.touk.nussknacker.engine.api.context.transformation.{NodeDependencyValue, SingleInputGenericNodeTransformation, TypedNodeDependencyValue}
import pl.touk.nussknacker.engine.api.definition.{FixedExpressionValue, FixedValuesParameterEditor, Parameter}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.api.typed.CustomNodeValidationException
import pl.touk.nussknacker.engine.avro.schemaregistry.{BasedOnVersionAvroSchemaDeterminer, SchemaRegistryClient, SchemaRegistryProvider}
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaUtils, PreparedKafkaTopic}

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
      case Invalid(e) => Writer[List[ProcessCompilationError], List[String]](List(CustomNodeError(e.getMessage, Some(KafkaAvroBaseTransformer.TopicParamName))), Nil)
    }).map { topics =>
      Parameter[String](KafkaAvroBaseTransformer.TopicParamName).copy(editor = Some(FixedValuesParameterEditor(
        topics
          .flatMap(topic => processObjectDependencies.objectNaming.decodeName(topic, processObjectDependencies.config, KafkaUtils.KafkaTopicUsageKey))
          .sorted
          .map(v => FixedExpressionValue(s"'$v'", v))
      )))
    }
  }

  protected def versionParam(preparedTopic: PreparedKafkaTopic)(implicit nodeId: NodeId): WithError[Parameter] = {
    val versions = schemaRegistryClient.getAllVersions(preparedTopic.prepared, isKey = false)
    (versions match {
      case Valid(versions) => Writer[List[ProcessCompilationError], List[Integer]](Nil, versions)
      case Invalid(e) => Writer[List[ProcessCompilationError], List[Integer]](List(CustomNodeError(e.getMessage, Some(KafkaAvroBaseTransformer.TopicParamName))), Nil)
    }).map(versionParam)
  }

  protected def versionParam(versions: List[Integer]): Parameter = {
    val versionValues = FixedExpressionValue("", "Latest version") :: versions.sorted.map(v => FixedExpressionValue(v.toString, v.toString))
    Parameter[java.lang.Integer](KafkaAvroBaseTransformer.SchemaVersionParamName).copy(editor = Some(FixedValuesParameterEditor(versionValues)), validators = Nil)
  }

  protected def typedDependency[C:ClassTag](list: List[NodeDependencyValue]): C = list.collectFirst {
    case TypedNodeDependencyValue(value:C) => value
  }.getOrElse(throw new CustomNodeValidationException(s"No node dependency: ${implicitly[ClassTag[C]].runtimeClass}", None, null))

  protected def extractPreparedTopic(params: Map[String, Any]): PreparedKafkaTopic = prepareTopic(
    params(KafkaAvroBaseTransformer.TopicParamName).asInstanceOf[String]
  )

  protected def extractVersion(params: Map[String, Any]): Option[Int] =
    Option(params(KafkaAvroBaseTransformer.SchemaVersionParamName).asInstanceOf[Integer]).map(_.intValue())

  protected def prepareTopic(topic: String): PreparedKafkaTopic =
    KafkaUtils.prepareKafkaTopic(topic, processObjectDependencies)

  protected def prepareSchemaDeterminer(params: Map[String, Any]): BasedOnVersionAvroSchemaDeterminer = {
    val preparedTopic = extractPreparedTopic(params)
    val version = extractVersion(params)
    prepareSchemaDeterminer(preparedTopic, version)
  }

  protected def prepareSchemaDeterminer(preparedTopic: PreparedKafkaTopic, version: Option[Int]): BasedOnVersionAvroSchemaDeterminer = {
    new BasedOnVersionAvroSchemaDeterminer(schemaRegistryProvider.createSchemaRegistryClient _, preparedTopic.prepared, version)
  }

  protected def fetchSchema(preparedTopic: PreparedKafkaTopic, version: Option[Int])(implicit nodeId: NodeId): WriterT[Id, List[ProcessCompilationError], Option[Schema]] = {
    schemaRegistryProvider.createSchemaRegistryClient.getSchema(preparedTopic.prepared, version, isKey = false) match {
      case Valid(schema) => Writer[List[ProcessCompilationError], Option[Schema]](Nil, Some(schema))
      case Invalid(e) => Writer[List[ProcessCompilationError], Option[Schema]](List(CustomNodeError(e.getMessage, Some(KafkaAvroBaseTransformer.SchemaVersionParamName))), None)
    }
  }

  //edge case - for some reason Topic is not defined
  protected val fallbackVersionParam: NextParameters = NextParameters(List(versionParam(Nil)), Nil, None)

}

object KafkaAvroBaseTransformer {

  final val SchemaVersionParamName = "Schema version"
  final val TopicParamName = "topic"
  final val SinkOutputParamName = "Output"

}