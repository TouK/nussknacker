package pl.touk.nussknacker.engine.sql

import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

trait SqlQueryableDataBase extends AutoCloseable {
  def createTables(tables: Map[String, ColumnModel]): Unit

  def getTypingResult(query: String): TypingResult

  def insertTables(tables: Map[String, Table]): Unit

  //TODO: split to compile query and execute query
  def query(query: String): List[TypedMap]

}
