package pl.touk.esp.ui.process.marshall

import argonaut.Json._
import argonaut.{CodecJson, CursorHistory, DecodeResult}
import pl.touk.esp.ui.db.migration.CreateProcessesMigration.ProcessType
import pl.touk.esp.ui.db.migration.CreateProcessesMigration.ProcessType.ProcessType

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
