package pl.touk.nussknacker.sql.db.query

final case class QueryArguments(value: List[QueryArgument])

final case class QueryArgument(index: Int, value: Option[Any])
