package pl.touk.nussknacker.ui.process.test

import cats.data.NonEmptyList
import io.circe.parser
import pl.touk.nussknacker.ui.process.test.PreliminaryScenarioRecordsSerDe.{DeserializationError, SerializationError}

class PreliminaryScenarioRecordsSerDe(serializedContentMaxLength: Option[Int], maxRecordsCount: Option[Int]) {

  def serialize(
      scenarioRecords: PreliminaryScenarioRecords
  ): Either[SerializationError, SerializedScenarioRecordsContent] = {
    import io.circe.syntax._

    val content = scenarioRecords.records
      .map(_.asJson.noSpaces)
      .toList
      .mkString("\n")
    validateContentMaxLength(content, SerializationError.TooManyCharactersGenerated).map(
      SerializedScenarioRecordsContent
    )
  }

  def deserialize(
      serializedScenarioRecordsContent: SerializedScenarioRecordsContent
  ): Either[DeserializationError, PreliminaryScenarioRecords] = {
    import cats.implicits.catsStdInstancesForEither
    import cats.syntax.either._
    import cats.syntax.traverse._

    for {
      _ <- validateContentMaxLength(serializedScenarioRecordsContent.content, DeserializationError.TooManyCharacters)
      serializedScenarioRecords = serializedScenarioRecordsContent.content.linesIterator.toList
      _ <- validateMaxRecordsCount(serializedScenarioRecords)
      decodedRecords <- serializedScenarioRecords.mapWithIndex { (rawTestRecord, recordIndex) =>
        val parsedRecord = parser.decode[PreliminaryScenarioRecord](rawTestRecord)
        parsedRecord.leftMap(_ => DeserializationError.RecordParsingError(rawTestRecord, recordIndex))
      }.sequence
      result <- NonEmptyList
        .fromList(decodedRecords)
        .map(nel => Right(PreliminaryScenarioRecords(nel)))
        .getOrElse(Left(DeserializationError.NoRecords))
    } yield result
  }

  private def validateContentMaxLength[TooManyCharactersError](
      content: String,
      createError: (Int, Int) => TooManyCharactersError
  ) = {
    serializedContentMaxLength
      .map { definedSerializedContentMaxLength =>
        Either.cond(
          content.length <= definedSerializedContentMaxLength,
          content,
          createError(
            content.length,
            definedSerializedContentMaxLength
          )
        )
      }
      .getOrElse(Right(content))
  }

  private def validateMaxRecordsCount(rawRecords: List[String]) = {
    maxRecordsCount
      .map { definedMaxRecordsCount =>
        Either.cond(
          rawRecords.size <= definedMaxRecordsCount,
          (),
          DeserializationError.TooManyRecords(size = rawRecords.size, limit = definedMaxRecordsCount)
        )
      }
      .getOrElse(Right(()))
  }

}

object PreliminaryScenarioRecordsSerDe {
  sealed trait SerializationError

  object SerializationError {
    final case class TooManyCharactersGenerated(length: Int, limit: Int) extends SerializationError
  }

  sealed trait DeserializationError

  object DeserializationError {
    final case class TooManyCharacters(length: Int, limit: Int)                         extends DeserializationError
    final case class TooManyRecords(size: Int, limit: Int)                              extends DeserializationError
    final case class RecordParsingError(serializedTestRecord: String, recordIndex: Int) extends DeserializationError
    final case object NoRecords                                                         extends DeserializationError
  }

  val noLimit = new PreliminaryScenarioRecordsSerDe(None, None)

}
