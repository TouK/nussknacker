package pl.touk.nussknacker.engine.definition.component.methodbased

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.MissingOutputVariableException
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.api.util.ReflectUtils

import java.lang.reflect.{InvocationTargetException, Method}

class MethodDefinition(
    method: Method,
    orderedDependencies: OrderedDependencies,
    // TODO: remove after full switch to ContextTransformation API
    val returnType: TypingResult,
    val runtimeClass: Class[_]
) extends Serializable
    with LazyLogging {

  def definedParameters: List[Parameter] = orderedDependencies.definedParameters

  def invoke(
      obj: Any,
      params: Map[ParameterName, Any],
      outputVariableNameOpt: Option[String],
      additional: Seq[AnyRef]
  ): AnyRef = {
    val values = orderedDependencies.prepareValues(params, outputVariableNameOpt, additional)
    try {
      method.invoke(obj, values.map(_.asInstanceOf[Object]): _*)
    } catch {
      case ex: IllegalArgumentException =>
        // this usually indicates that parameters do not match or argument list is incorrect
        logger.debug(s"Failed to invoke method: ${method.getName}, with params: $values", ex)

        def className(obj: Any) =
          Option(obj).map(o => ReflectUtils.simpleNameWithoutSuffix(o.getClass)).getOrElse("null")

        val parameterValues = orderedDependencies.definedParameters.map(_.name).map(params)
        throw new IllegalArgumentException(
          s"""Failed to invoke "${method.getName}" on ${className(obj)} with parameter types: ${parameterValues.map(
              className
            )}: ${ex.getMessage}""",
          ex
        )
      // this is somehow an edge case - normally service returns failed future for exceptions
      case ex: InvocationTargetException =>
        throw ex.getTargetException
    }
  }

}

class OrderedDependencies(dependencies: List[NodeDependency]) extends Serializable {

  lazy val definedParameters: List[Parameter] = dependencies.collect { case param: Parameter =>
    param
  }

  def prepareValues(
      values: Map[ParameterName, Any],
      outputVariableNameOpt: Option[String],
      additionalDependencies: Seq[AnyRef]
  ): List[Any] = {
    dependencies.map {
      case param: Parameter =>
        values.getOrElse(param.name, throw new IllegalArgumentException(s"Missing parameter: ${param.name.value}"))
      case OutputVariableNameDependency =>
        outputVariableNameOpt.getOrElse(throw MissingOutputVariableException)
      case TypedNodeDependency(clazz) =>
        val found = additionalDependencies.find(clazz.isInstance)
        found.getOrElse {
          throw new IllegalArgumentException(s"Missing additional parameter of class: ${clazz.getName}")
        }
    }
  }

}
