package pl.touk.nussknacker.engine.avro.source

import java.nio.charset.StandardCharsets

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, Validated}
import com.typesafe.config.ConfigFactory
import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => CSchemaRegistryClient}
import org.apache.avro.generic.{GenericData, GenericRecord}
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, NodeId}
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedEagerParameter, DefinedSingleParameter, OutputVariableNameValue, TypedNodeDependencyValue}
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.process.{Source, TestDataGenerator}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, Unknown}
import pl.touk.nussknacker.engine.api.{MetaData, StreamMetaData, VariableConstants}
import pl.touk.nussknacker.engine.avro.KafkaAvroBaseTransformer
import pl.touk.nussknacker.engine.avro.KafkaAvroBaseTransformer.{SchemaVersionParamName, TopicParamName}
import pl.touk.nussknacker.engine.avro.helpers.KafkaAvroSpecMixin
import pl.touk.nussknacker.engine.avro.schema.{AvroStringSettings, FullNameV1, FullNameV2, PaymentV1}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.schemaregistry.{ExistingSchemaVersion, LatestSchemaVersion, SchemaVersionOption}
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.compile.nodecompilation.{GenericNodeTransformationValidator, TransformationResult}
import pl.touk.nussknacker.engine.flink.api.process.FlinkSourceTestSupport
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.kafka.source.InputMeta
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator

import scala.collection.immutable.ListMap

class KafkaAvroSourceFactorySpec extends KafkaAvroSpecMixin with KafkaAvroSourceSpecMixin {

  import KafkaAvroSourceMockSchemaRegistry._

  override protected def schemaRegistryClient: CSchemaRegistryClient = schemaRegistryMockClient

  override protected def confluentClientFactory: ConfluentSchemaRegistryClientFactory = factory

  test("should read generated record in v1") {
    val givenObj = FullNameV1.createRecord("Jan", "Kowalski")

    roundTripValueObject(avroSourceFactory(useStringForKey = true), RecordTopic, ExistingSchemaVersion(1), "", givenObj)
  }

  test("should read generated record in v2") {
    val givenObj = FullNameV2.createRecord("Jan", "Maria", "Kowalski")

    roundTripValueObject(avroSourceFactory(useStringForKey = true), RecordTopic, ExistingSchemaVersion(2), "", givenObj)
  }

  test("should read generated record in last version") {
    val givenObj = FullNameV2.createRecord("Jan", "Maria", "Kowalski")

    roundTripValueObject(avroSourceFactory(useStringForKey = true), RecordTopic, LatestSchemaVersion, "", givenObj)
  }

  test("should return validation errors when schema doesn't exist") {
    val givenObj = FullNameV2.createRecord("Jan", "Maria", "Kowalski")

    readLastMessageAndVerify(avroSourceFactory(useStringForKey = true), "fake-topic", ExistingSchemaVersion(1), "", givenObj) should matchPattern {
      case Invalid(NonEmptyList(CustomNodeError(_, "Fetching schema error for topic: fake-topic, version: ExistingSchemaVersion(1)", _), _)) => ()
    }
  }

  test("should return validation errors when schema version doesn't exist") {
    val givenObj = FullNameV2.createRecord("Jan", "Maria", "Kowalski")

    readLastMessageAndVerify(avroSourceFactory(useStringForKey = true), RecordTopic, ExistingSchemaVersion(3), "", givenObj) should matchPattern {
      case Invalid(NonEmptyList(CustomNodeError(_, "Fetching schema error for topic: testAvroRecordTopic1, version: ExistingSchemaVersion(3)", _), _)) => ()
    }
  }

  test("should read last generated simple object without key schema") {
    val givenObj = 123123

    roundTripValueObject(avroSourceFactory(useStringForKey = true), IntTopicNoKey, ExistingSchemaVersion(1), "", givenObj)
  }

  test("should ignore key schema and empty key value with string-as-key deserialization") {
    val givenObj = 123123

    roundTripValueObject(avroSourceFactory(useStringForKey = true), IntTopicWithKey, ExistingSchemaVersion(1), "", givenObj)
  }

