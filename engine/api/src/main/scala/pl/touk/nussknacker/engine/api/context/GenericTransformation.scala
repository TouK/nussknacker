package pl.touk.nussknacker.engine.api.context

import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.api.definition.Parameter

/*
  The idea is this would be low-level API, with more accessible DSLs/builders - also with annotation based configuration
  I'd like to handle only this trait (or sth similar) in compiler - instead of MethodToInvoke, WithExplicitMethodToInvoke etc
 */
trait GenericTransformation[T] {

  //TODO: handle branches...
  type InputContext = ValidationContext

  def definition(context: InputContext, params: Map[String, ParameterEvaluation],
                 //TODO: what should we pass here?
                 additionalParams: List[Any]): TransformationResult[T]

  //TODO: handle in compilation
  def implementation(params: Map[String, Any], additionalParams: List[Any]): AnyRef


}

trait ParameterEvaluation {
  //Any can be also e.g LazyParameter
  def determine(definition: Parameter): ValidatedNel[ProcessCompilationError, Any]
}

//TODO: what if we cannot determine parameters/context? With some "fatal validation error"?
case class TransformationResult[T](errors: List[ProcessCompilationError],
                                   parameters: List[Parameter],
                                  //maybe we should just pass all available parameters?
                                   additionalParameters: List[Class[_]],
                                   outputContext: ValidationContext)


