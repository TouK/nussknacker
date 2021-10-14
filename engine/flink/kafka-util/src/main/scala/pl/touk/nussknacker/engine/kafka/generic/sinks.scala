package pl.touk.nussknacker.engine.kafka.generic

import java.util.UUID
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.kafka.serialization.schemas.SimpleSerializationSchema
import pl.touk.nussknacker.engine.kafka.sink.flink.KafkaSinkFactory
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder

//TODO: Move it to sink package
object sinks {

  private val encoder = BestEffortJsonEncoder(failOnUnkown = false, getClass.getClassLoader)

  class GenericKafkaJsonSink(processObjectDependencies: ProcessObjectDependencies)
    extends KafkaSinkFactory(GenericJsonSerialization, processObjectDependencies)

  case class GenericJsonSerialization(topic: String) extends SimpleSerializationSchema[AnyRef](topic, element => {
    // TODO: would be safer if will be added expected type in Sink and during expression evaluation,
    //       would be performed conversion to it
    encoder.encode(element).spaces2
    //UUID is *not* performant enough when volume is high...
  }, _ => UUID.randomUUID().toString)
}
