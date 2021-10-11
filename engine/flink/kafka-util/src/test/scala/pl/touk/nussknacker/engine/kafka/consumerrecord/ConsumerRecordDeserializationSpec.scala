package pl.touk.nussknacker.engine.kafka.consumerrecord

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.record.TimestampType
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.flink.serialization.FlinkTypeInformationSerializationMixin
import pl.touk.nussknacker.engine.kafka.source.{KafkaFlinkSourceFactoryMixin, SampleConsumerRecordDeserializationSchemaFactory}
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaSpec}
import pl.touk.nussknacker.engine.kafka.source.KafkaFlinkSourceFactoryMixin.{SampleKey, SampleValue, sampleKeyJsonDeserializer, sampleValueJsonDeserializer}
import pl.touk.nussknacker.engine.util.namespaces.ObjectNamingProvider

class ConsumerRecordDeserializationSpec extends FunSuite with Matchers with KafkaSpec with KafkaFlinkSourceFactoryMixin with FlinkTypeInformationSerializationMixin {

  test("should serialize and deserialize ConsumerRecord with TypeInformation serializer") {
    val processObjectDependencies = ProcessObjectDependencies(config, ObjectNamingProvider(getClass.getClassLoader))
    val kafkaConfig = KafkaConfig.parseProcessObjectDependencies(processObjectDependencies)

    val givenObj = new ConsumerRecord[SampleKey, SampleValue]("loremIpsum", 11, 22L, constTimestamp, TimestampType.CREATE_TIME, 33L, 44, 55, sampleKey, sampleValue, sampleHeaders)

    val deserializationSchemaFactory = new SampleConsumerRecordDeserializationSchemaFactory(sampleKeyJsonDeserializer, sampleValueJsonDeserializer)
    val typeInformation = deserializationSchemaFactory.create(List("dummyTopic"), kafkaConfig).getProducedType

    intercept[Exception] {getSerializeRoundTrip(givenObj, typeInformation, executionConfigWithoutKryo)}
    val out = getSerializeRoundTrip(givenObj, typeInformation, executionConfigWithKryo)
    checkResult(out, givenObj)
  }

}
