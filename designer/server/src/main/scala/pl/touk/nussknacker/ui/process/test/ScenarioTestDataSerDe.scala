package pl.touk.nussknacker.ui.process.test

import io.circe.syntax.EncoderOps
import io.circe.{Decoder, Encoder, Json, parser}
import pl.touk.nussknacker.engine.api.test.{ScenarioTestData, ScenarioTestRecord}
import pl.touk.nussknacker.ui.api.TestDataSettings

import java.nio.charset.StandardCharsets
import scala.io.Source
import scala.util.Using

object ScenarioTestDataSerDe {

  // @JsonCodec is not used to keep the file format simple and avoid unnecessary object nesting.
  private implicit val scenarioTestRecordEncoder: Encoder[ScenarioTestRecord] = Encoder.instance(scenarioTestRecord =>
    Json.obj(
      "sourceId" -> Json.fromString(scenarioTestRecord.sourceId.id),
      "record" -> scenarioTestRecord.record.json,
      "timestamp" -> scenarioTestRecord.record.timestamp.asJson
    ).dropNullValues
  )

  private implicit val scenarioTestRecordDecoder: Decoder[ScenarioTestRecord] = Decoder.instance(hcursor =>
    for {
      sourceId <- hcursor.downField("sourceId").as[String]
      record <- hcursor.downField("record").as[Json]
      timestamp <- hcursor.downField("timestamp").as[Option[Long]]
    } yield ScenarioTestRecord(sourceId, record, timestamp)
  )

}

class ScenarioTestDataSerDe(testDataSettings: TestDataSettings) {

  import ScenarioTestDataSerDe._

  def serializeTestData(scenarioTestData: ScenarioTestData): Either[String, RawScenarioTestData] = {
    import io.circe.syntax._

    val content = scenarioTestData.testRecords
      .map(_.asJson.noSpaces)
      .mkString("\n")
    Either.cond(content.length <= testDataSettings.testDataMaxLength,
      RawScenarioTestData(content),
      s"Too much data generated, limit is: ${testDataSettings.testDataMaxLength}")
  }

  def prepareTestData(rawTestData: RawScenarioTestData): Either[String, ScenarioTestData] = {
    import cats.implicits.catsStdInstancesForEither
    import cats.syntax.either._
    import cats.syntax.traverse._

    val rawRecords = rawTestData.content.linesIterator.toList
    val limitedRawRecords = Either.cond(rawRecords.size <= testDataSettings.maxSamplesCount,
      rawRecords,
      s"Too many samples: ${rawRecords.size}, limit is: ${testDataSettings.maxSamplesCount}")
    val records: Either[String, List[ScenarioTestRecord]] = limitedRawRecords.flatMap { rawRecord =>
      rawRecord.map { rawTestRecord =>
        val record = parser.decode[ScenarioTestRecord](rawTestRecord)
        record.leftMap(_ => Vector(s"Could not parse record: '$rawTestRecord'"))
      }.sequence.leftMap(_.head)
    }
    records.map(ScenarioTestData)
  }

}
