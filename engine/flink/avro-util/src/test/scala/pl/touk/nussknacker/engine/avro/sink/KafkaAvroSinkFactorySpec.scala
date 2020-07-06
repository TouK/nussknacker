package pl.touk.nussknacker.engine.avro.sink

import com.typesafe.config.ConfigFactory
import io.confluent.kafka.schemaregistry.client.{SchemaRegistryClient => CSchemaRegistryClient}
import org.apache.avro.generic.GenericContainer
import org.apache.flink.api.scala._
import pl.touk.nussknacker.engine.Interpreter
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, NodeId}
import pl.touk.nussknacker.engine.api.{LazyParameter, MetaData, StreamMetaData}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.TypedNodeDependencyValue
import pl.touk.nussknacker.engine.api.process.Sink
import pl.touk.nussknacker.engine.api.typed.CustomNodeValidationException
import pl.touk.nussknacker.engine.avro.KafkaAvroFactory.{SchemaVersionParamName, SinkOutputParamName, TopicParamName}
import pl.touk.nussknacker.engine.avro.{KafkaAvroFactory, KafkaAvroSpecMixin}
import pl.touk.nussknacker.engine.avro.schema.{FullNameV1, FullNameV2, PaymentV1}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.compile.nodevalidation.{GenericNodeTransformationValidator, TransformationResult}
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.testing.{EmptyProcessConfigCreator, LocalModelData}
import pl.touk.nussknacker.engine.spel.Implicits._

class KafkaAvroSinkFactorySpec extends KafkaAvroSpecMixin with KafkaAvroSinkSpecMixin {

  import KafkaAvroSinkMockSchemaRegistry._

  override def schemaRegistryClient: CSchemaRegistryClient = schemaRegistryMockClient

  override protected def confluentClientFactory: ConfluentSchemaRegistryClientFactory = factory

  private lazy val avroSinkFactory: KafkaAvroSinkFactory = createAvroSinkFactory(useSpecificAvroReader = false)

  private def validate(params: (String, Expression)*): TransformationResult = {
    val modelData = LocalModelData(ConfigFactory.empty(), new EmptyProcessConfigCreator)
    val validator = new GenericNodeTransformationValidator(ExpressionCompiler.withoutOptimization(modelData),
        ExpressionEvaluator.unOptimizedEvaluator(modelData))

    implicit val meta: MetaData = MetaData("processId", StreamMetaData())
    implicit val nodeId: NodeId = NodeId("id")
    val paramsList = params.toList.map(p => Parameter(p._1, p._2))
    validator.validateNode(avroSinkFactory, paramsList, ValidationContext(), Some(Interpreter.InputParamName)).toOption.get
  }

  protected def createSink(topic: String, version: Integer, output: LazyParameter[GenericContainer]): Sink =
    avroSinkFactory.implementation(
      Map(KafkaAvroFactory.TopicParamName -> topic,
          KafkaAvroFactory.SchemaVersionParamName -> version, KafkaAvroFactory.SinkOutputParamName -> output),
            List(TypedNodeDependencyValue(metaData), TypedNodeDependencyValue(nodeId)))

  test("should throw exception when schema doesn't exist") {
    assertThrowsWithParent[CustomNodeValidationException] {
      val output = createOutput(FullNameV1.schema, FullNameV1.exampleData)
      createSink("not-exists-subject", 1, output)
    }
  }

  test("should throw exception when schema version doesn't exist") {
    assertThrowsWithParent[CustomNodeValidationException] {
      val output = createOutput(FullNameV1.schema, FullNameV1.exampleData)
      createSink(fullnameTopic, 666, output)
    }
  }

  test("should allow to create sink when #output schema is the same as sink schema") {
    val output = createOutput(FullNameV1.schema, FullNameV1.exampleData)
    createSink(fullnameTopic, 1, output)
  }

  test("should allow to create sink when #output schema is compatible with newer sink schema") {
    val output = createOutput(FullNameV1.schema, FullNameV1.exampleData)
    createSink(fullnameTopic, 2, output)
  }

  test("should allow to create sink when #output schema is compatible with older fxied sink schema") {
    val output = createOutput(FullNameV2.schema, FullNameV2.exampleData)
    createSink(fullnameTopic, 1, output)
  }

  test("should throw exception when #output schema is not compatible with sink schema") {
    assertThrowsWithParent[CustomNodeValidationException] {
      val output = createOutput(PaymentV1.schema, PaymentV1.exampleData)
      createSink(fullnameTopic, 2, output)
    }
  }


  test("should validate specific version") {
    val result = validate(
      SinkOutputParamName -> "",
      TopicParamName -> s"'${KafkaAvroSinkMockSchemaRegistry.fullnameTopic}'",
      SchemaVersionParamName -> "1")

    result.errors shouldBe Nil
  }

  test("should validate latest version") {
    val result = validate(
      SinkOutputParamName -> "",
      TopicParamName -> s"'${KafkaAvroSinkMockSchemaRegistry.fullnameTopic}'",
      SchemaVersionParamName -> "")

    result.errors shouldBe Nil
  }

  test("should return sane error on invalid topic") {
    val result = validate(
      SinkOutputParamName -> "",
      TopicParamName -> "'tereferer'",
      SchemaVersionParamName -> "1")

    result.errors shouldBe CustomNodeError("id", "Schema subject doesn't exist.", Some(TopicParamName)) ::
      CustomNodeError("id", "Schema subject doesn't exist.", Some(SchemaVersionParamName))::Nil
  }

  test("should return sane error on invalid version") {
    val result = validate(
      SinkOutputParamName -> "",
      TopicParamName -> s"'${KafkaAvroSinkMockSchemaRegistry.fullnameTopic}'",
      SchemaVersionParamName -> "343543")

    result.errors shouldBe CustomNodeError("id", "Schema version doesn't exist.", Some(SchemaVersionParamName))::Nil
  }

  test("should validate output") {
    val result = validate(
      SinkOutputParamName -> "''",
      TopicParamName -> s"'${KafkaAvroSinkMockSchemaRegistry.fullnameTopic}'",
      SchemaVersionParamName -> "")

    result.errors shouldBe CustomNodeError("id", "Provided output doesn't match to selected avro schema.", Some(SinkOutputParamName))::Nil
  }

}
