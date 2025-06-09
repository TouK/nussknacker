package pl.touk.nussknacker.ui.process.test

import cats.data.NonEmptyList
import io.circe.parser
import pl.touk.nussknacker.ui.process.test.PreliminaryScenarioTestDataSerDe.{DeserializationError, SerializationError}

class PreliminaryScenarioTestDataSerDe(testDataMaxLength: Option[Int], maxSamplesCount: Option[Int]) {

  def serialize(scenarioTestData: PreliminaryScenarioTestData): Either[SerializationError, RawScenarioTestData] = {
    import io.circe.syntax._

    val content = scenarioTestData.testRecords
      .map(_.asJson.noSpaces)
      .toList
      .mkString("\n")
    validateTestDataMaxLength(content, SerializationError.TooManyCharactersGenerated).map(RawScenarioTestData)
  }

  def deserialize(rawTestData: RawScenarioTestData): Either[DeserializationError, PreliminaryScenarioTestData] = {
    import cats.implicits.catsStdInstancesForEither
    import cats.syntax.either._
    import cats.syntax.traverse._

    for {
      _ <- validateTestDataMaxLength(rawTestData.content, DeserializationError.TooManyCharacters)
      rawRecords = rawTestData.content.linesIterator.toList
      _ <- validateMaxSamplesCount(rawRecords)
      decodedRecords <- rawRecords.mapWithIndex { (rawTestRecord, recordIndex) =>
        val parsedRecord = parser.decode[PreliminaryScenarioTestRecord](rawTestRecord)
        parsedRecord.leftMap(_ => DeserializationError.RecordParsingError(rawTestRecord, recordIndex))
      }.sequence
      result <- NonEmptyList
        .fromList(decodedRecords)
        .map(nel => Right(PreliminaryScenarioTestData(nel)))
        .getOrElse(Left(DeserializationError.NoRecords))
    } yield result
  }

  private def validateTestDataMaxLength[TooManyCharactersError](
      content: String,
      createError: (Int, Int) => TooManyCharactersError
  ) = {
    testDataMaxLength
      .map { definedTestDataMaxLength =>
        Either.cond(
          content.length <= definedTestDataMaxLength,
          content,
          createError(
            content.length,
            definedTestDataMaxLength
          )
        )
      }
      .getOrElse(Right(content))
  }

  private def validateMaxSamplesCount(rawRecords: List[String]) = {
    maxSamplesCount
      .map { definedMaxSamplesCount =>
        Either.cond(
          rawRecords.size <= definedMaxSamplesCount,
          (),
          DeserializationError.TooManySamples(size = rawRecords.size, limit = definedMaxSamplesCount)
        )
      }
      .getOrElse(Right(()))
  }

}

object PreliminaryScenarioTestDataSerDe {
  sealed trait SerializationError

  object SerializationError {
    final case class TooManyCharactersGenerated(length: Int, limit: Int) extends SerializationError
  }

  sealed trait DeserializationError

  object DeserializationError {
    final case class TooManyCharacters(length: Int, limit: Int)                  extends DeserializationError
    final case class TooManySamples(size: Int, limit: Int)                       extends DeserializationError
    final case class RecordParsingError(rawTestRecord: String, recordIndex: Int) extends DeserializationError
    final case object NoRecords                                                  extends DeserializationError
  }

  val noLimit = new PreliminaryScenarioTestDataSerDe(None, None)

}
