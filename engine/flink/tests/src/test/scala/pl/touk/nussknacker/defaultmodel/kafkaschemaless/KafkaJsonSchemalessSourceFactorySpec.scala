package pl.touk.nussknacker.defaultmodel.kafkaschemaless

import cats.data.Validated.Valid
import cats.data.ValidatedNel
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.defaultmodel.MockSchemaRegistryClientHolder
import pl.touk.nussknacker.engine.api.{NodeId, NodeName}
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.namespaces.NamingStrategy
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.Source
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.flink.util.test.FlinkNodeCompiler.FlinkNodeCompilerExt
import pl.touk.nussknacker.engine.graph.evaluatedparam.{Parameter => NodeParameter}
import pl.touk.nussknacker.engine.graph.node.{Source => SourceNode}
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.kafka.source.flink.FlinkKafkaSourceImplFactory
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer
import pl.touk.nussknacker.engine.schemedkafka.helpers.KafkaSchemaRegistryMixin
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.UniversalSchemaBasedSerdeProvider
import pl.touk.nussknacker.engine.schemedkafka.source.UniversalKafkaSourceFactory
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion
import pl.touk.nussknacker.engine.util.test.TestNodeCompiler
import pl.touk.nussknacker.test.{ValidatedValuesDetailedMessage, WithModelConfig}