  test("should read last generated simple object with expected key schema and valid key") {
    val givenObj = 123123

    val serializedKey = keySerializer.serialize(IntTopicWithKey, -1)
    val serializedValue = valueSerializer.serialize(IntTopicWithKey, givenObj)
    kafkaClient.sendRawMessage(IntTopicWithKey, serializedKey, serializedValue, Some(0))

    readLastMessageAndVerify(avroSourceFactory(useStringForKey = true), IntTopicWithKey, ExistingSchemaVersion(1), new String(serializedKey, StandardCharsets.UTF_8), givenObj)
  }

  test("should read object with invalid defaults") {
    val givenObj = new GenericData.Record(InvalidDefaultsSchema)
    givenObj.put("field1", "foo")

    roundTripValueObject(avroSourceFactory(useStringForKey = true), InvalidDefaultsTopic, ExistingSchemaVersion(1), "", givenObj)
  }

  test("should read last generated key-value object, simple type") {
    val givenKey = 123
    val givenValue = 456

    val serializedKey = keySerializer.serialize(IntTopicWithKey, givenKey)
    val serializedValue = valueSerializer.serialize(IntTopicWithKey, givenValue)
    kafkaClient.sendRawMessage(IntTopicWithKey, serializedKey, serializedValue, Some(0))

    roundTripKeyValueObject(avroSourceFactory(useStringForKey = false), IntTopicWithKey, ExistingSchemaVersion(1), givenKey, givenValue)
  }

  test("should read last generated key-value object, complex object") {
    val givenKey = FullNameV1.record
    val givenValue = PaymentV1.record

    val serializedKey = keySerializer.serialize(RecordTopicWithKey, givenKey)
    val serializedValue = valueSerializer.serialize(RecordTopicWithKey, givenValue)
    kafkaClient.sendRawMessage(RecordTopicWithKey, serializedKey, serializedValue, Some(0))

    roundTripKeyValueObject(avroSourceFactory(useStringForKey = false), RecordTopicWithKey, ExistingSchemaVersion(1), givenKey, givenValue)
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
    result.outputContext shouldBe ValidationContext(Map(VariableConstants.InputVariableName -> Unknown, VariableConstants.InputMetaVariableName -> InputMeta.withType(Unknown)))
  }

  test("Should return sane error on invalid version") {
    val result = validate(TopicParamName -> s"'${KafkaAvroSourceMockSchemaRegistry.RecordTopic}'",
      SchemaVersionParamName -> "'12345'")

    result.errors shouldBe CustomNodeError("id", "Fetching schema error for topic: testAvroRecordTopic1, version: ExistingSchemaVersion(12345)", Some(SchemaVersionParamName)) :: Nil
    result.outputContext shouldBe ValidationContext(Map(VariableConstants.InputVariableName -> Unknown, VariableConstants.InputMetaVariableName -> InputMeta.withType(Unknown)))
  }

  test("Should properly detect input type") {
    val result = validate(TopicParamName -> s"'${KafkaAvroSourceMockSchemaRegistry.RecordTopic}'",
      SchemaVersionParamName -> s"'${SchemaVersionOption.LatestOptionName}'")

    result.errors shouldBe Nil
    result.outputContext shouldBe ValidationContext(
      Map(
        VariableConstants.InputVariableName -> TypedObjectTypingResult(
          ListMap(
            "first" -> AvroStringSettings.stringTypingResult,
            "middle" -> AvroStringSettings.stringTypingResult,
            "last" -> AvroStringSettings.stringTypingResult
          ),
          Typed.typedClass[GenericRecord]
        ),
        VariableConstants.InputMetaVariableName -> InputMeta.withType(Typed[String])
      )
    )
  }

