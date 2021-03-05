package pl.touk.nussknacker.engine.flink.util.db

import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}

import java.sql.ResultSetMetaData

object ColumnDef {
  def apply(columnNo: Int, resultMeta: ResultSetMetaData): ColumnDef =
    ColumnDef(
      no = columnNo,
      name = resultMeta.getColumnName(columnNo),
      typ = Typed(Class.forName(resultMeta.getColumnClassName(columnNo))))
}

case class ColumnDef(no: Int, name: String, typ: TypingResult)