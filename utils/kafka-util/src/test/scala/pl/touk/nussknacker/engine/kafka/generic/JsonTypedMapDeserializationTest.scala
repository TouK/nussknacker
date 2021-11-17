package pl.touk.nussknacker.engine.kafka.generic

import io.circe.Json
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.kafka.serialization.schemas.deserializeToTypedMap

import java.nio.charset.StandardCharsets

class JsonTypedMapDeserializationTest extends FunSuite with Matchers {

  import scala.collection.JavaConverters._

  test("should deserialize to java object") {

    val json = Json.fromFields(List(
      "arrayF" -> Json.fromValues(
        List(
          Json.fromString("one"),
          Json.fromBoolean(true),
          Json.fromDoubleOrNull(1.1),
          Json.fromFields(List("nest1" -> Json.fromString("str1")))
        )
      ),
      "mapF" -> Json.fromFields(List(
        "nested1" -> Json.fromString("str2")
      ))
    )).noSpaces.getBytes(StandardCharsets.UTF_8)

    deserializeToTypedMap(json) shouldBe TypedMap(Map(
      "arrayF" -> List(
        "one", true, 1.1, Map("nest1" -> "str1").asJava
      ).asJava,
      "mapF" -> Map("nested1" -> "str2").asJava
    ))
  }

}
