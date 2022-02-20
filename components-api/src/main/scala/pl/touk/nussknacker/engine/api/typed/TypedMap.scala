package pl.touk.nussknacker.engine.api.typed

import java.util.Collections
import java.{util => ju}

/**
 * The idea of this class is to be something like java bean with properties represented by Map entries.
 * If you use this class as a global variables, it will be typed using `TypedObjectTypingResult`.
 * Just like in java bean, case when property was typed correctly and is missing during runtime is something that
 * should not happen and is treated Exceptionally. Check `MapPropertyAccessor.canRead` for more details.
 */
class TypedMap(map: ju.Map[String, Any]) extends ju.HashMap[String, Any](map) {

  def this() =
    this(Collections.emptyMap())

}

object TypedMap {

  import scala.collection.JavaConverters._

  def apply(scalaFields: Map[String, Any]): TypedMap = {
    new TypedMap(scalaFields.asJava)
  }

}
