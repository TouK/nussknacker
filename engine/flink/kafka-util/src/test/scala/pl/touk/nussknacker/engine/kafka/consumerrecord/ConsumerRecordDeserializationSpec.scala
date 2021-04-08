package pl.touk.nussknacker.engine.kafka.consumerrecord

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.core.memory.{DataInputViewStreamWrapper, DataOutputViewStreamWrapper}
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.record.TimestampType
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.kafka.{ConsumerRecordUtils, KafkaConfig, KafkaSpec}
import pl.touk.nussknacker.engine.kafka.util.KafkaGenericNodeMixin
import pl.touk.nussknacker.engine.kafka.util.KafkaGenericNodeMixin.{SampleKey, SampleValue, sampleKeyDeserializationSchema, sampleValueDeserializationSchema}
import pl.touk.nussknacker.engine.util.namespaces.ObjectNamingProvider


class ConsumerRecordDeserializationSpec extends FunSuite with Matchers with KafkaSpec with KafkaGenericNodeMixin {

  test("should serialize and deserialize ConsumerRecord with TypeInformation serializer") {
    val processObjectDependencies = ProcessObjectDependencies(config, ObjectNamingProvider(getClass.getClassLoader))
    val kafkaConfig = KafkaConfig.parseProcessObjectDependencies(processObjectDependencies)

    val givenObj = new ConsumerRecord[SampleKey, SampleValue]("loremIpsum", 11, 22L, constTimestamp, TimestampType.CREATE_TIME, 33L, 44, 55, sampleKey, sampleValue, ConsumerRecordUtils.toHeaders(sampleHeaders))

    val deserializationSchemaFactory = new ConsumerRecordDeserializationSchemaFactory(sampleKeyDeserializationSchema, sampleValueDeserializationSchema)
    val typeInformation = deserializationSchemaFactory.create(List("dummyTopic"), kafkaConfig).getProducedType
    val serializer = typeInformation.createSerializer(new ExecutionConfig)

    //roundTrip Serialize And Deserialize
    val data = new ByteArrayOutputStream(10 * 1024)
    serializer.serialize(givenObj, new DataOutputViewStreamWrapper(data))
    val input = data.toByteArray
    val out = serializer.deserialize(new DataInputViewStreamWrapper(new ByteArrayInputStream(input)))

    checkResult(out, givenObj)
  }

}
