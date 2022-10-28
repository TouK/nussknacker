package pl.touk.nussknacker.engine.kafka.generic

import org.apache.flink.api.common.typeinfo.{TypeHint, TypeInformation}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.flink.util.source.EspDeserializationSchema
import pl.touk.nussknacker.engine.kafka.consumerrecord.FixedValueDeserializationSchemaFactory
import pl.touk.nussknacker.engine.kafka.serialization.schemas._
import pl.touk.nussknacker.engine.kafka.source.KafkaSourceFactory
import pl.touk.nussknacker.engine.kafka.source.flink.FlinkKafkaSourceImplFactory

//TODO: Move it to source package
object sources {

  class GenericJsonSourceFactory(processObjectDependencies: ProcessObjectDependencies) extends KafkaSourceFactory[String, java.util.Map[_, _]](
    new FixedValueDeserializationSchemaFactory(JsonMapDeserialization), jsonFormatterFactory, processObjectDependencies, new FlinkKafkaSourceImplFactory(None))

  object JsonMapDeserialization extends EspDeserializationSchema[java.util.Map[_, _]](deserializeToMap)(TypeInformation.of(new TypeHint[java.util.Map[_, _]] {}))

}
