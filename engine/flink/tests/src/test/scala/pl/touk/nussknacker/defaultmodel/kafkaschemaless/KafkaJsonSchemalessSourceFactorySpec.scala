package pl.touk.nussknacker.defaultmodel.kafkaschemaless

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.data.Validated.{Invalid, Valid}
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.defaultmodel.MockSchemaRegistryClientHolder
import pl.touk.nussknacker.engine.api.{Context, NodeId}
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedEagerParameter, OutputVariableNameValue}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.{Source, SourceFactory, TopicName}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.kafka.source.flink.FlinkKafkaSourceImplFactory
import pl.touk.nussknacker.engine.language.json.JsonParser
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer
import pl.touk.nussknacker.engine.schemedkafka.helpers.SchemaRegistryMixin
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaRegistryClientFactory
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.UniversalSchemaBasedSerdeProvider
import pl.touk.nussknacker.engine.schemedkafka.source.UniversalKafkaSourceFactory
import pl.touk.nussknacker.test.{ValidatedValuesDetailedMessage, WithConfig}

class KafkaJsonSchemalessSourceFactorySpec
    extends AnyFunSuite
    with Matchers
    with WithConfig
    with SchemaRegistryMixin
    with ValidatedValuesDetailedMessage {

  type KafkaSource = SourceFactory with KafkaUniversalComponentTransformer[Source, TopicName.ForSource]

  private val schemaRegistryClientProvider = MockSchemaRegistryClientHolder.registerSchemaRegistryClient()

  override def schemaRegistryClient: SchemaRegistryClient = schemaRegistryClientProvider.schemaRegistryClient

  private val inputNodeId             = NodeId("input")
  private val dataSampleParameterName = ParameterName("Data sample")

  test("Should handle an empty object data sample") {
    val dataSample = "{}"
    val result     = typingResultForDataSample(dataSample)
    result shouldBe Valid(Typed.json)
  }

  test("Should handle integer data sample") {
    val dataSample = "123"
    val result     = typingResultForDataSample(dataSample)
    result shouldBe Valid(Typed.typedClass[Int])
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
        List("name" -> Typed.typedClass[String], "age" -> Typed.typedClass[Int], "city" -> Typed.typedClass[String])
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
      List("name" -> Typed.typedClass[String], "age" -> Typed.typedClass[Int], "city" -> Typed.typedClass[String])
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
        "arrayExample: List[Unknown], booleanExample: Boolean, integerExample: Integer, nullExample: Unknown, numberExample: BigDecimal, " +
        "objectExample: Record{nestedArray: List[Integer], nestedBoolean: Boolean, nestedInteger: Integer, nestedNumber: BigDecimal, " +
        "nestedObject: Record{deepKey: String}, nestedString: String}, stringExample: String" +
        "}"
    )
  }

  private def typingResultForDataSample(
      dataSample: String,
  ): ValidatedNel[ProcessCompilationError, TypingResult] = {
    val outputName = "dummy"
    val dataSampleParam: DefinedEagerParameter = JsonParser
      .parse(dataSample, ValidationContext.empty, Unknown)
      .map { typedExpression =>
        DefinedEagerParameter(
          typedExpression.expression.evaluate(Context.dummy, Map.empty),
          typedExpression.returnType
        )
      }
      .validValue
    val params =
      List[(ParameterName, DefinedEagerParameter)](
        KafkaUniversalComponentTransformer.topicParamName       -> DefinedEagerParameter("topicName", Typed[String]),
        KafkaUniversalComponentTransformer.contentTypeParamName -> DefinedEagerParameter("JSON", Typed[String]),
        dataSampleParameterName                                 -> dataSampleParam
      )
    validateParamsAndGetValidationContext(universalSourceFactory, params, outputName)
      .map(_.localVariables.getOrElse(outputName, throw new IllegalStateException(s"Missing variable $outputName")))
  }

  private def validateParamsAndGetValidationContext(
      sourceFactory: KafkaSource,
      parameters: List[(ParameterName, DefinedEagerParameter)],
      outputName: String
  ): Validated[NonEmptyList[ProcessCompilationError], ValidationContext] = {
    implicit val nodeId: NodeId = inputNodeId
    val definition = sourceFactory.contextTransformation(ValidationContext(), List(OutputVariableNameValue(outputName)))
    val stepResult = definition(sourceFactory.TransformationStep(parameters, None))
    stepResult match {
      case sourceFactory.FinalResults(_, Nil, state) =>
        state.get
          .asInstanceOf[UniversalKafkaSourceFactory.ImplementationUniversalKafkaSourceFactoryState]
          .contextInitializer
          .validationContext(ValidationContext.empty)
      case result: sourceFactory.FinalResults =>
        Invalid(NonEmptyList.fromListUnsafe(result.errors))
      case other =>
        Invalid(NonEmptyList.one(CustomNodeError(s"Unexpected result of contextTransformation: $other", None)))
    }
  }

  private lazy val schemaRegistryClientFactory: SchemaRegistryClientFactory =
    schemaRegistryClientProvider.schemaRegistryClientFactory

  private lazy val universalSourceFactory: KafkaSource = {
    new UniversalKafkaSourceFactory(
      schemaRegistryClientFactory,
      UniversalSchemaBasedSerdeProvider.create(schemaRegistryClientFactory),
      testModelConfig,
      kafkaConfig,
      new FlinkKafkaSourceImplFactory
    )
  }

}
