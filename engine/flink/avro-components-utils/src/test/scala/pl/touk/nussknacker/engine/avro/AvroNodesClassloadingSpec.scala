package pl.touk.nussknacker.engine.avro

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.test.TestData
import pl.touk.nussknacker.engine.avro.KafkaAvroIntegrationMockSchemaRegistry.schemaRegistryMockClient
import pl.touk.nussknacker.engine.avro.helpers.SchemaRegistryMixin
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.ConfluentSchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.schemaregistry.{SchemaRegistryProvider, SchemaVersionOption}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.MockConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.definition.{ModelDataTestInfoProvider, TestingCapabilities}
import pl.touk.nussknacker.engine.graph.node.Source
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.testmode.TestDataPreparer
import pl.touk.nussknacker.engine.util.ThreadUtils
import pl.touk.nussknacker.test.FailingContextClassloader

class AvroNodesClassloadingSpec extends FunSuite with Matchers with SchemaRegistryMixin {

  override protected val schemaRegistryClient: SchemaRegistryClient = MockSchemaRegistry.getClientForScope("testScope")

  private val configCreator = new KafkaAvroTestProcessConfigCreator {
    override protected def createSchemaRegistryProvider: SchemaRegistryProvider =
      ConfluentSchemaRegistryProvider(new MockConfluentSchemaRegistryClientFactory(schemaRegistryMockClient))
  }

  private def withFailingLoader[T] = ThreadUtils.withThisAsContextClassLoader[T](new FailingContextClassloader) _

  private val scenario = ScenarioBuilder
    .streaming("test")
    .source("source", "kafka-avro",
      KafkaAvroBaseComponentTransformer.TopicParamName ->  "'not_exist'",
      KafkaAvroBaseComponentTransformer.SchemaVersionParamName -> s"'${SchemaVersionOption.LatestOptionName}'"
    )
    .emptySink("dead", "dead_end")

  private lazy val modelData = LocalModelData(config, configCreator)

  test("should load classes correctly for tests") {

    //we're interested only in Kafka classes loading, not in data parsing, we don't use mocks as they do not load serializers...
    withFailingLoader {
      new ModelDataTestInfoProvider(modelData).getTestingCapabilities(scenario.metaData,
        scenario.roots.head.data.asInstanceOf[Source]) shouldBe TestingCapabilities(canBeTested = false, canGenerateTestData = false)

      intercept[IllegalArgumentException] {
        new TestDataPreparer(modelData).prepareDataForTest(scenario, TestData.newLineSeparated())
      }.getMessage.contains("InvalidPropertyFixedValue") shouldBe true
    }
  }


}
