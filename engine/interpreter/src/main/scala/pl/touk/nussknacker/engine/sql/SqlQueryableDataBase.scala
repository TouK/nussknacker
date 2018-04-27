package pl.touk.nussknacker.engine.sql

import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

trait SqlQueryableDataBase extends AutoCloseable {

  def getTypingResult: TypingResult

  def query(tables: Map[String, Table]): List[TypedMap]

}
