package pl.touk.nussknacker.ui.process.test

import cats.data.NonEmptyList
import io.circe.Json
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks.forAll
import org.scalatest.prop.Tables.Table
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.test.EitherValuesDetailedMessage
import pl.touk.nussknacker.ui.api.TestDataFormat
import pl.touk.nussknacker.ui.process.test.PreliminaryScenarioRecordsSerDe.{DeserializationError, SerializationError}
import pl.touk.nussknacker.ui.process.test.testdataformat.TestDataFormatSerDe

class PreliminaryScenarioRecordsSerDeTest extends AnyFunSuite with Matchers with EitherValuesDetailedMessage {

  private val testDataMaxLength = 1000
  private val maxSamplesCount   = 5

  private def createSerDe(testDataFormat: TestDataFormat.Value) = new PreliminaryScenarioRecordsSerDe(
    serializedContentMaxLength = Some(testDataMaxLength),
    maxRecordsCount = Some(maxSamplesCount),
    testDataFormatSerDe = TestTestDataFormatSerDeFactory.create(testDataFormat)
  )

  private val correctSourceSpecificFormatRecord = SourceSpecificFormatPreliminaryScenarioRecord(
    sourceId = NodeId("source1"),
    record = Json.obj("f1" -> Json.fromString("field value"), "f2" -> Json.fromLong(42L)),
    timestamp = Some(24L)
  )

  private val serializedSourceSpecificFormatRecord =
    """{"sourceId":"source1","record":{"f1":"field value","f2":42},"timestamp":24}""".stripMargin

  private val correctCommonFormatRecord = CommonFormatPreliminaryScenarioRecord(
    sourceId = NodeId("source1"),
    variables = Map(
      "input" -> Json.obj(
        "f1" -> Json.fromString("field value"),
        "f2" -> Json.fromLong(42L)
      )
    ),
    timestamp = Some(24L)
  )

  private val serializedCommonFormatRecord =
    """[{"sourceId":"source1","variables":{"input":{"f1":"field value","f2":42}},"timestamp":24}]""".stripMargin

  test("should serialize scenario test data") {
    forAll(
      Table(
        ("testDataFormat", "inputRecord", "expectedSerializedRecords"),
        (TestDataFormat.SourceSpecific, correctSourceSpecificFormatRecord, serializedSourceSpecificFormatRecord),
        (TestDataFormat.CommonFormat, correctCommonFormatRecord, serializedCommonFormatRecord),
      )
    ) { (testDataFormat, inputRecord, expectedSerializedRecords) =>
      val serializedTestData = createSerDe(testDataFormat)
        .serialize(
          PreliminaryScenarioRecords(
            NonEmptyList.one(inputRecord)
          )
        )
        .rightValue

      serializedTestData.content shouldBe expectedSerializedRecords
    }
  }

  test("should fail trying to serialize too much bytes") {
    forAll(
      Table(
        ("testDataFormat", "inputRecord"),
        (TestDataFormat.SourceSpecific, correctSourceSpecificFormatRecord),
        (TestDataFormat.CommonFormat, correctCommonFormatRecord),
      )
    ) { (testDataFormat, inputRecord) =>
      val testData = PreliminaryScenarioRecords(
        NonEmptyList.fromListUnsafe(
          List.fill(30)(inputRecord)
        )
      )

      createSerDe(testDataFormat).serialize(testData) should matchPattern {
        case Left(SerializationError.TooManyCharactersGenerated(length, `testDataMaxLength`))
            if length > testDataMaxLength =>
      }
    }
  }

  test("should deserialize scenario test data") {
    forAll(
      Table(
        ("testDataFormat", "inputContent", "expectedDeserializedRecord"),
        (TestDataFormat.SourceSpecific, serializedSourceSpecificFormatRecord, correctSourceSpecificFormatRecord),
        (TestDataFormat.CommonFormat, serializedCommonFormatRecord, correctCommonFormatRecord),
      )
    ) { (testDataFormat, inputContent, expectedDeserializedRecord) =>
      val result = createSerDe(testDataFormat)
        .deserialize(SerializedScenarioRecordsContent(inputContent))
        .rightValue
        .records
        .toList

      result shouldBe List(expectedDeserializedRecord)
    }
  }

  test("should fail trying to parse too many characters") {
    val longString = "a long JSON string...".repeat(10)

    val tooBigSourceSpecificFormatContent =
      SerializedScenarioRecordsContent(
        List.fill(10)(s"""{"sourceId":"source1","record":"$longString"}""").mkString("\n")
      )

    val tooBigCommonFormatContent =
      SerializedScenarioRecordsContent(
        List.fill(10)(s"""[{"sourceId":"source1","variables":{"input":"$longString"}}]""").mkString("\n")
      )

    forAll(
      Table(
        ("testDataFormat", "inputContent"),
        (TestDataFormat.SourceSpecific, tooBigSourceSpecificFormatContent),
        (TestDataFormat.CommonFormat, tooBigCommonFormatContent),
      )
    ) { (testDataFormat, inputContent) =>
      createSerDe(testDataFormat).deserialize(inputContent) should matchPattern {
        case Left(DeserializationError.TooManyCharacters(length, `testDataMaxLength`)) if length > testDataMaxLength =>
      }
    }
  }

  test("should fail trying to parse too many records") {
    val tooBigSourceSpecificFormatContent =
      SerializedScenarioRecordsContent(
        List.fill(10)("""{"sourceId":"source1","record":"a JSON string"}""").mkString("\n")
      )

    val tooBigCommonFormatContent =
      SerializedScenarioRecordsContent(
        List.fill(10)("""{"sourceId":"source1","variables":{"input": "a JSON string"}}""").mkString("[", ",", "]")
      )

    forAll(
      Table(
        ("testDataFormat", "inputContent"),
        (TestDataFormat.SourceSpecific, tooBigSourceSpecificFormatContent),
        (TestDataFormat.CommonFormat, tooBigCommonFormatContent),
      )
    ) { (testDataFormat, inputContent) =>
      createSerDe(testDataFormat).deserialize(inputContent) should matchPattern {
        case Left(DeserializationError.TooManyRecords(size, `maxSamplesCount`)) if size > maxSamplesCount =>
      }
    }
  }

  test("should fail trying to parse invalid record") {
    val invalidRecord = "not a test record"
    val sourceSpecificFormatContentWithInvalidRecord = SerializedScenarioRecordsContent(
      s"""$serializedSourceSpecificFormatRecord
        |$invalidRecord""".stripMargin
    )

    val sourceSpecificDeserializationError = DeserializationError.RecordParsingError(invalidRecord, recordIndex = 1)

    val commonFormatInvalidContent = SerializedScenarioRecordsContent("invalid")

    val commonFormatDeserializationError =
      DeserializationError.RecordsParsingError("expected json value got 'invali...' (line 1, column 1)")

    forAll(
      Table(
        ("testDataFormat", "inputContent", "expectedError"),
        (
          TestDataFormat.SourceSpecific,
          sourceSpecificFormatContentWithInvalidRecord,
          sourceSpecificDeserializationError
        ),
        (TestDataFormat.CommonFormat, commonFormatInvalidContent, commonFormatDeserializationError),
      )
    ) { (testDataFormat, inputContent, expectedError) =>
      val error = createSerDe(testDataFormat).deserialize(inputContent).leftValue
      error shouldBe expectedError
    }
  }

}
