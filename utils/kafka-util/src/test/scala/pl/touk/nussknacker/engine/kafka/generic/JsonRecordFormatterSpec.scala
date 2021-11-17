package pl.touk.nussknacker.engine.kafka.generic

import io.circe.Json
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.CirceUtil.decodeJsonUnsafe

import java.nio.charset.StandardCharsets

class JsonRecordFormatterSpec extends FunSuite with Matchers {

  private val topic = "testTopic"

  test("handles generate -> parse for any json formatting") {

    testGenerateThenParse("{}")
    testGenerateThenParse("""{"st": {"a": "b"}}""")
    testGenerateThenParse(
      """{
        |"st": {
        |"a":
        |"bb"} }
        |""".stripMargin)
    testGenerateThenParse(
      """{
        |"st": 
        |{ "a": "bb\n\n", "list": [
        |]}
        |
        |}""".stripMargin)
  }

  private def testGenerateThenParse(json: String): Unit = {
    val recordBytes = json.getBytes(StandardCharsets.UTF_8)
    val size = 3

    val record = new ConsumerRecord[Array[Byte], Array[Byte]](topic, 0, 0, null, recordBytes)
    val formatted = JsonRecordFormatter.prepareGeneratedTestData((1 to size).map(_ => record).toList)
    val parsed = JsonRecordFormatter.parseDataForTest(topic, formatted)
    parsed should have length size
    parsed.foreach { producer =>
      decodeJsonUnsafe[Json](producer.value()) shouldBe decodeJsonUnsafe[Json](recordBytes)
    }
  }


}
