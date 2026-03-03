package pl.touk.nussknacker.engine.process.util

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.serialization.SerializerConfigImpl
import org.apache.flink.api.java.typeutils.AvroUtils

object Serializers extends LazyLogging {

  private val genericRecordClassName = "org.apache.avro.generic.GenericData$Record"

  def registerSerializers(modelClassLoader: ClassLoader, executionConfig: ExecutionConfig): Unit = {
    addAvroSerializersWhenAvroIsAvailableOnClasspath(modelClassLoader, executionConfig)
  }

  private def addAvroSerializersWhenAvroIsAvailableOnClasspath(
      modelClassLoader: ClassLoader,
      executionConfig: ExecutionConfig
  ): Unit = {
    // do not replace with 'tryToLoadClass', it may return a different Class instance than 'loadClass'
    val genericRecordClass =
      try {
        modelClassLoader.loadClass(genericRecordClassName)
      } catch {
        case _: ClassNotFoundException =>
          logger.debug(
            s"$genericRecordClassName is not available on classpath. Skipping default avro-kryo serializers registration"
          )
          return
      }

    logger.debug(s"$genericRecordClassName is available on classpath. Registering default avro-kryo serializers")
    val serializerConfig = executionConfig.getSerializerConfig.asInstanceOf[SerializerConfigImpl]
    AvroUtils.getAvroUtils.addAvroSerializersIfRequired(serializerConfig, genericRecordClass)
  }

}
