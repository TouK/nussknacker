package pl.touk.nussknacker.sql.db.query

import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.sql.db.schema.TableDefinition

object QueryResultStrategy {
  def apply(name: String): Option[QueryResultStrategy] = name match {
    case ResultSetStrategy.`name` => Some(ResultSetStrategy)
    case SingleResultStrategy.`name` => Some(SingleResultStrategy)
    case _ => None
  }
}

sealed trait QueryResultStrategy {
  def name: String

  def resultType(tableDef: TableDefinition): TypingResult =
    this match {
      case ResultSetStrategy => tableDef.resultSetType
      case SingleResultStrategy => tableDef.rowType
    }
}

case object ResultSetStrategy extends QueryResultStrategy {
  val name: String = "Result set"
}

case object SingleResultStrategy extends QueryResultStrategy {
  val name: String = "Single result"
}

