package pl.touk.nussknacker.engine.api

final case class Params(nameToValueMap: Map[String, Any]) {

  def extract[T](paramName: String): Option[T] =
    rawValueExtract(paramName).map(cast[T])

  def extractUnsafe[T](paramName: String): T =
    extract[T](paramName)
      .getOrElse(throw new IllegalArgumentException(cannotFindParamNameMessage(paramName)))

  def extractOrEvaluate[T](paramName: String, context: Context): Option[T] = {
    rawValueExtract(paramName)
      .map {
        case lp: LazyParameter[_] => lp.evaluate(context)
        case other                => other
      }
      .map(cast[T])
  }

  def extractOrEvaluateUnsafe[T](paramName: String, context: Context): T = {
    extractOrEvaluate[T](paramName, context)
      .getOrElse(throw new IllegalArgumentException(cannotFindParamNameMessage(paramName)))
  }

  private def rawValueExtract(paramName: String) = nameToValueMap.get(paramName).flatMap(Option.apply)

  private def cannotFindParamNameMessage(paramName: String) =
    s"Cannot find param name [$paramName]. Available param names: ${nameToValueMap.keys.mkString(",")}"

  private def cast[T](value: Any): T = value.asInstanceOf[T]

}

object Params {
  lazy val empty: Params = Params(Map.empty)
}
