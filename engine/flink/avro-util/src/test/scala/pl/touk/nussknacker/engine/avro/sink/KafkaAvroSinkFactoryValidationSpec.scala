package pl.touk.nussknacker.engine.avro.sink

import com.typesafe.config.ConfigFactory
import org.apache.flink.api.scala._
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.Interpreter
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, NodeId}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.namespaces.DefaultObjectNaming
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.api.{MetaData, StreamMetaData}
import pl.touk.nussknacker.engine.avro.KafkaAvroFactory.{SchemaVersionParamName, SinkOutputParamName, TopicParamName}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentSchemaRegistryProvider
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.compile.nodevalidation.{GenericNodeTransformationValidator, TransformationResult}
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testing.{EmptyProcessConfigCreator, LocalModelData}

import scala.collection.JavaConverters._

class KafkaAvroSinkFactoryValidationSpec extends FunSuite with Matchers with KafkaAvroSinkSpecMixin {

  private val modelData = LocalModelData(ConfigFactory.empty(), new EmptyProcessConfigCreator)

  private val validator = new GenericNodeTransformationValidator(ExpressionCompiler.withoutOptimization(modelData),
    ExpressionEvaluator.unOptimizedEvaluator(modelData))

  private val sinkFactory: KafkaAvroSinkFactory = {
    val processObjectDependencies = ProcessObjectDependencies(ConfigFactory.parseMap(Map("kafka.kafkaAddress" -> "notexist:9092").asJava), DefaultObjectNaming)
    val provider = ConfluentSchemaRegistryProvider[Any](KafkaAvroSinkMockSchemaRegistry.factory, processObjectDependencies)
    new KafkaAvroSinkFactory(provider, processObjectDependencies)
  }

  private def validate(params: (String, Expression)*): TransformationResult = {
    implicit val meta: MetaData = MetaData("processId", StreamMetaData())
    implicit val nodeId: NodeId = NodeId("id")
    val paramsList = params.toList.map(p => Parameter(p._1, p._2))
    validator.validateNode(sinkFactory, paramsList, ValidationContext(), Some(Interpreter.InputParamName)).toOption.get
  }



  test("Should validate specific version") {
    val result = validate(
      SinkOutputParamName -> "",
      TopicParamName -> s"'${KafkaAvroSinkMockSchemaRegistry.fullnameTopic}'",
      SchemaVersionParamName -> "1")

    result.errors shouldBe Nil
  }

  test("Should validate latest version") {
    val result = validate(
      SinkOutputParamName -> "",
      TopicParamName -> s"'${KafkaAvroSinkMockSchemaRegistry.fullnameTopic}'",
      SchemaVersionParamName -> "")

    result.errors shouldBe Nil
  }

  test("Should return sane error on invalid topic") {
    val result = validate(
      SinkOutputParamName -> "",
      TopicParamName -> "'tereferer'",
      SchemaVersionParamName -> "1")

    result.errors shouldBe CustomNodeError("id", "Schema subject doesn't exist.", Some(TopicParamName)) ::
      CustomNodeError("id", "Schema subject doesn't exist.", Some(SchemaVersionParamName))::Nil
  }

  test("Should return sane error on invalid version") {
    val result = validate(
      SinkOutputParamName -> "",
      TopicParamName -> s"'${KafkaAvroSinkMockSchemaRegistry.fullnameTopic}'",
      SchemaVersionParamName -> "343543")

    result.errors shouldBe CustomNodeError("id", "Schema version doesn't exist.", Some(SchemaVersionParamName))::Nil
  }

  test("Should validate output") {
    val result = validate(
      SinkOutputParamName -> "''",
      TopicParamName -> s"'${KafkaAvroSinkMockSchemaRegistry.fullnameTopic}'",
      SchemaVersionParamName -> "")

    result.errors shouldBe CustomNodeError("id", "Provided output doesn't match to selected avro schema.", Some(SinkOutputParamName))::Nil
  }

}
