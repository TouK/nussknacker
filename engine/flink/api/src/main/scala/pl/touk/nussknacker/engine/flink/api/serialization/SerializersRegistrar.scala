package pl.touk.nussknacker.engine.flink.api.serialization

import org.apache.flink.api.common.ExecutionConfig

trait SerializersRegistrar {

  def register(config: ExecutionConfig): Unit

}

trait BaseSerializersRegistrar extends SerializersRegistrar {

  protected def serializers: List[SerializerWithSpecifiedClass[_]]

  override def register(config: ExecutionConfig): Unit = {
    serializers.foreach(_.registerIn(config))
  }

}
