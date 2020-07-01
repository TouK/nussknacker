package pl.touk.nussknacker.engine.avro.source

import com.typesafe.config.ConfigFactory
import org.apache.avro.generic.GenericRecord
import org.apache.flink.api.scala._
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.Interpreter
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, NodeId}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.namespaces.DefaultObjectNaming
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, Unknown}
import pl.touk.nussknacker.engine.api.{MetaData, StreamMetaData}
import pl.touk.nussknacker.engine.avro.KafkaAvroFactory.{SchemaVersionParamName, TopicParamName}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentSchemaRegistryProvider
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.compile.nodevalidation.{GenericNodeTransformationValidator, TransformationResult}
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testing.{EmptyProcessConfigCreator, LocalModelData}

import scala.collection.JavaConverters._

class KafkaAvroSourceFactoryValidationSpec extends FunSuite with Matchers with KafkaAvroSourceSpecMixin {

  private val modelData = LocalModelData(ConfigFactory.empty(), new EmptyProcessConfigCreator)

  private val validator = new GenericNodeTransformationValidator(ExpressionCompiler.withoutOptimization(modelData),
    ExpressionEvaluator.unOptimizedEvaluator(modelData))

  private val sourceFactory: KafkaAvroSourceFactory[GenericRecord] = {
    val processObjectDependencies = ProcessObjectDependencies(ConfigFactory.parseMap(Map("kafka.kafkaAddress" -> "notexist:9092").asJava), DefaultObjectNaming)
    val provider = ConfluentSchemaRegistryProvider[GenericRecord](KafkaAvroSourceMockSchemaRegistry.factory, processObjectDependencies)
    new KafkaAvroSourceFactory[GenericRecord](provider, processObjectDependencies, None)
  }

  private def validate(params: (String, Expression)*): TransformationResult = {
    implicit val meta: MetaData = MetaData("processId", StreamMetaData())
    implicit val nodeId: NodeId = NodeId("id")
    val paramsList = params.toList.map(p => Parameter(p._1, p._2))
    validator.validateNode(sourceFactory, paramsList, ValidationContext(), Some(Interpreter.InputParamName)).toOption.get
  }

  test("Should validate specific version") {
    val result = validate(TopicParamName -> s"'${KafkaAvroSourceMockSchemaRegistry.RecordTopic}'",
      SchemaVersionParamName -> "1")

    result.errors shouldBe Nil
  }

  test("Should validate latest version") {
    val result = validate(TopicParamName -> s"'${KafkaAvroSourceMockSchemaRegistry.RecordTopic}'",
      SchemaVersionParamName -> "")

    result.errors shouldBe Nil
  }

  test("Should return sane error on invalid topic") {
    val result = validate(TopicParamName -> "'terefere'", SchemaVersionParamName -> "")

    result.errors shouldBe CustomNodeError("id", "Schema subject doesn't exist.", Some(TopicParamName)) ::
      CustomNodeError("id", "Schema subject doesn't exist.", Some(SchemaVersionParamName)) :: Nil
    result.outputContext shouldBe ValidationContext(Map(Interpreter.InputParamName -> Unknown))
  }

  test("Should return sane error on invalid version") {
    val result = validate(TopicParamName -> s"'${KafkaAvroSourceMockSchemaRegistry.RecordTopic}'",
      SchemaVersionParamName -> "12345")

    result.errors shouldBe CustomNodeError("id", "Schema version doesn't exist.", Some(SchemaVersionParamName)) :: Nil
    result.outputContext shouldBe ValidationContext(Map(Interpreter.InputParamName -> Unknown))
  }

  test("Should properly detect input type") {
    val result = validate(TopicParamName -> s"'${KafkaAvroSourceMockSchemaRegistry.RecordTopic}'",
      SchemaVersionParamName -> "")

    result.errors shouldBe Nil
    result.outputContext shouldBe ValidationContext(Map(Interpreter.InputParamName -> TypedObjectTypingResult(
      Map(
        "first" -> Typed[CharSequence],
        "middle" -> Typed[CharSequence],
        "last" -> Typed[CharSequence]
      ), Typed.typedClass[GenericRecord]
    )))
  }


}
