package pl.touk.nussknacker.engine.kafka.source

import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypedObjectTypingResult, TypingResult}

import scala.reflect.ClassTag

case class InputMeta[K](key: K, topic: String, partition: Int, offset: java.lang.Long, timestamp: java.lang.Long, headers: java.util.Map[String, String])

object InputMeta {

  def withType[K: ClassTag]: typing.TypingResult = {
    val keyTypingResult = Typed[K]
    TypedObjectTypingResult(fields(keyTypingResult), objType(keyTypingResult))
  }

  def withType(keyTypingResult: typing.TypingResult): typing.TypingResult = {
    TypedObjectTypingResult(fields(keyTypingResult), objType(keyTypingResult))
  }

  private def fields(keyTypingResult: typing.TypingResult): Map[String, TypingResult] = {
    Map(
      "key" -> keyTypingResult,
      "topic" -> Typed[String],
      "partition" -> Typed[Int],
      "offset" -> Typed[java.lang.Long],
      "timestamp" -> Typed[java.lang.Long],
      "headers" -> Typed.genericTypeClass[java.util.Map[_, _]](List(Typed[String], Typed[String]))
    )
  }
  private def objType[K: ClassTag](keyTypingResult: typing.TypingResult): TypedClass = {
    Typed.genericTypeClass[InputMeta[_]](List(keyTypingResult)).asInstanceOf[TypedClass]
  }
}