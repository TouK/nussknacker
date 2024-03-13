package pl.touk.nussknacker.engine.api

import pl.touk.nussknacker.engine.api.parameter.ParameterName

final case class Params(nameToValueMap: Map[ParameterName, Any]) {

  def isPresent(name: ParameterName): Boolean = nameToValueMap.get(name).isDefined

  def extract[T](name: ParameterName): Option[T] = {
    extractValue(name).map(cast[T])
  }

  def extractUnsafe[T](name: ParameterName): T =
    extract[T](name)
      .getOrElse(throw new IllegalArgumentException(paramValueIsNoneMessage(name)))

  def extractOrEvaluateLazyParam[T](name: ParameterName, context: Context): Option[T] = {
    extractValue(name)
      .map {
        case lazyParameter: LazyParameter[_] => lazyParameter.evaluate(context)
        case other                           => other
      }
      .map(cast[T])
  }

  def extractOrEvaluateLazyParamUnsafe[T](name: ParameterName, context: Context): T = {
    extractOrEvaluateLazyParam(name, context)
      .getOrElse(throw new IllegalArgumentException(paramValueIsNoneMessage(name)))
  }

  private def extractValue(paramName: ParameterName) = {
    nameToValueMap.get(paramName) match {
      case None        => throw new IllegalStateException(cannotFindParamNameMessage(paramName))
      case Some(null)  => None
      case Some(value) => Some(value)
    }
  }

  private def cannotFindParamNameMessage(paramName: ParameterName) =
    s"Cannot find param name [${paramName.value}]. Available param names: ${nameToValueMap.keys.map(_.value).mkString(",")}"

  private def paramValueIsNoneMessage(paramName: ParameterName) =
    s"Parameter [${paramName.value}] doesn't expect to be null!"

  private def cast[T](value: Any): T = value.asInstanceOf[T]

}

object Params {
  lazy val empty: Params = Params(Map.empty)
}
