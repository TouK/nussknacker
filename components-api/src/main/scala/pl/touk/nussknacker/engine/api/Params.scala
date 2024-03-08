package pl.touk.nussknacker.engine.api

import pl.touk.nussknacker.engine.api.parameter.ParameterName

final case class Params(nameToValueMap: Map[ParameterName, Any]) {

  def extract[T](name: ParameterName): Option[T] =
    rawValueExtract(name) match {
      case Some(null) | None => None
      case Some(value)       => Some(cast(value))
    }

  def extractMandatory[T](name: ParameterName): T =
    extract[T](name)
      .getOrElse(throw new IllegalArgumentException(cannotFindParamNameMessage(name)))

  def extractOrEvaluateLazyParam[T](name: ParameterName, context: Context): Option[T] = {
    rawValueExtract(name)
      .map {
        case lp: LazyParameter[_] => lp.evaluate(context)
        case other                => other
      }
      .map(cast[T])
  }

  def extractMandatoryOrEvaluateLazyParamUnsafe[T](name: ParameterName, context: Context): T = {
    extractOrEvaluateLazyParam[T](name, context)
      .getOrElse(throw new IllegalArgumentException(cannotFindParamNameMessage(name)))
  }

  private def rawValueExtract(paramName: ParameterName) = nameToValueMap.get(paramName)

  private def cannotFindParamNameMessage(paramName: ParameterName) =
    s"Cannot find param name [${paramName.value}]. Available param names: ${nameToValueMap.keys.mkString(",")}"

  private def cast[T](value: Any): T = value.asInstanceOf[T]

}

object Params {
  lazy val empty: Params = Params(Map.empty)
}
