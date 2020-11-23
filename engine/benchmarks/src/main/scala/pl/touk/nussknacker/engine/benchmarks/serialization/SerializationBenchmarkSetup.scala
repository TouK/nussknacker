package pl.touk.nussknacker.engine.benchmarks.serialization

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.core.memory.{DataInputViewStreamWrapper, DataOutputViewStreamWrapper}
import org.slf4j.LoggerFactory

class SerializationBenchmarkSetup[T](prepareConfig: ExecutionConfig => Unit, typeInfo: TypeInformation[T], val record: T) {

  private val config = {
    val c = new ExecutionConfig
    prepareConfig(c)
    c
  }

  private val data = new ByteArrayOutputStream(10* 1024)

  private val serializer = typeInfo.createSerializer(config)

  {
    LoggerFactory.getLogger(classOf[SerializationBenchmarkSetup[_]]).info(s"Data size: ${roundTripSerialization()._2}b")
  }

  def roundTripSerialization(): (T, Long) = {
    data.reset()
    serializer.serialize(record, new DataOutputViewStreamWrapper(data))
    val input = data.toByteArray
    val out = serializer.deserialize(new DataInputViewStreamWrapper(new ByteArrayInputStream(input)))
    (out, input.length)
  }

}

