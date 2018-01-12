package pl.touk.nussknacker.engine.api.typed

import scala.collection.JavaConversions._

case class TypedMapDefinition(fields: Map[String, ClazzRef])

object TypedMapDefinition {

  def create(fields: java.util.Map[String, String]) : TypedMapDefinition = {
    TypedMapDefinition(fields.toMap.mapValues(ClazzRef(_)))
  }

}

