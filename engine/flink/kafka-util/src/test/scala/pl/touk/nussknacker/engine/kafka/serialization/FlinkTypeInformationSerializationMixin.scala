package pl.touk.nussknacker.engine.kafka.serialization

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.common.typeutils.TypeSerializer
import org.apache.flink.core.memory.{DataInputViewStreamWrapper, DataOutputViewStreamWrapper}
import org.scalatest.{Assertion, Matchers}

trait FlinkTypeInformationSerializationMixin extends Matchers {

  protected val executionConfigWithoutKryo: ExecutionConfig = new ExecutionConfig {
    disableGenericTypes()
  }

  protected val executionConfigWithKryo: ExecutionConfig = new ExecutionConfig {
    enableGenericTypes()
  }

  protected def getSerializeRoundTrip[T](record: T, typeInfo: TypeInformation[T], executionConfig: ExecutionConfig = executionConfigWithoutKryo): T = {
    val serializer = typeInfo.createSerializer(executionConfig)
    getSerializeRoundTripWithSerializers(record, serializer, serializer)
  }

  protected def getSerializeRoundTripWithSerializers[T](record: T,
                                                        toSerialize: TypeSerializer[T],
                                                        toDeserialize: TypeSerializer[T]): T = {
    val data = new ByteArrayOutputStream(10 * 1024)
    toSerialize.serialize(record, new DataOutputViewStreamWrapper(data))
    val input = data.toByteArray
    toDeserialize.deserialize(new DataInputViewStreamWrapper(new ByteArrayInputStream(input)))
  }

  protected def serializeRoundTrip[T](record: T, typeInfo: TypeInformation[T], executionConfig: ExecutionConfig = executionConfigWithoutKryo)(expected:T = record): Assertion = {
    getSerializeRoundTrip(record, typeInfo, executionConfig) shouldBe expected
  }

  protected def serializeRoundTripWithSerializers[T](record: T,
                                                     toSerialize: TypeSerializer[T],
                                                     toDeserialize: TypeSerializer[T])(expected:T = record): Assertion = {
    getSerializeRoundTripWithSerializers(record, toSerialize, toDeserialize) shouldBe expected
  }

}