class KafkaJsonSchemalessSourceFactorySpec
    extends AnyFunSuite
    with Matchers
    with WithModelConfig
    with KafkaSchemaRegistryMixin
    with ValidatedValuesDetailedMessage {

  override protected val kafkaComponentsConfigPrefix: String = "not-important"

  override protected def resolveModelConfig(config: Config): Config = {
    super
      .resolveModelConfig(config)
      .withValue(
        s"$kafkaComponentsConfigPrefix.useDataSampleParamForSchemalessJsonTopicBasedKafkaSource",
        fromAnyRef(true)
      )
  }

  private val schemaRegistryClientProvider = MockSchemaRegistryClientHolder.registerSchemaRegistryClient()

  override def schemaRegistryClient: SchemaRegistryClient = schemaRegistryClientProvider.schemaRegistryClient

  private lazy val nodeCompiler = {
    val sourceFactory = new UniversalKafkaSourceFactory(
      schemaRegistryClientProvider.schemaRegistryClientFactory,
      UniversalSchemaBasedSerdeProvider
        .create(schemaRegistryClientProvider.schemaRegistryClientFactory, kafkaComponentsConfig),
      kafkaComponentsConfig,
      NamingStrategy.Disabled,
      new FlinkKafkaSourceImplFactory
    )
    TestNodeCompiler
      .flinkBased(ConfigFactory.empty())
      .withExtraComponents(List(ComponentDefinition("kafka", sourceFactory)))
      .build()
  }

  private val sampleTopic = "topicName"

  private val dataSampleParameterName = ParameterName("Data sample")

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    kafkaClient.createTopic(sampleTopic, 1)
  }

  test("Should handle an empty object data sample") {
    val dataSample = "{}"
    val result     = typingResultForDataSample(dataSample)
    result shouldBe Valid(Typed.json)
  }

  test("Should handle integer data sample") {
    val dataSample = "123"
    val result     = typingResultForDataSample(dataSample)
    result shouldBe Valid(Typed.typedClass[Long])
  }

  test("Should handle floating number data sample") {
    val dataSample = "3.14"
    val result     = typingResultForDataSample(dataSample)
    result shouldBe Valid(Typed.typedClass[java.math.BigDecimal])
  }

  test("Should handle string data sample") {
    val dataSample = "\"text\""
    val result     = typingResultForDataSample(dataSample)
    result shouldBe Valid(Typed.typedClass[String])
  }

  test("Should handle an empty array data sample") {
    val dataSample = "[]"
    val result     = typingResultForDataSample(dataSample)
    result shouldBe Valid(Typed.json)
  }

  test("Should handle boolean data sample") {
    val dataSample = "false"
    val result     = typingResultForDataSample(dataSample)
    result shouldBe Valid(Typed.typedClass[Boolean])
  }

  test("Should handle null data sample") {
    val dataSample = "null"
    val result     = typingResultForDataSample(dataSample)
    result shouldBe Valid(Typed.json)
  }

  test("Should handle object data sample") {
    val dataSample =
      s"""
         |{
         |  "name": "Tom",
         |  "age": 22,
         |  "city": "Warsaw"
         |}
         |""".stripMargin
    val result = typingResultForDataSample(dataSample)
    result shouldBe Valid(
      Typed.record(
        List("name" -> Typed.typedClass[String], "age" -> Typed.typedClass[Long], "city" -> Typed.typedClass[String])
      )
    )
  }

  test("Should handle array data sample") {
    val dataSample =
      s"""
         |[
         |  {
         |    "name": "Tom",
         |    "age": 22,
         |    "city": "Warsaw"
         |  }
         |]
         |""".stripMargin

    val recordType = Typed.record(
      List("name" -> Typed.typedClass[String], "age" -> Typed.typedClass[Long], "city" -> Typed.typedClass[String])
    )
    val expectedType = Typed.genericTypeClass[java.util.List[_]](List(recordType))
    val result       = typingResultForDataSample(dataSample)
    result shouldBe Valid(expectedType)
  }

  test("Should handle complex object data sample") {
    val dataSample =
      s"""
         |{
         |  "stringExample": "exampleText",
         |  "numberExample": 42.5,
         |  "integerExample": 100,
         |  "booleanExample": true,
         |  "nullExample": null,
         |  "arrayExample": [
         |    "one",
         |    2,
         |    false,
         |    null,
         |    {"nestedKey": "nestedValue"}
         |  ],
         |  "objectExample": {
         |    "nestedString": "nestedText",
         |    "nestedNumber": 3.14,
         |    "nestedInteger": 2,
         |    "nestedBoolean": false,
         |    "nestedArray": [1, 2, 3],
         |    "nestedObject": {
         |      "deepKey": "deepValue"
         |    }
         |  }
         |}""".stripMargin

    // TypingResult does not keep the order of fields in the Maps, so it is hard to assert with the TypingResult instance
    // display method sorts fields, so the order is deterministic
    val expectedDisplayedResult = typingResultForDataSample(dataSample).map(_.display)
    expectedDisplayedResult shouldBe Valid(
      "Record{" +
        "arrayExample: List[Unknown], booleanExample: Boolean, integerExample: Long, nullExample: Unknown, numberExample: BigDecimal, " +
        "objectExample: Record{nestedArray: List[Long], nestedBoolean: Boolean, nestedInteger: Long, nestedNumber: BigDecimal, " +
        "nestedObject: Record{deepKey: String}, nestedString: String}, stringExample: String" +
        "}"
    )
  }

  private def typingResultForDataSample(
      dataSample: String,
  ): ValidatedNel[ProcessCompilationError, TypingResult] = {
    val params =
      List[NodeParameter](
        NodeParameter(KafkaUniversalComponentTransformer.topicParamName, s"'$sampleTopic'".spel),
        NodeParameter(KafkaUniversalComponentTransformer.contentTypeParamName, "'JSON'".spel),
        NodeParameter(dataSampleParameterName, dataSample.jsonExpression)
      )
    validateParamsAndGetValidationContext(params)
      .map(_.localVariables.getOrElse("input", throw new IllegalStateException(s"Missing input variable")))
  }

  protected def validateParamsAndGetValidationContext(
      nodeParameters: List[NodeParameter]
  ): ValidatedNel[ProcessCompilationError, ValidationContext] = {
    val compilationResult =
      nodeCompiler.compileNode(SourceNode(NodeId("mock-id"), NodeName("mock-id"), SourceRef("kafka", nodeParameters)))
    compilationResult.compiledObject.andThen { _ =>
      compilationResult.validationContext
    }
  }

}
