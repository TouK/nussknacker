package pl.touk.nussknacker.engine.schemedkafka.source.flink

import org.scalatest.BeforeAndAfter
import pl.touk.nussknacker.engine.api.CustomStreamTransformer
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.flink.test.RecordingExceptionConsumer
import pl.touk.nussknacker.engine.kafka.generic.FlinkKafkaDelayedSourceImplFactory
import pl.touk.nussknacker.engine.kafka.source.InputMeta
import pl.touk.nussknacker.engine.kafka.source.delayed.DelayedKafkaSourceFactory.{
  delayParameter,
  timestampFieldParamName
}
import pl.touk.nussknacker.engine.process.helpers.TestResultsHolder
import pl.touk.nussknacker.engine.process.helpers.SampleNodes.SinkForLongs
import pl.touk.nussknacker.engine.schemedkafka.KafkaAvroIntegrationMockSchemaRegistry.schemaRegistryMockClient
import pl.touk.nussknacker.engine.schemedkafka.KafkaAvroTestProcessConfigCreator
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer.{
  schemaVersionParamName,
  sinkValueParamName,
  topicParamName
}
import pl.touk.nussknacker.engine.schemedkafka.helpers.KafkaAvroSpecMixin
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.MockSchemaRegistryClient
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.{
  MockSchemaRegistryClientFactory,
  UniversalSchemaBasedSerdeProvider
}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{SchemaRegistryClientFactory, SchemaVersionOption}
import pl.touk.nussknacker.engine.schemedkafka.source.delayed.DelayedUniversalKafkaSourceFactory
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.engine.testing.LocalModelData

import java.time.Instant

trait DelayedUniversalKafkaSourceIntegrationMixinSpec extends KafkaAvroSpecMixin with BeforeAndAfter {
  protected val sinkForLongsResultsHolder: () => TestResultsHolder[java.lang.Long]
  protected val sinkForInputMetaResultsHolder: () => TestResultsHolder[InputMeta[_]]

  private lazy val creator: ProcessConfigCreator = new DelayedKafkaUniversalProcessConfigCreator(
    sinkForLongsResultsHolder(),
    sinkForInputMetaResultsHolder()
  )

  override protected def schemaRegistryClient: MockSchemaRegistryClient = schemaRegistryMockClient

  override protected def schemaRegistryClientFactory: SchemaRegistryClientFactory =
    MockSchemaRegistryClientFactory.confluentBased(schemaRegistryMockClient)

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    modelData = LocalModelData(config, List.empty, creator)
  }

  before {
    sinkForLongsResultsHolder().clear()
    sinkForInputMetaResultsHolder().clear()
  }

  protected def runAndVerify(topic: TopicName.ForSource, process: CanonicalProcess, givenObj: AnyRef): Unit = {
    kafkaClient.createTopic(topic.name, partitions = 1)
    pushMessage(givenObj, topic)
    run(process) {
      eventually {
        RecordingExceptionConsumer.exceptionsFor(runId) shouldBe empty
        sinkForLongsResultsHolder().results should have size 1
      }
    }
  }

  protected def createProcessWithDelayedSource(
      topic: TopicName.ForSource,
      version: SchemaVersionOption,
      timestampField: String,
      delay: String
  ): CanonicalProcess = {

    import pl.touk.nussknacker.engine.spel.SpelExtension._

    ScenarioBuilder
      .streaming("kafka-universal-delayed-test")
      .parallelism(1)
      .source(
        "start",
        "kafka-universal-delayed",
        topicParamName.value               -> s"'${topic.name}'".spel,
        schemaVersionParamName.value       -> formatVersionParam(version).spel,
        timestampFieldParamName.value      -> timestampField.spel,
        delayParameter.parameterName.value -> delay.spel
      )
      .emptySink("out", "sinkForLongs", sinkValueParamName.value -> "T(java.time.Instant).now().toEpochMilli()".spel)
  }

}

class DelayedKafkaUniversalProcessConfigCreator(
    sinkForLongsResultsHolder: => TestResultsHolder[java.lang.Long],
    sinkForInputMetaResultsHolder: => TestResultsHolder[InputMeta[_]]
) extends KafkaAvroTestProcessConfigCreator(sinkForInputMetaResultsHolder) {

  override def sourceFactories(
      modelDependencies: ProcessObjectDependencies
  ): Map[String, WithCategories[SourceFactory]] = {
    Map(
      "kafka-universal-delayed" -> defaultCategory(
        new DelayedUniversalKafkaSourceFactory(
          schemaRegistryClientFactory,
          UniversalSchemaBasedSerdeProvider.create(schemaRegistryClientFactory),
          modelDependencies,
          new FlinkKafkaDelayedSourceImplFactory(None, UniversalTimestampFieldAssigner(_))
        )
      )
    )
  }

  override def customStreamTransformers(
      modelDependencies: ProcessObjectDependencies
  ): Map[String, WithCategories[CustomStreamTransformer]] =
    Map.empty

  override def sinkFactories(
      modelDependencies: ProcessObjectDependencies
  ): Map[String, WithCategories[SinkFactory]] = {
    Map(
      "sinkForLongs" -> defaultCategory(SinkForLongs(sinkForLongsResultsHolder))
    )
  }

  override def expressionConfig(modelDependencies: ProcessObjectDependencies): ExpressionConfig = {
    super.expressionConfig(modelDependencies).copy(additionalClasses = List(classOf[Instant]))
  }

  override protected def schemaRegistryClientFactory: SchemaRegistryClientFactory =
    MockSchemaRegistryClientFactory.confluentBased(schemaRegistryMockClient)

}
