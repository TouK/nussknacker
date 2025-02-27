package pl.touk.nussknacker.engine.kafka.source

import io.circe.Json
import io.circe.Json._
import io.circe.generic.JsonCodec
import org.apache.kafka.common.record.TimestampType
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.api.DisplayJsonWithEncoder
import pl.touk.nussknacker.engine.util.json.ToJsonEncoder

import scala.jdk.CollectionConverters._

class InputMetaToJsonSpec extends AnyFunSuite with Matchers with TableDrivenPropertyChecks {

  private val encoder = ToJsonEncoder.defaultForTests

  test("should encode for various keys") {

    forEvery(
      Table[Any, Json](
        ("key", "serialized"),
        ("keyString", fromString("keyString")),
        (100, fromInt(100)),
        (CustomKey("abc"), obj("customKey" -> fromString("abc")))
      )
    ) { (key, serialized) =>
      val inputMeta = InputMeta(key, "topic1", 1, 10, 1000, TimestampType.CREATE_TIME, Map("A" -> "B").asJava, 10)
      encoder.encode(inputMeta) shouldBe obj(
        "key"           -> serialized,
        "topic"         -> fromString("topic1"),
        "partition"     -> fromInt(1),
        "offset"        -> fromLong(10),
        "timestamp"     -> fromLong(1000),
        "timestampType" -> fromString("CreateTime"),
        "headers"       -> obj("A" -> fromString("B")),
        "leaderEpoch"   -> fromInt(10)
      )
    }

  }

  @JsonCodec case class CustomKey(customKey: String) extends DisplayJsonWithEncoder[CustomKey]

}
