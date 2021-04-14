package pl.touk.nussknacker.engine.api.typed

import java.util.function.BiConsumer
import java.{util => ju}
import scala.collection.immutable.ListMap
import scala.collection.mutable

/**
 * The idea of this class is to be something like java bean with properties represented by Map entries.
 * If you use this class as a global variables, it will be typed using `TypedObjectTypingResult`.
 * Just like in java bean, case when property was typed correctly and is missing during runtime is something that
 * should not happen and is treated Exceptionally. Check `MapPropertyAccessor.canRead` for more details.
 */
class TypedMap(map: ju.LinkedHashMap[String, Any]) extends ju.LinkedHashMap[String, Any](map) {

  def this() = this(new ju.LinkedHashMap)

  def toScalaListMap: ListMap[String, Any] = {
    val arr = new mutable.ArrayBuffer[(String, Any)]()
    map.forEach((k: String, v: Any) => arr.prepend((k, v)))
    ListMap(arr: _*)
  }
}

object TypedMap {

  def apply(scalaFields: ListMap[String, Any]): TypedMap = {
    val jMap = new ju.LinkedHashMap[String, Any]
    scalaFields.foreach { case (name, value) =>
      jMap.put(name, value)
    }
    new TypedMap(jMap)
  }
}
