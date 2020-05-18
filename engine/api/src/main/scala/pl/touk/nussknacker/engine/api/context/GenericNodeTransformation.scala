package pl.touk.nussknacker.engine.api.context

import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.api.definition.{NodeDependency, Parameter}

/*
  The idea is this would be low-level API, with more accessible DSLs/builders - also with annotation based configuration
  I'd like to handle only this trait (or sth similar) in compiler - instead of MethodToInvoke, WithExplicitMethodToInvoke etc
  Note: this trait is used without @MethodToInvoke. Usages with @MethodToInvoke can be implemented using GenericNodeTransformation
  by NK itself (using more or less current DefinitionExtractor logic)
 */
trait GenericNodeTransformation[T] {

  type InputContext

  //we pass empty option when we want to get e.g. initial parameters/return type etc.
  def contextTransformation(context: Option[DefinitionContext[InputContext]]): TransformationResult[T]

  //TODO: handle in compilation
  def implementation(params: Map[String, Any], additionalParams: List[Any]): AnyRef

}

trait SingleInputGenericNodeTransformation[T] extends GenericNodeTransformation[T] {
  type InputContext = ValidationContext
}

trait BranchGenericNodeTransformation[T] extends GenericNodeTransformation[T] {
  type InputContext = Map[String, ValidationContext]
}

//we assume that these are the data that we get from usage in one node
//ParameterEvaluation is wrapper on Expression
case class DefinitionContext[InputContext](context: InputContext,
                                           //we get expressions from NodeData here
                                           //we cannot just pass Any (evaluated from expression) because it's Parameter that defines how
                                           //expression is evaluated...
                                           params: Map[String, ParameterEvaluation],
                                           additionalParams: List[Any])

trait ParameterEvaluation {
  //Any can be also e.g LazyParameter
  def determine(definition: Parameter): ValidatedNel[ProcessCompilationError, Any]
}

//TODO: what if we cannot determine parameters/context? With some "fatal validation error"?
case class TransformationResult[T](errors: List[ProcessCompilationError],
                                   parameters: List[Parameter],
                                   nodeDependencies: List[NodeDependency],
                                   //can be empty when initial parameters computed?
                                   outputContext: Option[ValidationContext])


