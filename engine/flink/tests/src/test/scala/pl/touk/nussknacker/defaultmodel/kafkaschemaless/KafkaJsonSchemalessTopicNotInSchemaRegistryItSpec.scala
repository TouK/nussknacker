package pl.touk.nussknacker.defaultmodel.kafkaschemaless

class KafkaJsonSchemalessTopicNotInSchemaRegistryItSpec extends BaseKafkaJsonSchemalessItSpec {

  test("should round-trip json message when topic is not in schema registry") {
    shouldRoundTripJsonMessageWithoutProvidedSchema()
  }

  ignore("should round-trip plain message when topic is not in schema registry") {
    shouldRoundTripPlainMessageWithoutProvidedSchema()
  }

}
