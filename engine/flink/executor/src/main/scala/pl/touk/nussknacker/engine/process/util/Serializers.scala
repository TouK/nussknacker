package pl.touk.nussknacker.engine.process.util

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.java.typeutils.AvroUtils

import scala.reflect.internal.util.ScalaClassLoader.apply

object Serializers extends LazyLogging {

  private val genericRecordClassName = "org.apache.avro.generic.GenericData$Record"

  def registerSerializers(
      modelClassLoader: ClassLoader,
      executionConfig: ExecutionConfig
  ): Unit = {
    modelClassLoader
      .tryToLoadClass(genericRecordClassName) match {
      case Some(genericRecordClass) =>
        logger.debug(s"$genericRecordClassName is available on classpath. Registering default avro-kryo serializers")
        AvroUtils.getAvroUtils.addAvroSerializersIfRequired(executionConfig.getSerializerConfig, genericRecordClass)
      case None =>
        logger.debug(
          s"$genericRecordClassName is not available on classpath. Skipping default avro-kryo serializers registration"
        )
    }
    TimeSerializers.addDefaultSerializers(executionConfig)
  }

}
