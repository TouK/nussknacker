package pl.touk.nussknacker.engine.kafka.source

import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypedObjectTypingResult, TypingResult}

import scala.reflect.ClassTag

case class InputMeta[K](key: K, topic: String, partition: Int, offset: java.lang.Long, timestamp: java.lang.Long, headers: java.util.Map[String, String])

object InputMeta {

  // TODO: provide better type definition for InputMeta[AnyRef]
  // objType should contain K type information
  def withType(keyTypingResult: typing.TypingResult): typing.TypingResult = {
    TypedObjectTypingResult(
      Map(
        "key" -> keyTypingResult,
        "topic" -> Typed[String],
        "partition" -> Typed[Int],
        "offset" -> Typed[java.lang.Long],
        "timestamp" -> Typed[java.lang.Long],
        "headers" -> Typed.typedClass[Map[String, String]]
      ),
      Typed.typedClass[InputMeta[AnyRef]]
    )
  }
}