package pl.touk.nussknacker.engine.kafka.sink

import pl.touk.nussknacker.engine.kafka.serialization.schemas.SimpleSerializationSchema
import pl.touk.nussknacker.engine.kafka.sink.GenericJsonSerialization._
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder

import java.util.UUID

case class GenericJsonSerialization(topic: String) extends SimpleSerializationSchema[AnyRef](topic, element => {
  // TODO: would be safer if will be added expected type in Sink and during expression evaluation,
  //       would be performed conversion to it
  encoder.encode(element).spaces2
  //UUID is *not* performant enough when volume is high...
}, _ => UUID.randomUUID().toString)

object GenericJsonSerialization {

  private val encoder = BestEffortJsonEncoder(failOnUnkown = false, getClass.getClassLoader)

}