package pl.touk.nussknacker.engine.avro.source

import java.nio.charset.StandardCharsets
import com.typesafe.config.ConfigFactory
import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => CSchemaRegistryClient}
import org.apache.avro.Schema
import org.apache.avro.generic.{GenericData, GenericRecord}
import org.scalatest.Assertion
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, NodeId}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.TypedNodeDependencyValue
import pl.touk.nussknacker.engine.api.process.{Source, TestDataGenerator}
import pl.touk.nussknacker.engine.api.typed.ReturningType
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, Unknown}
import pl.touk.nussknacker.engine.api.{MetaData, StreamMetaData, VariableConstants}
import pl.touk.nussknacker.engine.avro.KafkaAvroBaseTransformer.{SchemaVersionParamName, TopicParamName}
import pl.touk.nussknacker.engine.avro.helpers.KafkaAvroSpecMixin
import pl.touk.nussknacker.engine.avro.schema.{FullNameV1, FullNameV2}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentSchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client._
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.ConfluentAvroSerializationSchemaFactory
import pl.touk.nussknacker.engine.avro.schemaregistry.{ExistingSchemaVersion, LatestSchemaVersion, SchemaVersionOption}
import pl.touk.nussknacker.engine.avro.typed.AvroSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.avro.{KafkaAvroBaseTransformer, SchemaDeterminerError}
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.compile.nodecompilation.{GenericNodeTransformationValidator, TransformationResult}
import pl.touk.nussknacker.engine.flink.api.process.FlinkSourceTestSupport
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator

import scala.reflect.ClassTag

class KafkaAvroSourceFactorySpec extends KafkaAvroSpecMixin with KafkaAvroSourceSpecMixin {

  import KafkaAvroSourceMockSchemaRegistry._

  override protected def schemaRegistryClient: CSchemaRegistryClient = schemaRegistryMockClient

  override protected def confluentClientFactory: ConfluentSchemaRegistryClientFactory = factory

  test("should read generated record in v1") {
    val givenObj = FullNameV1.createRecord("Jan", "Kowalski")

    roundTripSingleObject(avroSourceFactory, RecordTopic, ExistingSchemaVersion(1), givenObj, FullNameV1.schema)
  }

  test("should read generated record in v2") {
    val givenObj = FullNameV2.createRecord("Jan", "Maria", "Kowalski")

    roundTripSingleObject(avroSourceFactory, RecordTopic, ExistingSchemaVersion(2), givenObj, FullNameV2.schema)
  }

  test("should read generated record in last version") {
    val givenObj = FullNameV2.createRecord("Jan", "Maria", "Kowalski")

    roundTripSingleObject(avroSourceFactory, RecordTopic, LatestSchemaVersion, givenObj, FullNameV2.schema)
  }

  test("should throw exception when schema doesn't exist") {
    val givenObj = FullNameV2.createRecord("Jan", "Maria", "Kowalski")

    assertThrowsWithParent[SchemaDeterminerError] {
      readLastMessageAndVerify(avroSourceFactory, "fake-topic", ExistingSchemaVersion(1), givenObj, FullNameV2.schema)
    }
  }

  test("should throw exception when schema version doesn't exist") {
    val givenObj = FullNameV2.createRecord("Jan", "Maria", "Kowalski")

    assertThrowsWithParent[SchemaDeterminerError] {
      readLastMessageAndVerify(avroSourceFactory, RecordTopic, ExistingSchemaVersion(3), givenObj, FullNameV2.schema)
    }
  }

  test("should read last generated simple object") {
    val givenObj = 123123

    roundTripSingleObject(avroSourceFactory, IntTopic, ExistingSchemaVersion(1), givenObj, IntSchema)
  }

  test("should read object with invalid defaults") {
    val givenObj = new GenericData.Record(InvalidDefaultsSchema)
    givenObj.put("field1", "foo")

    roundTripSingleObject(avroSourceFactory, InvalidDefaultsTopic, ExistingSchemaVersion(1), givenObj, InvalidDefaultsSchema)
  }

  test("should read last generated key-value object") {
    val sourceFactory = createKeyValueAvroSourceFactory[Int, Int]
    val givenObj = (123, 345)

    val serializedKey = keySerializer.serialize(IntTopic, givenObj._1)
    val serializedValue = valueSerializer.serialize(IntTopic, givenObj._2)
    kafkaClient.sendRawMessage(IntTopic, serializedKey, serializedValue, Some(0))

    readLastMessageAndVerify(sourceFactory, IntTopic, ExistingSchemaVersion(1), givenObj, IntSchema)
  }

  test("Should validate specific version") {
    val result = validate(TopicParamName -> s"'${KafkaAvroSourceMockSchemaRegistry.RecordTopic}'",
      SchemaVersionParamName -> "'1'")

    result.errors shouldBe Nil
  }

  test("Should validate latest version") {
    val result = validate(TopicParamName -> s"'${KafkaAvroSourceMockSchemaRegistry.RecordTopic}'",
      SchemaVersionParamName -> s"'${SchemaVersionOption.LatestOptionName}'")

    result.errors shouldBe Nil
  }

