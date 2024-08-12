package pl.touk.nussknacker.sql.db.schema

import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

import java.sql.ResultSetMetaData

object ColumnDefinition {

  def apply(columnNo: Int, resultMeta: ResultSetMetaData): ColumnDefinition =
    ColumnDefinition(
      name = resultMeta.getColumnName(columnNo),
      convertedToSupportTypeTypingResult = ConvertedToSupportTypeTypingResult(resultMeta.getColumnClassName(columnNo))
    )

  def apply(typing: (String, String)): ColumnDefinition =
    ColumnDefinition(
      name = typing._1,
      convertedToSupportTypeTypingResult = ConvertedToSupportTypeTypingResult(typing._2)
    )

}

final case class ColumnDefinition(
    name: String,
    convertedToSupportTypeTypingResult: ConvertedToSupportTypeTypingResult
) {
  val typing: TypingResult = convertedToSupportTypeTypingResult.typing

  def mapValue(value: Any): Any = Option(value)
    .map(convertedToSupportTypeTypingResult.typeRemappingFunction)
    .getOrElse(value)

}
