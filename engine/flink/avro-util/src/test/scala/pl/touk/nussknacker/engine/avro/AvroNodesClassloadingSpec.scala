package pl.touk.nussknacker.engine.avro

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.deployment.TestProcess.TestData
import pl.touk.nussknacker.engine.avro.helpers.SchemaRegistryMixin
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaVersionOption
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.definition.{ModelDataTestInfoProvider, TestingCapabilities}
import pl.touk.nussknacker.engine.graph.node.Source
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.testmode.TestDataPreparer
import pl.touk.nussknacker.engine.util.ThreadUtils
import pl.touk.nussknacker.test.FailingContextClassloader

class AvroNodesClassloadingSpec extends FunSuite with Matchers with SchemaRegistryMixin {

  private val configCreator = new KafkaAvroTestProcessConfigCreator

  override protected val schemaRegistryClient: SchemaRegistryClient = MockSchemaRegistry.getClientForScope("testScope")

  private def withFailingLoader[T] = ThreadUtils.withThisAsContextClassLoader[T](new FailingContextClassloader) _

  private val scenario = EspProcessBuilder
    .id("test")
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
