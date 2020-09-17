package pl.touk.nussknacker.engine.javaapi.context.transformation

import java.util.Optional

import pl.touk.nussknacker.engine.api.CustomStreamTransformer
import pl.touk.nussknacker.engine.api.context.transformation._
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition.{NodeDependency, Parameter}
import pl.touk.nussknacker.engine.api.process.{Source, SourceFactory}

import scala.collection.JavaConverters._

trait JavaGenericTransformation[T, VC, PAR, ST] {

  def contextTransformation(context: VC,
                            dependencies: java.util.List[NodeDependencyValue],
                            nodeId: ProcessCompilationError.NodeId,
                            parameters: java.util.Map[String, PAR],
                            state: Optional[ST]): JavaTransformationStepResult[ST]

  def initialParameters: java.util.List[Parameter]

  def implementation(params: java.util.Map[String, Any], dependencies: java.util.List[NodeDependencyValue], finalState: java.util.Optional[ST]): T

  def nodeDependencies: java.util.List[NodeDependency]

}

trait JavaGenericSingleTransformation[T, ST] extends JavaGenericTransformation[T, ValidationContext, DefinedSingleParameter, ST]

trait JavaGenericJoinTransformation[T, ST] extends JavaGenericTransformation[T, java.util.Map[String, ValidationContext], BaseDefinedParameter, ST]

trait JavaSourceFactoryGenericTransformation[ST] extends JavaGenericSingleTransformation[Source[_ <: AnyRef], ST] {

  def clazz: Class[_]

}

trait GenericContextTransformationWrapper[T, VC, PAR, ST] extends GenericNodeTransformation[T] {

  override type State = ST

  def javaDef: JavaGenericTransformation[T, VC, PAR, ST]

  override def initialParameters: List[Parameter] = javaDef.initialParameters.asScala.toList

  override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalState: Option[State]): T =
    javaDef.implementation(params.asJava, dependencies.asJava, java.util.Optional.ofNullable(finalState.getOrElse(null.asInstanceOf[State])))

  override def nodeDependencies: List[NodeDependency] = javaDef.nodeDependencies.asScala.toList

}

class SourceFactoryGenericContextTransformationWrapper[ST](val javaDef: JavaSourceFactoryGenericTransformation[ST])
  extends SourceFactory[Object] with SingleInputGenericNodeTransformation[Source[Object]]
    with GenericContextTransformationWrapper[Source[Object], ValidationContext, DefinedSingleParameter, ST] {

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(implicit nodeId: ProcessCompilationError.NodeId): NodeTransformationDefinition = {
    case step => javaDef.contextTransformation(context, dependencies.asJava, nodeId, step.parameters.toMap.asJava, java.util.Optional.ofNullable(step.state.getOrElse(null.asInstanceOf[State]))) match {
      case JavaNextParameters(parameters, errors, state) => NextParameters(parameters.asScala.toList, errors.asScala.toList, Option(state.orElse(null.asInstanceOf[ST])))
      case JavaFinalResults(finalContext, errors, state) => FinalResults(finalContext, errors.asScala.toList, Option(state.orElse(null.asInstanceOf[ST])))
    }
  }

  override def clazz: Class[_] = javaDef.clazz

}

class JoinGenericContextTransformationWrapper[ST](javaDef: JavaGenericJoinTransformation[_ <: AnyRef, ST])
  extends CustomStreamTransformer with JoinGenericNodeTransformation[Object] {

  override type State = ST

  override def canHaveManyInputs: Boolean = true

  override def contextTransformation(context: Map[String, ValidationContext], dependencies: List[NodeDependencyValue])(implicit nodeId: ProcessCompilationError.NodeId): NodeTransformationDefinition = {
    case step => javaDef.contextTransformation(context.asJava, dependencies.asJava, nodeId, step.parameters.toMap.asJava, java.util.Optional.ofNullable(step.state.getOrElse(null.asInstanceOf[State]))) match {
      case JavaNextParameters(parameters, errors, state) => NextParameters(parameters.asScala.toList, errors.asScala.toList, Option(state.orElse(null.asInstanceOf[ST])))
      case JavaFinalResults(finalContext, errors, state) => FinalResults(finalContext, errors.asScala.toList, Option(state.orElse(null.asInstanceOf[ST])))
    }
  }

  override def initialParameters: List[Parameter] = javaDef.initialParameters.asScala.toList

  override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalState: Option[State]): Object =
    javaDef.implementation(params.asJava, dependencies.asJava, java.util.Optional.ofNullable(finalState.getOrElse(null.asInstanceOf[State])))

  override def nodeDependencies: List[NodeDependency] = javaDef.nodeDependencies.asScala.toList

}


sealed trait JavaTransformationStepResult[ST]

case class JavaNextParameters[ST](parameters: java.util.List[Parameter],
                                  errors: java.util.List[ProcessCompilationError],
                                  state: java.util.Optional[ST]) extends JavaTransformationStepResult[ST]

case class JavaFinalResults[ST](finalContext: ValidationContext,
                                errors: java.util.List[ProcessCompilationError],
                                state: java.util.Optional[ST]) extends JavaTransformationStepResult[ST]