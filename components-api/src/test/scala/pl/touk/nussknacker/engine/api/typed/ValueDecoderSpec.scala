package pl.touk.nussknacker.engine.api.typed

import com.typesafe.scalalogging.LazyLogging
import io.circe.Json
import io.circe.syntax.EncoderOps
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.test.EitherValuesDetailedMessage

import scala.jdk.CollectionConverters._

class ValueDecoderSpec extends AnyFunSuite with EitherValuesDetailedMessage with Matchers with LazyLogging {

  test("decodeValue should decode Record fields correctly when all fields are present") {
    val typedRecord = Typed.record(
      Map(
        "name" -> Typed.fromInstance("Alice"),
        "age"  -> Typed.fromInstance(30)
      )
    )

    val json = Json.obj(
      "name" -> "Alice".asJson,
      "age"  -> 30.asJson
    )

    ValueDecoder.decodeValue(typedRecord, json.hcursor) shouldEqual Right(
      Map(
        "name" -> "Alice",
        "age"  -> 30
      ).asJava
    )
  }

  test("decodeValue should fail when a required Record field is missing") {
    val typedRecord = Typed.record(
      Map(
        "name" -> Typed.fromInstance("Alice"),
        "age"  -> Typed.fromInstance(30)
      )
    )

    val json = Json.obj(
      "name" -> "Alice".asJson
    )

    ValueDecoder.decodeValue(typedRecord, json.hcursor).leftValue.message should include(
      "Record field 'age' isn't present in encoded Record fields"
    )
  }

  test("decodeValue should not include extra fields that aren't typed") {
    val typedRecord = Typed.record(
      Map(
        "name" -> Typed.fromInstance("Alice"),
        "age"  -> Typed.fromInstance(30)
      )
    )

    val json = Json.obj(
      "name"       -> "Alice".asJson,
      "age"        -> 30.asJson,
      "occupation" -> "nurse".asJson,
    )

    ValueDecoder.decodeValue(typedRecord, json.hcursor) shouldEqual Right(
      Map(
        "name" -> "Alice",
        "age"  -> 30
      ).asJava
    )
  }

  test("decodeValue should handle nested records correctly") {
    val json = Json.obj(
      "name" -> "Alice".asJson,
      "address" -> Json.obj(
        "street"      -> "Main St".asJson,
        "houseNumber" -> "123".asJson
      )
    )

    val typedRecord = Typed.record(
      Map(
        "name" -> Typed.fromInstance("Alice"),
        "address" -> Typed.record(
          Map(
            "street"      -> Typed.fromInstance("Main St"),
            "houseNumber" -> Typed.fromInstance("123")
          )
        )
      )
    )

    ValueDecoder.decodeValue(typedRecord, json.hcursor) shouldEqual Right(
      Map(
        "name" -> "Alice",
        "address" -> Map(
          "street"      -> "Main St",
          "houseNumber" -> "123"
        ).asJava
      ).asJava
    )
  }

}
