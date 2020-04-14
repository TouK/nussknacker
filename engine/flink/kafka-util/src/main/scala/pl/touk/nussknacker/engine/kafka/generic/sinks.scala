package pl.touk.nussknacker.engine.kafka.generic

import java.util.UUID

import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.kafka.KafkaSinkFactory
import pl.touk.nussknacker.engine.kafka.serialization.schemas.SimpleSerializationSchema
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder

object sinks {

  private val encoder = BestEffortJsonEncoder(failOnUnkown = false)

  class GenericKafkaJsonSink(processObjectDependencies: ProcessObjectDependencies)
    extends KafkaSinkFactory(GenericJsonSerialization, processObjectDependencies)

  case class GenericJsonSerialization(topic: String) extends SimpleSerializationSchema[Any](topic, element => {
      val objToEncode = element match {
        // TODO: would be safer if will be added expected type in Sink and during expression evaluation,
        // would be performed conversion to it
        case TypedMap(fields) => fields
        case other => other
      }
      encoder.encode(objToEncode).spaces2
    //UUID is *not* performant enough when volume is high...
    }, _ => UUID.randomUUID().toString)

}
