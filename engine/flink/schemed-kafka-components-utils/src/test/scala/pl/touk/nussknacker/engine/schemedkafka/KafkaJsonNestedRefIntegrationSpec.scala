package pl.touk.nussknacker.engine.schemedkafka

import org.apache.kafka.common.serialization.Serializer
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, SinkFactory, WithCategories}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.flink.test.RecordingExceptionConsumer
import pl.touk.nussknacker.engine.kafka.source.InputMeta
import pl.touk.nussknacker.engine.kafka.source.delayed.DelayedKafkaSourceFactory.{
  delayParameter,
  timestampFieldParamName
}
import pl.touk.nussknacker.engine.process.helpers.SampleNodes.SinkForAny
import pl.touk.nussknacker.engine.process.helpers.TestResultsHolder
import pl.touk.nussknacker.engine.schemedkafka.KafkaAvroIntegrationMockSchemaRegistry.schemaRegistryMockClient
import pl.touk.nussknacker.engine.schemedkafka.KafkaJsonNestedRefIntegrationSpec.{
  sinkForAnyResultsHolder,
  sinkForInputMetaResultsHolder
}
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer.{
  schemaVersionParamName,
  sinkValueParamName,
  topicParamName
}
import pl.touk.nussknacker.engine.schemedkafka.helpers.{KafkaAvroSpecMixin, SimpleKafkaJsonSerializer}
import pl.touk.nussknacker.engine.schemedkafka.schema.NestedRefSchema
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{ExistingSchemaVersion, SchemaRegistryClientFactory}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.MockSchemaRegistryClient
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.MockSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.engine.testing.LocalModelData
import scala.jdk.CollectionConverters._

class KafkaJsonNestedRefIntegrationSpec extends KafkaAvroSpecMixin {

  override protected def keySerializer: Serializer[Any] = SimpleKafkaJsonSerializer

  override protected def valueSerializer: Serializer[Any] = SimpleKafkaJsonSerializer

  override protected def schemaRegistryClient: MockSchemaRegistryClient = schemaRegistryMockClient

  override protected def schemaRegistryClientFactory: SchemaRegistryClientFactory =
    MockSchemaRegistryClientFactory.confluentBased(schemaRegistryMockClient)

  private lazy val creator: KafkaAvroTestProcessConfigCreator = new KafkaAvroTestProcessConfigCreator(
    sinkForInputMetaResultsHolder
  ) {

    override protected def schemaRegistryClientFactory: SchemaRegistryClientFactory =
      MockSchemaRegistryClientFactory.confluentBased(
        schemaRegistryMockClient
      ) // what about override protected def schemaRegistryClientFactory ???

    override def sinkFactories(modelDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] =
      super.sinkFactories(modelDependencies) ++ Map(
        "sinkForAny" -> defaultCategory(SinkForAny(sinkForAnyResultsHolder)),
      )

  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    modelData = LocalModelData(config, List.empty, creator)
  }

  protected def runAndVerify(
      topic: String,
      process: CanonicalProcess,
      givenObj: List[AnyRef],
      expectedObj: List[AnyRef]
  ): Unit = {
    kafkaClient.createTopic(topic, partitions = 1)

    givenObj.foreach(obj => pushMessage(obj, topic))

    run(process) {
      eventually {
        RecordingExceptionConsumer.exceptionsFor(runId) shouldBe empty
        sinkForAnyResultsHolder.results should have size givenObj.size
      }
      sinkForAnyResultsHolder.results.toString() shouldEqual expectedObj.toString()
    }
  }

  protected def createProcess(topic: String): CanonicalProcess = {

    import spel.Implicits._

    ScenarioBuilder
      .streaming("nested-ref-test")
      .parallelism(1)
      .source(
        "start",
        "kafka",
        topicParamName.value               -> s"'$topic'",
        schemaVersionParamName.value       -> asSpelExpression(formatVersionParam(ExistingSchemaVersion(1))),
        timestampFieldParamName.value      -> "null",
        delayParameter.parameterName.value -> "null"
      )
      .emptySink(
        "out",
        "sinkForAny",
        sinkValueParamName.value -> """{
         |  "evaluatedConditions": {
         |    "hasNonEmptyRecord": (#input.forwardCallRecord != null OR #input.mtSMSRecord != null),
         |    "notEmptyForwardCallRecord": #input.forwardCallRecord != null,
         |    "notEmptyMtSMSRecord": #input.mtSMSRecord != null
         |  },
         |  "record": {
         |    "forwardCallRecord": #input.forwardCallRecord,
         |    "mtSMSRecord": #input.mtSMSRecord
         |  },
         |  "input": #input
         |}""".stripMargin
      )
  }

  val expected1 = Map(
    "evaluatedConditions" -> Map(
      "hasNonEmptyRecord"         -> false,
      "notEmptyForwardCallRecord" -> false,
      "notEmptyMtSMSRecord"       -> false
    ).asJava,
    "record" -> Map(
      "forwardCallRecord" -> null,
      "mtSMSRecord"       -> null
    ).asJava,
    "input" -> Map(
      "forwardCallRecord" -> null,
      "mtSMSRecord"       -> null
    ).asJava
  ).asJava

  val expected4 = Map(
    "evaluatedConditions" -> Map(
      "hasNonEmptyRecord"         -> true,
      "notEmptyForwardCallRecord" -> true,
      "notEmptyMtSMSRecord"       -> true
    ).asJava,
    "record" -> Map(
      "forwardCallRecord" -> Map("callDuration" -> 123).asJava,
      "mtSMSRecord"       -> Map("chargedParty" -> 321).asJava
    ).asJava,
    "input" -> Map(
      "forwardCallRecord" -> Map("callDuration" -> 123).asJava,
      "mtSMSRecord"       -> Map("chargedParty" -> 321).asJava
    ).asJava
  ).asJava

  test("no problem") {
    val inputTopic = "schema-with-no-refs"
    registerJsonSchema(inputTopic, NestedRefSchema.jsonSchema, isKey = false)
    val process     = createProcess(inputTopic)
    val givenObj    = List(NestedRefSchema.example1, NestedRefSchema.example4)
    val expectedObj = List(expected1, expected4)
    runAndVerify(inputTopic, process, givenObj, expectedObj)
  }

  test("problem") {
    val inputTopic = "schema-with-refs"
    registerJsonSchema(inputTopic, NestedRefSchema.jsonSchemaWithRefs, isKey = false)
    val process     = createProcess(inputTopic)
    val givenObj    = List(NestedRefSchema.example1, NestedRefSchema.example4)
    val expectedObj = List(expected1, expected4)
    runAndVerify(inputTopic, process, givenObj, expectedObj)
  }

}

object KafkaJsonNestedRefIntegrationSpec {
  private val sinkForInputMetaResultsHolder = new TestResultsHolder[InputMeta[_]]
  private val sinkForAnyResultsHolder       = new TestResultsHolder[AnyRef]

}
