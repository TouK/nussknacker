package pl.touk.nussknacker.defaultmodel.kafkaschemaless

class KafkaJsonSchemalessTopicNotInSchemaRegistryItSpec extends BaseKafkaJsonSchemalessItSpec {

  test("should round-trip json message when topic is not in schema registry") {
    shouldRoundTripJsonMessageWithoutProvidedSchema()
  }

  test("should round-trip json message without schema derived from data sample") {
    shouldRoundTripJsonMessageWithSchemaDerivedFromProvidedDataSample()
  }

  test("should round-trip json message with schema derived from data sample") {
    shouldRoundTripJsonMessageWithoutSchemaDerivedFromProvidedDataSample()
  }

  ignore("should round-trip plain message when topic is not in schema registry") {
    shouldRoundTripPlainMessageWithoutProvidedSchema()
  }

}
