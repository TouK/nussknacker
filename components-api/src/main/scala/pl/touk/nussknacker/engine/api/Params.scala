package pl.touk.nussknacker.engine.api

import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

final class Params private (val nameToRawValueMap: Map[ParameterName, Any]) {

  def isPresent(name: ParameterName): Boolean = nameToRawValueMap.contains(name)

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
    nameToRawValueMap.get(paramName) match {
      case None        => throw new IllegalStateException(cannotFindParamNameMessage(paramName))
      case Some(null)  => None
      case Some(value) => Some(value)
    }
  }

  private def cannotFindParamNameMessage(paramName: ParameterName) =
    s"Cannot find param name [${paramName.value}]. Available param names: ${nameToRawValueMap.keys.map(_.value).mkString(",")}"

  private def paramValueIsNoneMessage(paramName: ParameterName) =
    s"Parameter [${paramName.value}] doesn't expect to be null!"

  private def cast[T](value: Any): T = value.asInstanceOf[T]

}

object Params {

  // TODO: We should use fromParameterEvaluationResultMap instead of current variant. Thanks to that,
  //       in types, it will be visible what kind of "value" we have
  def fromRawValuesMap(nameToRawValueMap: Map[ParameterName, Any]) = new Params(nameToRawValueMap)

  def fromParameterEvaluationResultMap(
      nameToEvaluationResult: Map[ParameterName, ParameterEvaluationResult]
  ): Params = {
    val nameToValueMap = nameToEvaluationResult.mapValuesNow {
      case SingleEagerParameterEvaluationResult(value, _)           => value
      case SingleLazyParameterEvaluationResult(lazyParameter)       => lazyParameter
      case BranchEagerParameterEvaluationResult(valueByBranchId, _) => valueByBranchId
      case BranchLazyParameterEvaluationResult(lazyParamByBranchId) => lazyParamByBranchId
    }
    new Params(nameToValueMap)
  }

  lazy val empty: Params = new Params(Map.empty)

}
