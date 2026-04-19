package pl.touk.nussknacker.defaultmodel.kafkaschemaless

import com.typesafe.config.Config
import pl.touk.nussknacker.defaultmodel.MockSchemaRegistryClientHolder.MockSchemaRegistryClientProvider
import pl.touk.nussknacker.engine.flink.util.transformer.FlinkKafkaComponentProvider

class KafkaJsonSchemalessNoSchemaRegistryItSpec extends BaseKafkaJsonSchemalessItSpec {

  override def createFlinkKafkaComponentProvider(schemaRegistryClientProvider: MockSchemaRegistryClientProvider) =
    new FlinkKafkaComponentProvider()

  override protected def maybeAddSchemaRegistryUrl(config: Config): Config = config

  test("should round-trip json message without schema registry") {
    shouldRoundTripJsonMessageWithoutProvidedSchema()
  }

  test("should round-trip json message without schema derived from data sample") {
    shouldRoundTripJsonMessageWithSchemaDerivedFromProvidedDataSample()
  }

  test("should round-trip json message with schema derived from data sample") {
    shouldRoundTripJsonMessageWithoutSchemaDerivedFromProvidedDataSample()
  }

  test("should round-trip plain message without schema registry") {
    shouldRoundTripPlainMessageWithoutProvidedSchema()
  }

  test("should throw if json message cannot be deserialized based on the inferred data sample type") {
    shouldDropEventsBasedOnTheInferredDataSampleType()
  }

}
