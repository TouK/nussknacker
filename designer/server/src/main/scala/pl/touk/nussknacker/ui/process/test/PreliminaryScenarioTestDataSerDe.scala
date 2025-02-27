package pl.touk.nussknacker.ui.process.test

import cats.data.NonEmptyList
import io.circe.parser
import pl.touk.nussknacker.engine.definition.test.{PreliminaryScenarioTestData, PreliminaryScenarioTestRecord}
import pl.touk.nussknacker.ui.api.TestDataSettings
import pl.touk.nussknacker.ui.process.test.PreliminaryScenarioTestDataSerDe.{DeserializationError, SerializationError}

class PreliminaryScenarioTestDataSerDe(testDataSettings: TestDataSettings) {

  def serialize(scenarioTestData: PreliminaryScenarioTestData): Either[SerializationError, RawScenarioTestData] = {
    import io.circe.syntax._

    val content = scenarioTestData.testRecords
      .map(_.asJson.noSpaces)
      .toList
      .mkString("\n")
    Either.cond(
      content.length <= testDataSettings.testDataMaxLength,
      RawScenarioTestData(content),
      SerializationError.TooManyCharactersGenerated(length = content.length, limit = testDataSettings.testDataMaxLength)
    )
  }

  def deserialize(rawTestData: RawScenarioTestData): Either[DeserializationError, PreliminaryScenarioTestData] = {
    import cats.implicits.catsStdInstancesForEither
    import cats.syntax.either._
    import cats.syntax.traverse._

    for {
      _ <- Either.cond(
        rawTestData.content.length <= testDataSettings.testDataMaxLength,
        (),
        DeserializationError.TooManyCharacters(
          length = rawTestData.content.length,
          limit = testDataSettings.testDataMaxLength
        )
      )
      rawRecords = rawTestData.content.linesIterator.toList
      _ <- Either.cond(
        rawRecords.size <= testDataSettings.maxSamplesCount,
        (),
        DeserializationError.TooManySamples(size = rawRecords.size, limit = testDataSettings.maxSamplesCount)
      )
      decodedRecords <- rawRecords.map { rawTestRecord =>
        val parsedRecord = parser.decode[PreliminaryScenarioTestRecord](rawTestRecord)
        parsedRecord.leftMap(_ => DeserializationError.RecordParsingError(rawTestRecord))
      }.sequence
      result <- NonEmptyList
        .fromList(decodedRecords)
        .map(nel => Right(PreliminaryScenarioTestData(nel)))
        .getOrElse(Left(DeserializationError.NoRecords))
    } yield result
  }

}

object PreliminaryScenarioTestDataSerDe {
  sealed trait SerializationError

  object SerializationError {
    final case class TooManyCharactersGenerated(length: Int, limit: Int) extends SerializationError
  }

  sealed trait DeserializationError

  object DeserializationError {
    final case class TooManyCharacters(length: Int, limit: Int) extends DeserializationError
    final case class TooManySamples(size: Int, limit: Int)      extends DeserializationError
    final case class RecordParsingError(rawTestRecord: String)  extends DeserializationError
    final case object NoRecords                                 extends DeserializationError
  }

}
