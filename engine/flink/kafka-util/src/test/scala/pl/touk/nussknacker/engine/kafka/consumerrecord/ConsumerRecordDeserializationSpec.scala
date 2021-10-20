package pl.touk.nussknacker.engine.kafka.consumerrecord

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.record.TimestampType
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.flink.serialization.FlinkTypeInformationSerializationMixin
import pl.touk.nussknacker.engine.kafka.source.flink.KafkaSourceFactoryMixin.{SampleKey, SampleValue, sampleKeyJsonDeserializer, sampleValueJsonDeserializer}
import pl.touk.nussknacker.engine.kafka.source.flink.{KafkaSourceFactoryMixin, SampleConsumerRecordDeserializationSchemaFactory}
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaSpec}
import pl.touk.nussknacker.engine.util.namespaces.ObjectNamingProvider

import scala.reflect.classTag

class ConsumerRecordDeserializationSpec extends FunSuite with Matchers with KafkaSpec with KafkaSourceFactoryMixin with FlinkTypeInformationSerializationMixin {

  type TestConsumerRecord = ConsumerRecord[SampleKey, SampleValue]
  test("should serialize and deserialize ConsumerRecord with TypeInformation serializer") {
    val processObjectDependencies = ProcessObjectDependencies(config, ObjectNamingProvider(getClass.getClassLoader))
    val kafkaConfig = KafkaConfig.parseProcessObjectDependencies(processObjectDependencies)

    val givenObj: TestConsumerRecord = new TestConsumerRecord("loremIpsum", 11, 22L, constTimestamp, TimestampType.CREATE_TIME, 33L, 44, 55, sampleKey, sampleValue, sampleHeaders)

    val deserializationSchemaFactory = new SampleConsumerRecordDeserializationSchemaFactory(sampleKeyJsonDeserializer, sampleValueJsonDeserializer)
    val value1 = deserializationSchemaFactory.create(List("dummyTopic"), kafkaConfig)
    val typeInformation: TypeInformation[TestConsumerRecord] = TypeInformation.of(classTag[TestConsumerRecord].runtimeClass.asInstanceOf[Class[TestConsumerRecord]])

    intercept[Exception] {
      getSerializeRoundTrip(givenObj, typeInformation, executionConfigWithoutKryo)
    }
    val out = getSerializeRoundTrip(givenObj, typeInformation, executionConfigWithKryo)
    checkResult(out, givenObj)
  }
}
