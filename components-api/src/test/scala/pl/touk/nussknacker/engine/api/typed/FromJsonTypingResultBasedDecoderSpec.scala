package pl.touk.nussknacker.engine.api.typed

import com.typesafe.scalalogging.LazyLogging
import io.circe.Json
import io.circe.syntax.EncoderOps
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.json.decoders.FromJsonTypingResultBasedDecoder
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.test.EitherValuesDetailedMessage

import scala.jdk.CollectionConverters._

class FromJsonTypingResultBasedDecoderSpec
    extends AnyFunSuite
    with EitherValuesDetailedMessage
    with Matchers
    with LazyLogging {

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

    FromJsonTypingResultBasedDecoder.decodeValue(typedRecord, json.hcursor) shouldEqual Right(
      Map(
        "name" -> "Alice",
        "age"  -> 30
      ).asJava
    )
  }

  test("decodeValue should ignore missing Record field") {
    val typedRecord = Typed.record(
      Map(
        "name" -> Typed.fromInstance("Alice"),
        "age"  -> Typed.fromInstance(30)
      )
    )

    val json = Json.obj(
      "name" -> "Alice".asJson
    )

    FromJsonTypingResultBasedDecoder.decodeValue(typedRecord, json.hcursor).rightValue shouldBe Map(
      "name" -> "Alice"
    ).asJava
  }

  test("decodeValue should decode extra fields using generic json decoding strategy") {
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

    FromJsonTypingResultBasedDecoder.decodeValue(typedRecord, json.hcursor) shouldEqual Right(
      Map(
        "name"       -> "Alice",
        "age"        -> 30,
        "occupation" -> "nurse"
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

    FromJsonTypingResultBasedDecoder.decodeValue(typedRecord, json.hcursor) shouldEqual Right(
      Map(
        "name" -> "Alice",
        "address" -> Map(
          "street"      -> "Main St",
          "houseNumber" -> "123"
        ).asJava
      ).asJava
    )
  }

  test("decodeValue should handle null json despite of typing") {
    val json = Json.Null

    val typedRecord = Typed.typedClass(classOf[String])

    FromJsonTypingResultBasedDecoder.decodeValue(typedRecord, json.hcursor) shouldEqual Right(null)
  }

}
