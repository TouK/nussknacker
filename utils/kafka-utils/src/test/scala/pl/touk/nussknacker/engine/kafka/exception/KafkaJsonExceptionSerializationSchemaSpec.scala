package pl.touk.nussknacker.engine.kafka.exception

import io.circe.{Decoder, JsonObject, KeyDecoder}
import io.circe.parser.decode
import io.circe.syntax.EncoderOps
import org.scalatest.EitherValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.{Context, MetaData, StreamMetaData}
import pl.touk.nussknacker.engine.api.exception.{NonTransientException, NuExceptionInfo}

import java.nio.charset.StandardCharsets
import java.time.Instant

class KafkaJsonExceptionSerializationSchemaSpec extends AnyFunSuite with Matchers with EitherValues {
  private val timestamp = Instant.now()
  private val exception = NonTransientException("input", "message", timestamp)
  private implicit val mapDecoder: Decoder[Map[String, String]] =
    Decoder.decodeMap(KeyDecoder.decodeKeyString, Decoder.decodeString)

  test("variable sizes are calculated correctly") {
    def jsonBytes(variables: Map[String, Any]): Int = {
      createKafkaExceptionInfo(variables).asJson.spaces2.length
    }

    def serializedSize(variables: Map[String, Any], variableName: String): Int = {
      jsonBytes(variables) - jsonBytes(variables - variableName)
    }

    val variables = Map(
      "str"           -> "12345",
      "int"           -> 12345,
      "number"        -> 123.45,
      "array"         -> Seq(1, 2),
      "object"        -> Map("a" -> 1, "b" -> 2),
      "complexObject" -> Map("a" -> 1, "b" -> Seq(1, 2, "c"), "c" -> Map.empty, "d" -> Map("e" -> 1))
    )
    val expectedSizes = variables.map { case (k, _) =>
      // remove bytes taken up by serialized key name
      k -> (serializedSize(variables, k) - 2 - k.length)
    }

    val inputEvent = createKafkaExceptionInfo(variables).asJson.hcursor.downField("inputEvent").as[JsonObject].value
    KafkaJsonExceptionSerializationSchema
      .countVariableLengths(inputEvent, indentBytes = 4)
      .toMap shouldBe expectedSizes
  }

  test("strips longest inputEvent entries") {
    val baseContext = Map(
      "longVariable"   -> "",
      "shortVariable1" -> "111",
      "shortVariable2" -> "222222",
      "shortVariable3" -> "shortVariableValue"
    )
    val largeContext = baseContext ++ Map("longVariable" -> "1".repeat(400))

    val (baseRecord, key) = getSerializedJson(baseContext)
    decode[KafkaExceptionInfo](baseRecord).value.inputEvent.get shouldBe baseContext.asJson

    // limit that will remove longVariable, we need to account for the added !warning key
    val maxRecord1Length = baseRecord.length + 350
    val (record1, _)     = getSerializedJson(largeContext, maxMessageBytes = maxRecord1Length)
    val inputEvent1      = decode[KafkaExceptionInfo](record1).value.inputEvent.get.as[Map[String, String]].value

    (inputEvent1.keySet - "!warning") shouldBe (largeContext.keySet - "longVariable")
    inputEvent1("!warning") should startWith("variables truncated")
    inputEvent1("!warning") should endWith(": longVariable")
    record1.length should be <= maxRecord1Length

    // add padding to size limit, variable removal rounds some calculations
    val maxRecord2Length = record1.length + key.length + KafkaJsonExceptionSerializationSchema.recordOverhead + 1
    val (record2, _)     = getSerializedJson(largeContext, maxMessageBytes = maxRecord2Length)
    val inputEvent2      = decode[KafkaExceptionInfo](record2).value.inputEvent.get.as[Map[String, String]].value

    inputEvent2.keySet shouldBe inputEvent1.keySet
    record2.length should equal(record1.length)
  }

  private def getSerializedJson(
      contextVariables: Map[String, Any],
      maxMessageBytes: Int = 1048576,
  ): (String, String) = {
    val serializer = serializerWithMaxMessageLength(maxMessageBytes)
    val record     = serializer.serialize(createData(contextVariables), timestamp.toEpochMilli)

    (new String(record.value(), StandardCharsets.UTF_8), new String(record.key(), StandardCharsets.UTF_8))
  }

  private def serializerWithMaxMessageLength(maxMessageLength: Int): KafkaJsonExceptionSerializationSchema = {
    val config = KafkaExceptionConsumerConfig(
      topic = "topic",
      maxMessageBytes = maxMessageLength,
      includeInputEvent = true
    )
    new KafkaJsonExceptionSerializationSchema(MetaData("test", StreamMetaData()), config)
  }

  private def createData(contextVariables: Map[String, Any]): NuExceptionInfo[NonTransientException] = {
    NuExceptionInfo(None, exception, Context("contextId", contextVariables))
  }

  private def createKafkaExceptionInfo(variables: Map[String, Any]) = KafkaExceptionInfo(
    MetaData("test", StreamMetaData()),
    createData(variables),
    KafkaExceptionConsumerConfig(topic = "topic", includeInputEvent = true)
  )

}
