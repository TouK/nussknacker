package pl.touk.nussknacker.engine.api.context.transformation

import pl.touk.nussknacker.engine.api.context.transformation.GenericNodeTransformation.NodeTransformationDefinition
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.definition.{NodeDependency, Parameter, WithExplicitMethodToInvoke}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.Unknown

/*
  The idea is this would be low-level API, with more accessible DSLs/builders - also with annotation based configuration
  I'd like to handle only this trait (or sth similar) in compiler - instead of MethodToInvoke, WithExplicitMethodToInvoke etc
  Note: this trait is used without @MethodToInvoke. Usages with @MethodToInvoke can be implemented using GenericNodeTransformation
  by NK itself (using more or less current DefinitionExtractor logic)
 */
object GenericNodeTransformation {

  //TODO: what if we cannot determine parameters/context? With some "fatal validation error"?
  type NodeTransformationDefinition = PartialFunction[List[(String, DefinedParameter)], TransformationStepResult]

}

trait GenericNodeTransformation[T] {

  type InputContext

  def contextTransformation(context: InputContext, dependencies: List[NodeDependencyValue]): NodeTransformationDefinition

  def initialParameters: List[Parameter]

  //TODO: handle in compilation
  def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue]): AnyRef

  //Here we assume that this list is fixed - cannot be changed depending on parameter values
  def nodeDependencies: List[NodeDependency]

}

trait SingleInputGenericNodeTransformation[T] extends GenericNodeTransformation[T] {
  type InputContext = ValidationContext
}

trait BranchGenericNodeTransformation[T] extends GenericNodeTransformation[T] {
  type InputContext = Map[String, ValidationContext]
}


//TODO: this is temporary solution, won't be able to handle adding parameters etc.
trait WithExplicitMethodToInvokeTransformation extends WithExplicitMethodToInvoke {
  self: SingleInputGenericNodeTransformation[_] =>

  //TODO: we'd like to have proper type also here!
  override def parameterDefinition: List[Parameter] = initialParameters

  override def returnType: typing.TypingResult = Unknown

  override def runtimeClass: Class[_] = classOf[Any]

  override def additionalDependencies: List[Class[_]] = Nil

  override def invoke(params: List[AnyRef]): AnyRef = implementation(initialParameters.map(_.name).zip(params).toMap, Nil)

}