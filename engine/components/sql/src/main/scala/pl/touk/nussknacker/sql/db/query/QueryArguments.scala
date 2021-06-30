package pl.touk.nussknacker.sql.db.query

case class QueryArguments(value: List[QueryArgument])

case class QueryArgument(index: Int, value: Any)
