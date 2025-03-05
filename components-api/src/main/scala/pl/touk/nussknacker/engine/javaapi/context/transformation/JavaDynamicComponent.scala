package pl.touk.nussknacker.engine.javaapi.context.transformation

import pl.touk.nussknacker.engine.api.{CustomStreamTransformer, NodeId, Params}
import pl.touk.nussknacker.engine.api.component.UnboundedStreamComponent
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.context.transformation._
import pl.touk.nussknacker.engine.api.definition.{NodeDependency, Parameter}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.{Source, SourceFactory}

import java.util.Optional
import scala.jdk.CollectionConverters._

trait JavaDynamicComponent[T, VC, PAR, ST] {

  def contextTransformation(
      context: VC,
      dependencies: java.util.List[NodeDependencyValue],
      nodeId: NodeId,
      parameters: java.util.Map[ParameterName, PAR],
      state: Optional[ST]
  ): JavaTransformationStepResult[ST]

  def implementation(
      params: java.util.Map[ParameterName, Any],
      dependencies: java.util.List[NodeDependencyValue],
      finalState: java.util.Optional[ST]
  ): T

  def nodeDependencies: java.util.List[NodeDependency]

}

trait JavaSingleInputDynamicComponent[T, ST]
    extends JavaDynamicComponent[T, ValidationContext, DefinedSingleParameter, ST] {

  def canBeEnding: Boolean = false

}

trait JavaJoinDynamicComponent[T, ST]
    extends JavaDynamicComponent[T, java.util.Map[String, ValidationContext], BaseDefinedParameter, ST] {

  def canBeEnding: Boolean = false

}

trait JavaSourceFactoryDynamicComponent[ST] extends JavaSingleInputDynamicComponent[Source, ST] {

  def clazz: Class[_]

}

trait DynamicComponentWrapper[T, VC, PAR, ST] { self: DynamicComponent[T] =>

  override type State = ST

  def javaDef: JavaDynamicComponent[T, VC, PAR, ST]

  override def implementation(
      params: Params,
      dependencies: List[NodeDependencyValue],
      finalState: Option[State]
  ): T =
    javaDef.implementation(
      params.nameToValueMap.asJava,
      dependencies.asJava,
      java.util.Optional.ofNullable(finalState.getOrElse(null.asInstanceOf[State]))
    )

  override def nodeDependencies: List[NodeDependency] = javaDef.nodeDependencies.asScala.toList

}

class SingleInputDynamicComponentWrapper[T, ST](val javaDef: JavaSingleInputDynamicComponent[T, ST])
    extends CustomStreamTransformer
    with SingleInputDynamicComponent[T]
    with DynamicComponentWrapper[T, ValidationContext, DefinedSingleParameter, ST] {

  override def canBeEnding: Boolean = javaDef.canBeEnding

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition = { case step =>
    javaDef.contextTransformation(
      context,
      dependencies.asJava,
      nodeId,
      step.parameters.toMap.asJava,
      java.util.Optional.ofNullable(step.state.getOrElse(null.asInstanceOf[State]))
    ) match {
      case JavaNextParameters(parameters, errors, state) =>
        NextParameters(parameters.asScala.toList, errors.asScala.toList, Option(state.orElse(null.asInstanceOf[ST])))
      case JavaFinalResults(finalContext, errors, state) =>
        FinalResults(finalContext, errors.asScala.toList, Option(state.orElse(null.asInstanceOf[ST])))
    }
  }

}

class SourceFactoryDynamicComponentWrapper[ST](val javaDef: JavaSourceFactoryDynamicComponent[ST])
    extends SourceFactory
    with SingleInputDynamicComponent[Source]
    with DynamicComponentWrapper[Source, ValidationContext, DefinedSingleParameter, ST]
    with UnboundedStreamComponent {

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition = { case step =>
    javaDef.contextTransformation(
      context,
      dependencies.asJava,
      nodeId,
      step.parameters.toMap.asJava,
      java.util.Optional.ofNullable(step.state.getOrElse(null.asInstanceOf[State]))
    ) match {
      case JavaNextParameters(parameters, errors, state) =>
        NextParameters(parameters.asScala.toList, errors.asScala.toList, Option(state.orElse(null.asInstanceOf[ST])))
      case JavaFinalResults(finalContext, errors, state) =>
        FinalResults(finalContext, errors.asScala.toList, Option(state.orElse(null.asInstanceOf[ST])))
    }
  }

}

class JoinDynamicComponentWrapper[ST](javaDef: JavaJoinDynamicComponent[_ <: AnyRef, ST])
    extends CustomStreamTransformer
    with JoinDynamicComponent[Object] {

  override type State = ST

  override def canBeEnding: Boolean = javaDef.canBeEnding

  override def contextTransformation(context: Map[String, ValidationContext], dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition = { case step =>
    javaDef.contextTransformation(
      context.asJava,
      dependencies.asJava,
      nodeId,
      step.parameters.toMap.asJava,
      java.util.Optional.ofNullable(step.state.getOrElse(null.asInstanceOf[State]))
    ) match {
      case JavaNextParameters(parameters, errors, state) =>
        NextParameters(parameters.asScala.toList, errors.asScala.toList, Option(state.orElse(null.asInstanceOf[ST])))
      case JavaFinalResults(finalContext, errors, state) =>
        FinalResults(finalContext, errors.asScala.toList, Option(state.orElse(null.asInstanceOf[ST])))
    }
  }

  override def implementation(
      params: Params,
      dependencies: List[NodeDependencyValue],
      finalState: Option[State]
  ): Object =
    javaDef.implementation(
      params.nameToValueMap.asJava,
      dependencies.asJava,
      java.util.Optional.ofNullable(finalState.getOrElse(null.asInstanceOf[State]))
    )

  override def nodeDependencies: List[NodeDependency] = javaDef.nodeDependencies.asScala.toList

}

sealed trait JavaTransformationStepResult[ST]

case class JavaNextParameters[ST](
    parameters: java.util.List[Parameter],
    errors: java.util.List[ProcessCompilationError],
    state: java.util.Optional[ST]
) extends JavaTransformationStepResult[ST]

case class JavaFinalResults[ST](
    finalContext: ValidationContext,
    errors: java.util.List[ProcessCompilationError],
    state: java.util.Optional[ST]
) extends JavaTransformationStepResult[ST]
