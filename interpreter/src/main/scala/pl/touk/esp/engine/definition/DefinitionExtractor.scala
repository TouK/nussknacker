package pl.touk.esp.engine.definition

import java.lang.reflect.Method

import pl.touk.esp.engine.api.{MethodToInvoke, ParamName}
import pl.touk.esp.engine.definition.DefinitionExtractor.{MethodDefinition, ObjectDefinition, OrderedParameters, Parameter}
import pl.touk.esp.engine.util.ReflectUtils

trait DefinitionExtractor[T] {

  def extract(obj: T): ObjectDefinition =
    ObjectDefinition(
      extractMethodDefinition(obj).orderedParameters.definedParameters
    )

  def extractMethodDefinition(obj: T): MethodDefinition = {

    val methods = obj.getClass.getMethods

    def findByReturnType = methods.find { m =>
      m.getReturnType == returnType
    }
    def findByAnnotation = methods.find { m =>
      m.getAnnotation(classOf[MethodToInvoke]) != null
    }

    val method = findByAnnotation orElse findByReturnType getOrElse {
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

  protected def returnType: Class[_]

  protected def additionalParameters: Set[Class[_]]

}

object DefinitionExtractor {

  trait ParametersProvider {
    def parameters: List[Parameter]
  }

  case class ObjectWithMethodDef(obj: Any, methodDef: MethodDefinition) extends ParametersProvider {
    def method = methodDef.method

    override def parameters = orderedParameters.definedParameters

    def orderedParameters = methodDef.orderedParameters
  }

  case class MethodDefinition(method: Method, orderedParameters: OrderedParameters)

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

  object ObjectDefinition {
    def noParam: ObjectDefinition = ObjectDefinition(List.empty)
  }

}