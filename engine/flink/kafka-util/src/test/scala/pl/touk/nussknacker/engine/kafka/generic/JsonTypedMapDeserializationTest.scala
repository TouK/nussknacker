package pl.touk.nussknacker.engine.kafka.generic

import java.nio.charset.StandardCharsets

import com.google.common.collect.{ImmutableList, ImmutableMap}
import io.circe.Json
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.kafka.generic.sources.JsonTypedMapDeserialization

class JsonTypedMapDeserializationTest extends FunSuite with Matchers {

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

    JsonTypedMapDeserialization.deserialize(json) shouldBe TypedMap(Map(
      "arrayF" -> ImmutableList.of(
        "one", true, 1.1, ImmutableMap.of("nest1", "str1")
      ),
      "mapF" -> ImmutableMap.of("nested1", "str2")
    ))
  }

}
