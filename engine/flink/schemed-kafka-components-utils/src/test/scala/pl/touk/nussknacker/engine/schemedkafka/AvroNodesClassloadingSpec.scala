package pl.touk.nussknacker.engine.schemedkafka

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.definition.test.{ModelDataTestInfoProvider, TestingCapabilities}
import pl.touk.nussknacker.engine.process.helpers.TestResultsHolder
import pl.touk.nussknacker.engine.schemedkafka.AvroNodesClassloadingSpec.sinkForInputMetaResultsHolder
import pl.touk.nussknacker.engine.schemedkafka.KafkaAvroIntegrationMockSchemaRegistry.schemaRegistryMockClient
import pl.touk.nussknacker.engine.schemedkafka.helpers.SchemaRegistryMixin
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaVersionOption
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.MockSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.ThreadUtils
import pl.touk.nussknacker.test.FailingContextClassloader

class AvroNodesClassloadingSpec extends AnyFunSuite with Matchers with SchemaRegistryMixin {

  override protected val schemaRegistryClient: SchemaRegistryClient = MockSchemaRegistry.getClientForScope("testScope")

  private val configCreator = new KafkaAvroTestProcessConfigCreator(sinkForInputMetaResultsHolder) {
    override protected def schemaRegistryClientFactory =
      MockSchemaRegistryClientFactory.confluentBased(schemaRegistryMockClient)
  }

  private def withFailingLoader[T] = ThreadUtils.withThisAsContextClassLoader[T](new FailingContextClassloader) _

  private val scenario = ScenarioBuilder
    .streaming("test")
    .source(
      "source",
      "kafka",
      KafkaUniversalComponentTransformer.topicParamName.value -> "'not_exist'".spel,
      KafkaUniversalComponentTransformer.schemaVersionParamName.value -> s"'${SchemaVersionOption.LatestOptionName}'".spel
    )
    .emptySink("dead", "dead_end")

  private lazy val modelData = LocalModelData(config, List.empty, configCreator = configCreator)

  test("should load classes correctly for tests") {

    // we're interested only in Kafka classes loading, not in data parsing, we don't use mocks as they do not load serializers...
    withFailingLoader {
      new ModelDataTestInfoProvider(modelData).getTestingCapabilities(scenario) shouldBe TestingCapabilities.Disabled
    }
  }

}

object AvroNodesClassloadingSpec {

  private val sinkForInputMetaResultsHolder = new TestResultsHolder[java.util.Map[String @unchecked, _]]

}
