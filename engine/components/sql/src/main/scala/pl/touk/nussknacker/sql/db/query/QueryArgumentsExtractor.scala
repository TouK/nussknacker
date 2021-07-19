package pl.touk.nussknacker.sql.db.query

trait QueryArgumentsExtractor {

  def apply(argsCount: Int, params: Map[String, Any]): QueryArguments
}
