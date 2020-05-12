package pl.touk.nussknacker.engine.api.typed

import java.util.Collections
import java.{util => ju}

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
