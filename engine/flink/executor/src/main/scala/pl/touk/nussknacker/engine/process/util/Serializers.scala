package pl.touk.nussknacker.engine.process.util

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.ExecutionConfig
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.flink.api.serialization.SerializersRegistrar
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader

/**
  * Watch out, serializers are also serialized. Incompatible SerializationUID on serializer class can lead process state loss (unable to continue from old snapshot).
  * This is why we set SerialVersionUID explicit.
  *
  * @see [[org.apache.flink.api.common.typeutils.TypeSerializerSnapshotSerializationUtil#writeSerializersAndConfigsWithResilience]]
  * @see [[org.apache.flink.api.common.typeutils.TypeSerializerSnapshotSerializationUtil#readSerializersAndConfigsWithResilience]]
  */
object Serializers extends LazyLogging {

  def registerSerializers(
      modelData: ModelData,
      config: ExecutionConfig
  ): Unit = {
    (ScalaServiceLoader
      .load[SerializersRegistrar](getClass.getClassLoader))
      .foreach(_.register(modelData.modelConfig.underlyingConfig, config))
    TimeSerializers.addDefaultSerializers(config)
  }

}
