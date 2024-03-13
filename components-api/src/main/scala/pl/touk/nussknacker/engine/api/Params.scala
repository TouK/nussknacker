package pl.touk.nussknacker.engine.api

import pl.touk.nussknacker.engine.api.parameter.ParameterName

final case class Params(nameToValueMap: Map[ParameterName, Any]) {

  def extract[T](paramName: ParameterName): Option[T] =
    rawValueExtract(paramName).map(cast[T])

  def extractUnsafe[T](paramName: ParameterName): T =
    extract[T](paramName)
      .getOrElse(throw new IllegalArgumentException(cannotFindParamNameMessage(paramName)))

  def extractOrEvaluate[T](paramName: ParameterName, context: Context): Option[T] = {
    rawValueExtract(paramName)
      .map {
        case lp: LazyParameter[_] => lp.evaluate(context)
        case other                => other
      }
      .map(cast[T])
  }

  def extractOrEvaluateUnsafe[T](paramName: ParameterName, context: Context): T = {
    extractOrEvaluate[T](paramName, context)
      .getOrElse(throw new IllegalArgumentException(cannotFindParamNameMessage(paramName)))
  }

  private def rawValueExtract(paramName: ParameterName) = nameToValueMap.get(paramName).flatMap(Option.apply)

  private def cannotFindParamNameMessage(paramName: ParameterName) =
    s"Cannot find param name [${paramName.value}]. Available param names: ${nameToValueMap.keys.map(_.value).mkString(",")}"

  private def cast[T](value: Any): T = value.asInstanceOf[T]

}

object Params {
  lazy val empty: Params = Params(Map.empty)
}
