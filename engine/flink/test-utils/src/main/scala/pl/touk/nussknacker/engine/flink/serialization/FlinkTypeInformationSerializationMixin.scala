package pl.touk.nussknacker.engine.flink.serialization

import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.serialization.SerializerConfig
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.common.typeutils.TypeSerializer
import org.apache.flink.configuration.{Configuration, PipelineOptions}
import org.apache.flink.core.memory.{DataInputViewStreamWrapper, DataOutputViewStreamWrapper}
import org.scalatest.Assertion
import org.scalatest.matchers.should.Matchers

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

trait FlinkTypeInformationSerializationMixin extends Matchers {

  protected val serializerConfigWithoutKryo: SerializerConfig = new ExecutionConfig(
    new Configuration().set[java.lang.Boolean](PipelineOptions.GENERIC_TYPES, false)
  ).getSerializerConfig

  protected val serializerConfigWithKryo: SerializerConfig = new ExecutionConfig(
    new Configuration().set[java.lang.Boolean](PipelineOptions.GENERIC_TYPES, true)
  ).getSerializerConfig

  protected def getSerializeRoundTrip[T](
      record: T,
      typeInfo: TypeInformation[T],
      serializerConfig: SerializerConfig = serializerConfigWithoutKryo
  ): T = {
    val serializer = typeInfo.createSerializer(serializerConfig)
    getSerializeRoundTripWithSerializers(record, serializer, serializer)
  }

  protected def getSerializeRoundTripWithSerializers[T](
      record: T,
      toSerialize: TypeSerializer[T],
      toDeserialize: TypeSerializer[T]
  ): T = {
    val data = new ByteArrayOutputStream(10 * 1024)
    toSerialize.serialize(record, new DataOutputViewStreamWrapper(data))
    val input = data.toByteArray
    toDeserialize.deserialize(new DataInputViewStreamWrapper(new ByteArrayInputStream(input)))
  }

  protected def serializeRoundTrip[T](
      record: T,
      typeInfo: TypeInformation[T],
      serializerConfig: SerializerConfig = serializerConfigWithoutKryo
  )(expected: T = record): Assertion = {
    getSerializeRoundTrip(record, typeInfo, serializerConfig) shouldBe expected
  }

  protected def serializeRoundTripWithSerializers[T](
      record: T,
      toSerialize: TypeSerializer[T],
      toDeserialize: TypeSerializer[T]
  )(expected: T = record): Assertion = {
    getSerializeRoundTripWithSerializers(record, toSerialize, toDeserialize) shouldBe expected
  }

}
