package pl.touk.nussknacker.engine.benchmarks.serialization

import com.github.ghik.silencer.silent
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.core.memory.{DataInputViewStreamWrapper, DataOutputViewStreamWrapper}

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

class SerializationBenchmarkSetup[T](
    typeInfo: TypeInformation[T],
    val record: T,
    prepareConfig: ExecutionConfig => Unit = _ => {}
) extends LazyLogging {

  private val config = {
    val c = new ExecutionConfig
    prepareConfig(c)
    c
  }

  private val data = new ByteArrayOutputStream(10 * 1024)

  @silent("deprecated")
  private val serializer = typeInfo.createSerializer(config)

  {
    serializer.serialize(record, new DataOutputViewStreamWrapper(data))
    logger.debug(s"Size: ${data.size()}")
  }

  def roundTripSerialization(): (T, Long) = {
    data.reset()
    serializer.serialize(record, new DataOutputViewStreamWrapper(data))
    val input = data.toByteArray
    val out   = serializer.deserialize(new DataInputViewStreamWrapper(new ByteArrayInputStream(input)))
    (out, input.length)
  }

}
