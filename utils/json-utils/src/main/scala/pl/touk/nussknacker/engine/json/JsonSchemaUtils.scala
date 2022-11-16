package pl.touk.nussknacker.engine.json
import org.json.{JSONArray, JSONObject}

import java.util
import collection.JavaConverters._

object JsonSchemaUtils {

  def toJson(value: Any): Any = value match {
    case map: collection.Map[String@unchecked, _] =>
      new JSONObject(map.mapValues(toJson).asJava)
    case map: java.util.Map[String@unchecked, _] =>
      new JSONObject(map.asScala.mapValues(toJson).asJava)
    case collection: Traversable[_] =>
      new JSONArray(collection.map(toJson).toList.asJava)
    case collection: util.Collection[_] =>
      new JSONArray(collection.asScala.map(toJson).asJava)
    case None =>
      JSONObject.NULL
    case any =>
      JSONObject.wrap(any)
  }

}
