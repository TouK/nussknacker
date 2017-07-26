package pl.touk.nussknacker.engine.example

object UtilProcessHelper {

  import argonaut.Argonaut._

  import scala.collection.JavaConversions._

  def mapToJson(map: java.util.Map[String, String]) = {
    map.toMap.asJson.nospaces
  }

}
