package pl.touk.nussknacker.engine.example

import io.circe.Encoder
import pl.touk.nussknacker.engine.api.{Documentation, ParamName}

import scala.util.Random

object UtilProcessHelper {

  import scala.collection.JavaConverters._

  @Documentation(description = "Convert map to JSON")
  def mapToJson(map: java.util.Map[String, String]): String = {
    Encoder.encodeMap[String, String].apply(map.asScala.toMap).noSpaces
  }

  @Documentation(description = "Get random number")
  def random(@ParamName("To (exclusive)") to: Int): Unit = {
    Random.nextInt(to)

  }
}
