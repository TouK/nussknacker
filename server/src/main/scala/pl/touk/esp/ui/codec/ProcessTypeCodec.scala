package pl.touk.esp.ui.codec

import argonaut.Json._
import argonaut.{CodecJson, CursorHistory, DecodeResult}
import pl.touk.esp.ui.db.entity.ProcessEntity.{ProcessType, ProcessingType}
import pl.touk.esp.ui.db.entity.ProcessEntity.ProcessType.ProcessType
import pl.touk.esp.ui.db.entity.ProcessEntity.ProcessingType.ProcessingType

import scala.util.{Failure, Success, Try}

object ProcessTypeCodec {

  def codec = CodecJson[ProcessType.ProcessType](
    value => jString(value.toString),
    cursor => cursor.as[String].flatMap(tryToParse)
  )

  private def tryToParse(value: String) : DecodeResult[ProcessType]= Try(ProcessType.withName(value)) match {
    case Success(processType) => DecodeResult.ok(processType)
    case Failure(_) => DecodeResult.fail(s"$value cannot be converted to ProcessType", CursorHistory(List()))
  }

}

object ProcessingTypeCodec {
  def codec = CodecJson[ProcessingType.ProcessingType](
    value => jString(value.toString),
    cursor => cursor.as[String].flatMap(tryToParse)
  )

  private def tryToParse(value: String) : DecodeResult[ProcessingType]= Try(ProcessingType.withName(value)) match {
    case Success(processingType) => DecodeResult.ok(processingType)
    case Failure(_) => DecodeResult.fail(s"$value cannot be converted to ProcessingType", CursorHistory(List()))
  }
}