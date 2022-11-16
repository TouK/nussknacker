package pl.touk.nussknacker.engine.json

import org.json.{JSONArray, JSONObject}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.collection.JavaConverters._

class JsonSchemaUtilsTest extends AnyFunSuite with Matchers {
  test("should convert to org.everit.json") {
    val expected = new JSONObject(Map(
      "obj" -> new JSONObject(Map(
        "array" -> new JSONArray(List("1", "2").asJava)
      ).asJava),
      "jObj" -> new JSONObject(Map(
        "array" -> new JSONArray(List(1, 2).asJava),
      ).asJava),
      "int" -> 1,
      "str" -> "str",
      "boolean" -> true,
      "null" -> JSONObject.NULL
    ).asJava)

    val json = JsonSchemaUtils.toJson(Map(
      "obj" -> Map(
        "array" -> List("1", "2")
      ),
      "jObj" -> Map(
        "array" -> List(1, 2).asJava,
      ).asJava,
      "int" -> 1,
      "str" -> "str",
      "boolean" -> true,
      "null" -> None
    )).asInstanceOf[JSONObject]

    compare(json, expected)
  }

  //JSONObject and JSONArray are primitives.. And comparing is done by checking reference..
  private def compare(obj: Any, expected: Any): Any = (obj, expected) match {
    case (o: JSONObject, e: JSONObject) =>
      o.keys().asScala.foreach{ key =>
        compare(o.get(key), e.get(key))
      }
    case (o: JSONArray, e: JSONArray) =>
      o.toList.asScala.zipWithIndex.foreach{ case (el, index) =>
        compare(el, e.get(index))
      }
    case (_, _) => obj shouldBe expected
  }
}
