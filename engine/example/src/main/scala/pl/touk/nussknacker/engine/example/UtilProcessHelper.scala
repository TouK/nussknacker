package pl.touk.nussknacker.engine.example

import pl.touk.nussknacker.engine.api.{Documentation, ParamName}

import scala.util.Random

object UtilProcessHelper {

  import argonaut.Argonaut._

  import scala.collection.JavaConversions._

  @Documentation(description = "Convert map to JSON")
  def mapToJson(map: java.util.Map[String, String]) = {
    map.toMap.asJson.nospaces
  }

  @Documentation(description = "Get random number")
  def random(@ParamName("To (exclusive)") to: Int): Unit = {
    Random.nextInt(to)

  }
}
