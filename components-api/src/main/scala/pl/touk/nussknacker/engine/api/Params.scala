package pl.touk.nussknacker.engine.api

import cats.Functor
import cats.implicits.toFunctorOps
import pl.touk.nussknacker.engine.api.Params.ParamExtractionResult
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

final class Params private (val nameToRawValueMap: Map[ParameterName, Any]) extends Serializable {

  def extractParam[T](name: ParameterName): ParamExtractionResult[T] = {
    extractValue(name).map(cast[T])
  }

  def extractDeclaredParam[T](name: ParameterName): Option[T] = {
    extractParam[T](name) match {
      case ParamExtractionResult.MissingParam =>
        throw new IllegalArgumentException(cannotFindParamNameMessage(name))
      case ParamExtractionResult.ParamValueIsNone => None
      case ParamExtractionResult.Value(value)     => Some(value)
    }
  }

  def extractDeclaredParamUnsafe[T](name: ParameterName): T =
    extractParam[T](name) match {
      case ParamExtractionResult.MissingParam =>
        throw new IllegalArgumentException(cannotFindParamNameMessage(name))
      case ParamExtractionResult.ParamValueIsNone => throw new IllegalArgumentException(paramValueIsNoneMessage(name))
      case ParamExtractionResult.Value(value)     => value
    }

  def extractOrEvaluateDeclaredLazyParam[T](name: ParameterName, context: Context): Option[T] = {
    extractDeclaredParam[T](name)
      .map {
        case lazyParameter: LazyParameter[_] => lazyParameter.evaluate(context)
        case other                           => other
      }
      .map(cast[T])
  }

  def extractOrEvaluateDeclaredLazyParamUnsafe[T](name: ParameterName, context: Context): T = {
    extractOrEvaluateDeclaredLazyParam(name, context)
      .getOrElse(throw new IllegalArgumentException(paramValueIsNoneMessage(name)))
  }

  private def extractValue(paramName: ParameterName): ParamExtractionResult[Any] = {
    nameToRawValueMap.get(paramName) match {
      case None        => ParamExtractionResult.MissingParam
      case Some(null)  => ParamExtractionResult.ParamValueIsNone
      case Some(value) => ParamExtractionResult.Value(value)
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

  sealed trait ParamExtractionResult[+T]

  object ParamExtractionResult {
    final case class Value[+T](value: T) extends ParamExtractionResult[T]
    case object ParamValueIsNone         extends ParamExtractionResult[Nothing]
    case object MissingParam             extends ParamExtractionResult[Nothing]

    implicit val resultFunctor: Functor[ParamExtractionResult] = new Functor[ParamExtractionResult] {
      override def map[A, B](fa: ParamExtractionResult[A])(f: A => B): ParamExtractionResult[B] = fa match {
        case ParamExtractionResult.Value(value)     => ParamExtractionResult.Value(f(value))
        case ParamExtractionResult.ParamValueIsNone => ParamExtractionResult.ParamValueIsNone
        case ParamExtractionResult.MissingParam     => ParamExtractionResult.MissingParam
      }
    }

  }

}
