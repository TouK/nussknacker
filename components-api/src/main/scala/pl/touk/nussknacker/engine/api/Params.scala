package pl.touk.nussknacker.engine.api

import pl.touk.nussknacker.engine.api.Params.Result

final case class Params(nameToValueMap: Map[String, Any]) {

  def extract[T](paramName: String): Result[T] =
    rawValueExtract(paramName) match {
      case Some(null)  => Result.ParamPresent(paramName, None)
      case Some(value) => Result.ParamPresent(paramName, Some(cast[T](value)))
      case None        => Result.ParamNotPresent
    }

  def extractPresentUnsafe[T](paramName: String): Result.ParamPresent[T] =
    extract[T](paramName) match {
      case present @ Result.ParamPresent(_, _) => present
      case Result.ParamNotPresent =>
        throw new IllegalArgumentException(cannotFindParamNameMessage(paramName))
    }

  def extractPresentValueUnsafe[T](paramName: String): Option[T] = extractPresentUnsafe[T](paramName).value

  def extractNonOptionalPresentValueUnsafe[T](paramName: String): T =
    extractPresentValueUnsafe[T](paramName)
      .getOrElse(throw new IllegalArgumentException(paramValueIsNullMessage(paramName)))

  def _extractOld[T](paramName: String): Option[T] =
    rawValueExtract(paramName).map(cast[T])

//  def _extractUnsafe[T](paramName: String): T =
//    _extractOld[T](paramName)
//      .getOrElse(throw new IllegalArgumentException(cannotFindParamNameMessage(paramName)))

  def _extractOrEvaluate[T](paramName: String, context: Context): Option[T] = {
    rawValueExtract(paramName)
      .map {
        case lp: LazyParameter[_] => lp.evaluate(context)
        case other                => other
      }
      .map(cast[T])
  }

  def _extractOrEvaluateUnsafe[T](paramName: String, context: Context): T = {
    _extractOrEvaluate[T](paramName, context)
      .getOrElse(throw new IllegalArgumentException(cannotFindParamNameMessage(paramName)))
  }

  private def rawValueExtract(paramName: String) = nameToValueMap.get(paramName)

  private def cannotFindParamNameMessage(paramName: String) =
    s"Cannot find param name [$paramName]. Available param names: ${nameToValueMap.keys.mkString(",")}"

  private def paramValueIsNullMessage(paramName: String) =
    s"Param [$paramName] value is null."

  private def cast[T](value: Any): T = value.asInstanceOf[T]

}

object Params {
  lazy val empty: Params = Params(Map.empty)

  sealed trait Result[+T]

  object Result {
    final case class ParamPresent[+T](name: String, value: Option[T]) extends Result[T]
    case object ParamNotPresent                                       extends Result[Nothing]
  }

}