  private def validate(params: (String, Expression)*): TransformationResult = {

    val modelData = LocalModelData(ConfigFactory.empty(), new EmptyProcessConfigCreator)

    val validator = new GenericNodeTransformationValidator(ExpressionCompiler.withoutOptimization(modelData),
      modelData.processWithObjectsDefinition.expressionConfig)

    implicit val meta: MetaData = MetaData("processId", StreamMetaData())
    implicit val nodeId: NodeId = NodeId("id")
    val paramsList = params.toList.map(p => Parameter(p._1, p._2))
    validator.validateNode(avroSourceFactory(useStringForKey = true), paramsList, Nil, Some(VariableConstants.InputVariableName))(ValidationContext()).toOption.get
  }

  private def roundTripValueObject(sourceFactory: KafkaAvroSourceFactory[Any, Any], topic: String, versionOption: SchemaVersionOption, givenKey: Any, givenValue: Any) = {
    pushMessage(givenValue, topic)
    readLastMessageAndVerify(sourceFactory, topic, versionOption, givenKey, givenValue)
  }

  private def roundTripKeyValueObject(sourceFactory: KafkaAvroSourceFactory[Any, Any], topic: String, versionOption: SchemaVersionOption, givenKey: Any, givenValue: Any) = {
    pushMessageWithKey(givenKey, givenValue, topic)
    readLastMessageAndVerify(sourceFactory, topic, versionOption, givenKey, givenValue)
  }

  private def readLastMessageAndVerify(sourceFactory: KafkaAvroSourceFactory[Any, Any], topic: String, versionOption: SchemaVersionOption, givenKey: Any, givenValue: Any) = {
    createValidatedSource(sourceFactory, topic, versionOption)
      .map(source => {
        val bytes = source.generateTestData(1)
        info("test object: " + new String(bytes, StandardCharsets.UTF_8))
        val deserializedObj = source.testDataParser.parseTestData(bytes).head.asInstanceOf[ConsumerRecord[Any, Any]]

        deserializedObj.key() shouldEqual givenKey
        deserializedObj.value() shouldEqual givenValue
      })
  }

  private def createValidatedSource(sourceFactory: KafkaAvroSourceFactory[Any, Any], topic: String, versionOption: SchemaVersionOption) = {
    val version = versionOption match {
      case LatestSchemaVersion => SchemaVersionOption.LatestOptionName
      case ExistingSchemaVersion(v) => v.toString
    }
    val validatedState = validateParamsAndInitializeState(sourceFactory, topic, version)
    validatedState.map(state => {
      sourceFactory
        .implementation(
          Map(KafkaAvroBaseTransformer.TopicParamName -> topic, KafkaAvroBaseTransformer.SchemaVersionParamName -> version),
          List(TypedNodeDependencyValue(metaData), TypedNodeDependencyValue(nodeId)),
          state)
        .asInstanceOf[Source[AnyRef] with TestDataGenerator with FlinkSourceTestSupport[AnyRef]]
    })
  }

  // Use final contextTransformation to 1) validate parameters and 2) to calculate the final state.
  // This transformation can return
  // - the state that contains information on runtime key-value schemas, which is required in createSource.
  // - validation errors
  private def validateParamsAndInitializeState(sourceFactory: KafkaAvroSourceFactory[Any, Any], topic: String, version: String): Validated[NonEmptyList[ProcessCompilationError], Option[KafkaAvroSourceFactory.KafkaAvroSourceFactoryState[Any, Any, DefinedSingleParameter]]] = {
    implicit val nodeId: NodeId = NodeId("dummy")
    val parameters = (TopicParamName, DefinedEagerParameter(topic, null)) :: (SchemaVersionParamName, DefinedEagerParameter(version, null)) :: Nil
    val definition = sourceFactory.contextTransformation(ValidationContext(), List(OutputVariableNameValue("dummy")))
    val stepResult = definition(sourceFactory.TransformationStep(parameters, None))
    stepResult match {
      case result: sourceFactory.FinalResults if result.errors.isEmpty => Valid(result.state)
      case result: sourceFactory.FinalResults => Invalid(NonEmptyList.fromListUnsafe(result.errors))
      case _ => Invalid(NonEmptyList(CustomNodeError("Unexpected result of contextTransformation", None), Nil))
    }
  }
}
