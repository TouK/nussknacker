package pl.touk.esp.engine.definition

import java.lang.reflect.Method

import pl.touk.esp.engine.api.ParamName
import pl.touk.esp.engine.definition.DefinitionExtractor.{MethodDefinition, ObjectDefinition, OrderedParameters, Parameter}
import pl.touk.esp.engine.util.ReflectUtils

trait DefinitionExtractor[T] {

  protected def returnType: Class[_]
  protected def additionalParameters: Set[Class[_]]

  def extract(obj: T): ObjectDefinition =
    ObjectDefinition(
      extractMethodDefinition(obj).orderedParameters.definedParameters
    )

  def extractMethodDefinition(obj: T): MethodDefinition = {
    val method = obj.getClass.getMethods.find { m =>
      m.getReturnType == returnType
    } getOrElse {
      throw new IllegalArgumentException(s"Missing method with return type: $returnType")
    }

    val params = method.getParameters.map { p =>
      if (additionalParameters.contains(p.getType)) {
        Right(p.getType)
      } else {
        val name = Option(p.getAnnotation(classOf[ParamName]))
          .map(_.value())
          .getOrElse(throw new IllegalArgumentException(s"Parameter $p of $obj has missing @ParamName annotation"))
        Left(Parameter(name, ReflectUtils.fixedClassSimpleNameWithoutParentModule(p.getType)))
      }
    }.toList
    MethodDefinition(method, new OrderedParameters(params))
  }

}

object DefinitionExtractor {

  case class ObjectWithMethodDef(obj: Any, methodDef: MethodDefinition) extends ParametersProvider {
    def method = methodDef.method
    def orderedParameters = methodDef.orderedParameters
    override def parameters = orderedParameters.definedParameters
  }

  case class MethodDefinition(method: Method, orderedParameters: OrderedParameters)

  trait ParametersProvider {
    def parameters: List[Parameter]
  }

  case class ObjectDefinition(parameters: List[Parameter]) extends ParametersProvider

  case class Parameter(name: String, typ: String)

  private[definition] class OrderedParameters(baseOrAdditional: List[Either[Parameter, Class[_]]]) {

    lazy val definedParameters: List[Parameter] = baseOrAdditional.collect {
      case Left(param) => param
    }

    def prepareValues(prepareValue: Parameter => Any,
                      additionalParameters: Seq[Any]): List[AnyRef] = {
      baseOrAdditional.map {
        case Left(param) =>
          prepareValue(param)
        case Right(classOfAdditional) =>
          additionalParameters.find(classOfAdditional.isInstance).get
      }.map(_.asInstanceOf[AnyRef])
    }
  }

}