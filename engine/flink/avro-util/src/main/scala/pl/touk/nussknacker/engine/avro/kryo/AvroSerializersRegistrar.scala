package pl.touk.nussknacker.engine.avro.kryo

import com.typesafe.scalalogging.LazyLogging
import org.apache.avro.generic.GenericData
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.java.typeutils.AvroUtils
import pl.touk.nussknacker.engine.process.util.SerializersRegistrar

// We need it because we use avro records inside our Context class
class AvroSerializersRegistrar extends SerializersRegistrar with LazyLogging {

  override def register(config: ExecutionConfig): Unit = {
    logger.debug("Registering default avro serializers")
    AvroUtils.getAvroUtils.addAvroSerializersIfRequired(config, classOf[GenericData.Record])
  }

}
