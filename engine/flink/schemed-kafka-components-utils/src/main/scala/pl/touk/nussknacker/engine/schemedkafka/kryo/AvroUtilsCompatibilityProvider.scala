package pl.touk.nussknacker.engine.schemedkafka.kryo

import org.apache.avro.generic.GenericData
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.java.typeutils.AvroUtils

trait AvroUtilsWrapper {
  def addAvroSerializersIfRequired(executionConfig: ExecutionConfig): Unit
}

trait AvroUtilsCompatibilityProvider extends AvroUtilsWrapper

object DefaultAvroUtils extends AvroUtilsWrapper {

  def addAvroSerializersIfRequired(executionConfig: ExecutionConfig): Unit = {
    AvroUtils.getAvroUtils.addAvroSerializersIfRequired(
      executionConfig.getSerializerConfig,
      classOf[GenericData.Record]
    )
  }

}
