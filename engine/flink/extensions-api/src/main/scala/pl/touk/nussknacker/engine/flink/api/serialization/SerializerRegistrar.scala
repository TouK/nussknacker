package pl.touk.nussknacker.engine.flink.api.serialization

import com.esotericsoftware.kryo.Serializer
import org.apache.flink.api.common.ExecutionConfig

trait SerializerRegistrar[S <: Serializer[_]] {

  def registerIn(config: ExecutionConfig): Unit

}
