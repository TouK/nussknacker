package pl.touk.nussknacker.defaultmodel.kafkaschemaless

import com.typesafe.config.Config
import pl.touk.nussknacker.defaultmodel.MockSchemaRegistryClientHolder.MockSchemaRegistryClientProvider
import pl.touk.nussknacker.engine.flink.util.transformer.FlinkKafkaComponentProvider

class KafkaJsonSchemalessNoSchemaRegistryItSpec extends BaseKafkaJsonSchemalessItSpec {

  override def createFinkKafkaComponentProvider(schemaRegistryClientProvider: MockSchemaRegistryClientProvider) =
    new FlinkKafkaComponentProvider()

  override protected def maybeAddSchemaRegistryUrl(config: Config): Config = config

  test("should round-trip json message without schema registry") {
    shouldRoundTripJsonMessageWithoutProvidedSchema()
  }

  ignore("should round-trip plain message without schema registry") {
    shouldRoundTripPlainMessageWithoutProvidedSchema()
  }

}
