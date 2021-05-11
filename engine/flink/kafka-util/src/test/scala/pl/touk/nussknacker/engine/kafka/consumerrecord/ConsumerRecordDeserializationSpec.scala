package pl.touk.nussknacker.engine.kafka.consumerrecord

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.common.typeutils.TypeSerializer
import org.apache.flink.api.scala.typeutils.{CaseClassTypeInfo, ScalaCaseClassSerializer}
import org.apache.flink.core.memory.{DataInputViewStreamWrapper, DataOutputViewStreamWrapper}
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.record.TimestampType
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.kafka.source.InputMeta
import pl.touk.nussknacker.engine.kafka._
import pl.touk.nussknacker.engine.kafka.SampleConsumerRecordDeserializationSchemaFactory
import pl.touk.nussknacker.engine.kafka.KafkaSourceFactoryMixin.{SampleKey, SampleValue, sampleKeyJsonDeserializer, sampleValueJsonDeserializer}
import pl.touk.nussknacker.engine.util.namespaces.ObjectNamingProvider

import scala.collection.JavaConverters._

class ConsumerRecordDeserializationSpec extends FunSuite with Matchers with KafkaSpec with KafkaSourceFactoryMixin {

  private val executionConfigWithoutKryo = new ExecutionConfig {
    disableGenericTypes()
  }

  private val executionConfigWithKryo = new ExecutionConfig {
    enableGenericTypes()
  }

  test("should serialize and deserialize ConsumerRecord with TypeInformation serializer") {
    val processObjectDependencies = ProcessObjectDependencies(config, ObjectNamingProvider(getClass.getClassLoader))
    val kafkaConfig = KafkaConfig.parseProcessObjectDependencies(processObjectDependencies)

    val givenObj = new ConsumerRecord[SampleKey, SampleValue]("loremIpsum", 11, 22L, constTimestamp, TimestampType.CREATE_TIME, 33L, 44, 55, sampleKey, sampleValue, ConsumerRecordUtils.toHeaders(sampleHeaders))

    val deserializationSchemaFactory = new SampleConsumerRecordDeserializationSchemaFactory(sampleKeyJsonDeserializer, sampleValueJsonDeserializer)
    val typeInformation = deserializationSchemaFactory.create(List("dummyTopic"), kafkaConfig).getProducedType

    intercept[Exception] {serializeRoundTrip(givenObj, typeInformation, executionConfigWithoutKryo)()}
    val out = serializeRoundTrip(givenObj, typeInformation, executionConfigWithKryo)()
    checkResult(out, givenObj)
  }

  test("should serialize and deserialize input metadata with TypeInformation serializer") {
    val sampleKeyFieldTypes = List(TypeInformation.of(classOf[String]), TypeInformation.of(classOf[Long]))
    val sampleKeyTypeInformation = new CaseClassTypeInfo[SampleKey](classOf[SampleKey], Array.empty, sampleKeyFieldTypes, List("partOne", "partTwo")){
      override def createSerializer(config: ExecutionConfig): TypeSerializer[SampleKey] =
        new ScalaCaseClassSerializer[SampleKey](classOf[SampleKey], sampleKeyFieldTypes.map(_.createSerializer(config)).toArray)
    }

    val typeInformation = InputMeta.typeInformation[SampleKey](sampleKeyTypeInformation)
    val givenObj = InputMeta[SampleKey](SampleKey("one", 2), "dummy", 3, 4L, 5L, TimestampType.CREATE_TIME, Map("one" -> "header value", "two" -> null).asJava, 6)

    serializeRoundTrip(givenObj, typeInformation, executionConfigWithoutKryo)()
    serializeRoundTrip(givenObj, typeInformation, executionConfigWithKryo)()
  }

  private def serializeRoundTrip[T](record: T, typeInfo: TypeInformation[T], executionConfig: ExecutionConfig = executionConfigWithoutKryo)(expected:T = record): T = {
    val serializer = typeInfo.createSerializer(executionConfig)
    serializeRoundTripWithSerializers(record, serializer, serializer)(expected)
  }

  private def serializeRoundTripWithSerializers[T](record: T,
                                                   toSerialize: TypeSerializer[T],
                                                   toDeserialize: TypeSerializer[T])(expected:T = record): T = {
    val data = new ByteArrayOutputStream(10 * 1024)
    toSerialize.serialize(record, new DataOutputViewStreamWrapper(data))
    val input = data.toByteArray
    val out = toDeserialize.deserialize(new DataInputViewStreamWrapper(new ByteArrayInputStream(input)))
    out
  }

}
