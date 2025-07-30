package pl.touk.nussknacker.ui.process.test

import cats.data.NonEmptyList
import cats.implicits.toBifunctorOps
import pl.touk.nussknacker.ui.process.test.PreliminaryScenarioRecordsSerDe.{DeserializationError, SerializationError}
import pl.touk.nussknacker.ui.process.test.testdataformat.TestDataFormatSerDe

class PreliminaryScenarioRecordsSerDe(
    serializedContentMaxLength: Option[Int],
    maxRecordsCount: Option[Int],
    testDataFormatSerDe: TestDataFormatSerDe
) {

  def serialize(
      scenarioRecords: PreliminaryScenarioRecords
  ): Either[SerializationError, SerializedScenarioRecordsContent] = {

    val content = testDataFormatSerDe.serializeRecords(scenarioRecords)
    validateContentMaxLength(content, SerializationError.TooManyCharactersGenerated)
  }

  def deserialize(
      serializedScenarioRecordsContent: SerializedScenarioRecordsContent
  ): Either[DeserializationError, PreliminaryScenarioRecords] = {
    for {
      _ <- validateContentMaxLength(serializedScenarioRecordsContent, DeserializationError.TooManyCharacters)
      decodedRecords <- decodeRecords(serializedScenarioRecordsContent)
      result <- NonEmptyList
        .fromList(decodedRecords)
        .map(nel => Right(PreliminaryScenarioRecords(nel)))
        .getOrElse(Left(DeserializationError.NoRecords))
    } yield result
  }

  private def decodeRecords(
      content: SerializedScenarioRecordsContent
  ): Either[DeserializationError, List[PreliminaryScenarioRecord]] = {
    for {
      decoded <- testDataFormatSerDe
        .deserializeRecords(content)
        .leftMap {
          case TestDataFormatSerDe.DeserializationError.RecordParsingError(serializedTestRecord, recordIndex) =>
            DeserializationError.RecordParsingError(serializedTestRecord, recordIndex)
          case TestDataFormatSerDe.DeserializationError.RecordsParsingError(message) =>
            DeserializationError.RecordsParsingError(message)
        }
      _ <- validateMaxRecordsCount(decoded)
    } yield decoded
  }

  private def validateContentMaxLength[TooManyCharactersError](
      content: SerializedScenarioRecordsContent,
      createError: (Int, Int) => TooManyCharactersError
  ) = {
    serializedContentMaxLength
      .map { definedSerializedContentMaxLength =>
        Either.cond(
          content.content.length <= definedSerializedContentMaxLength,
          content,
          createError(
            content.content.length,
            definedSerializedContentMaxLength
          )
        )
      }
      .getOrElse(Right(content))
  }

  private def validateMaxRecordsCount(records: List[_]) = {
    maxRecordsCount
      .map { definedMaxRecordsCount =>
        Either.cond(
          records.size <= definedMaxRecordsCount,
          (),
          DeserializationError.TooManyRecords(size = records.size, limit = definedMaxRecordsCount)
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
    final case class RecordsParsingError(message: String)                               extends DeserializationError
    final case object NoRecords                                                         extends DeserializationError
  }

}
