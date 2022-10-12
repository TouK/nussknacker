package pl.touk.nussknacker.engine.kafka.consumerrecord

import com.typesafe.config.ConfigFactory
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.record.TimestampType
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.process.EmptyProcessConfigCreator
import pl.touk.nussknacker.engine.flink.serialization.FlinkTypeInformationSerializationMixin
import pl.touk.nussknacker.engine.kafka.KafkaSpec
import pl.touk.nussknacker.engine.kafka.source.flink.KafkaSourceFactoryMixin
import pl.touk.nussknacker.engine.kafka.source.flink.KafkaSourceFactoryMixin.{SampleKey, SampleValue}
import pl.touk.nussknacker.engine.process.util.Serializers
import pl.touk.nussknacker.engine.testing.LocalModelData

import java.util.Optional

class ConsumerRecordDeserializationSpec extends AnyFunSuite with Matchers with KafkaSpec with KafkaSourceFactoryMixin with FlinkTypeInformationSerializationMixin {

  private val emptyModel = LocalModelData(ConfigFactory.empty(), new EmptyProcessConfigCreator)

  type TestConsumerRecord = ConsumerRecord[SampleKey, SampleValue]
  test("should serialize and deserialize ConsumerRecord with TypeInformation serializer") {
    val givenObj: TestConsumerRecord = new TestConsumerRecord("loremIpsum", 11, 22L, constTimestamp, TimestampType.CREATE_TIME,
      ConsumerRecord.NULL_CHECKSUM.longValue(), 44, 55, sampleKey, sampleValue, sampleHeaders, Optional.empty[Integer])

    val typeInformation: TypeInformation[TestConsumerRecord] = TypeInformation.of(classOf[TestConsumerRecord])

    Serializers.registerSerializers(emptyModel, executionConfigWithoutKryo)
    intercept[Exception] {
      getSerializeRoundTrip(givenObj, typeInformation, executionConfigWithoutKryo)
    }
    
    Serializers.registerSerializers(emptyModel, executionConfigWithKryo)
    val out = getSerializeRoundTrip(givenObj, typeInformation, executionConfigWithKryo)
    checkResult(out, givenObj)
  }
}
