package pl.touk.nussknacker.engine.process.util

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.ExecutionConfig
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.flink.api.serialization.SerializersRegistrar
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader

object Serializers extends LazyLogging {

  def registerSerializers(
      modelData: ModelData,
      extraSerializersRegistrars: List[SerializersRegistrar],
      executionConfig: ExecutionConfig,
  ): Unit = {
    (ScalaServiceLoader
      .load[SerializersRegistrar](getClass.getClassLoader) ++ extraSerializersRegistrars)
      .foreach(_.register(modelData.modelConfig.underlyingConfig, executionConfig))
  }

}