  test("Should return sane error on invalid topic") {
    val result = validate(TopicParamName -> "'terefere'", SchemaVersionParamName -> s"'${SchemaVersionOption.LatestOptionName}'")

    result.errors shouldBe
      CustomNodeError("id", "Schema subject doesn't exist.", Some(TopicParamName)) ::
      CustomNodeError("id", "Fetching schema error for topic: terefere, version: LatestSchemaVersion", Some(SchemaVersionParamName)) :: Nil
    result.outputContext shouldBe ValidationContext(Map(VariableConstants.InputVariableName -> Unknown))
  }

  test("Should return sane error on invalid version") {
    val result = validate(TopicParamName -> s"'${KafkaAvroSourceMockSchemaRegistry.RecordTopic}'",
      SchemaVersionParamName -> "'12345'")

    result.errors shouldBe CustomNodeError("id", "Fetching schema error for topic: testAvroRecordTopic1, version: ExistingSchemaVersion(12345)", Some(SchemaVersionParamName)) :: Nil
    result.outputContext shouldBe ValidationContext(Map(VariableConstants.InputVariableName -> Unknown))
  }

  test("Should properly detect input type") {
    val result = validate(TopicParamName -> s"'${KafkaAvroSourceMockSchemaRegistry.RecordTopic}'",
      SchemaVersionParamName -> s"'${SchemaVersionOption.LatestOptionName}'")

    result.errors shouldBe Nil
    result.outputContext shouldBe ValidationContext(Map(VariableConstants.InputVariableName -> TypedObjectTypingResult(
      Map(
        "first" -> Typed[CharSequence],
        "middle" -> Typed[CharSequence],
        "last" -> Typed[CharSequence]
      ), Typed.typedClass[GenericRecord]
    )))
  }

  private def validate(params: (String, Expression)*): TransformationResult = {

    val modelData = LocalModelData(ConfigFactory.empty(), new EmptyProcessConfigCreator)

    val validator = new GenericNodeTransformationValidator(ExpressionCompiler.withoutOptimization(modelData),
      modelData.processWithObjectsDefinition.expressionConfig)

    implicit val meta: MetaData = MetaData("processId", StreamMetaData())
    implicit val nodeId: NodeId = NodeId("id")
    val paramsList = params.toList.map(p => Parameter(p._1, p._2))
    validator.validateNode(avroSourceFactory, paramsList, Nil, Some(VariableConstants.InputVariableName))(ValidationContext()).toOption.get
  }

  private def createKeyValueAvroSourceFactory[K: ClassTag, V: ClassTag]: KafkaAvroSourceFactory[Any] = {
    val deserializerFactory = new TupleAvroKeyValueKafkaAvroDeserializerSchemaFactory[K, V](factory)
    val provider = new ConfluentSchemaRegistryProvider(
      factory,
      new ConfluentAvroSerializationSchemaFactory(factory),
      deserializerFactory,
      kafkaConfig,
      formatKey = true)
    new KafkaAvroSourceFactory(provider, testProcessObjectDependencies, None)
  }

  private def roundTripSingleObject(sourceFactory: KafkaAvroSourceFactory[Any], topic: String, versionOption: SchemaVersionOption, givenObj: Any, expectedSchema: Schema) = {
    pushMessage(givenObj, topic)
    readLastMessageAndVerify(sourceFactory, topic, versionOption, givenObj, expectedSchema)
  }

  private def readLastMessageAndVerify(sourceFactory: KafkaAvroSourceFactory[Any], topic: String, versionOption: SchemaVersionOption, givenObj: Any, expectedSchema: Schema): Assertion = {
    val source = createAndVerifySource(sourceFactory, topic, versionOption, expectedSchema)

    val bytes = source.generateTestData(1)
    info("test object: " + new String(bytes, StandardCharsets.UTF_8))
    val deserializedObj = source.testDataParser.parseTestData(bytes)

    deserializedObj shouldEqual List(givenObj)
  }

  private def createAndVerifySource(sourceFactory: KafkaAvroSourceFactory[Any], topic: String, versionOption: SchemaVersionOption, expectedSchema: Schema): Source[AnyRef] with FlinkSourceTestSupport[AnyRef] with TestDataGenerator with ReturningType = {
    val version = versionOption match {
      case LatestSchemaVersion => SchemaVersionOption.LatestOptionName
      case ExistingSchemaVersion(version) => version.toString
    }
    val source = sourceFactory
      .implementation(Map(KafkaAvroBaseTransformer.TopicParamName -> topic, KafkaAvroBaseTransformer.SchemaVersionParamName -> version),
        List(TypedNodeDependencyValue(metaData), TypedNodeDependencyValue(nodeId)), None)
      .asInstanceOf[Source[AnyRef] with TestDataGenerator with FlinkSourceTestSupport[AnyRef] with ReturningType]

    source.returnType shouldEqual AvroSchemaTypeDefinitionExtractor.typeDefinition(expectedSchema)

    source
  }
}
