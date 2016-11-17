package pl.touk.esp.engine.definition

import java.lang.reflect.Method

import pl.touk.esp.engine.api.process.{SinkFactory, SourceFactory, WithCategories}
import pl.touk.esp.engine.api.{MethodToInvoke, ParamName, Service}
import pl.touk.esp.engine.definition.DefinitionExtractor._
import pl.touk.esp.engine.types.EspTypeUtils
import pl.touk.esp.engine.util.ReflectUtils

trait DefinitionExtractor[T] {

  def extract(obj: T, categories: List[String]): ObjectDefinition = {
    val methodDef = extractMethodDefinition(obj)
    ObjectDefinition(
      methodDef.orderedParameters.definedParameters,
      ClazzRef(obj.getClass),
      EspTypeUtils.getGenericMethodType(methodDef.method).map(clazz => ClazzRef.apply(clazz.getName)),
      categories
    )
  }

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
        Left(Parameter(name, ClazzRef(extractParameterType(p))))
      }
    }.toList
    MethodDefinition(method, new OrderedParameters(params))
  }

  protected def extractParameterType(p: java.lang.reflect.Parameter) = p.getType

  protected def returnType: Class[_]

  protected def additionalParameters: Set[Class[_]]

}

object DefinitionExtractor {

  trait ObjectMetadata {
    def parameters: List[Parameter]
    def definedClass: ClazzRef
    def categories: List[String]
  }

  case class ObjectWithMethodDef(obj: Any, methodDef: MethodDefinition, objectDefinition: ObjectDefinition) extends ObjectMetadata {
    def method = {
      methodDef.method
    }

    override def parameters = orderedParameters.definedParameters

    def orderedParameters = methodDef.orderedParameters

    override def definedClass: ClazzRef = objectDefinition.definedClass

    override def categories = objectDefinition.categories
  }
  object ObjectWithMethodDef {
    def apply[T](obj: WithCategories[T], extractor: DefinitionExtractor[T]): ObjectWithMethodDef = {
      ObjectWithMethodDef(obj.value, extractor.extractMethodDefinition(obj.value), extractor.extract(obj.value, obj.categories))
    }
  }

  case class MethodDefinition(method: Method, orderedParameters: OrderedParameters)

  case class ClazzRef(refClazzName: String)
  object ClazzRef {
    def apply(clazz: Class[_]): ClazzRef = {
      ClazzRef(clazz.getName)
    }
  }

  case class PlainClazzDefinition(clazzName: ClazzRef, methods: Map[String, ClazzRef]) {
    def getMethod(methodName: String): Option[ClazzRef] = {
      methods.get(methodName)
    }
  }

  object TypesInformation {
    def extract(services: Map[String, ObjectWithMethodDef],
                sourceFactories: Map[String, SourceFactory[_]],
                globalProcessVariables: Map[String, Class[_]]): List[PlainClazzDefinition] = {

      //TODO: czy tutaj potrzebujemy serwisów jako takich?
      val classesToExtractDefinitions = globalProcessVariables.values ++
          sourceFactories.values.map(_.clazz) ++
          services.values.map(sv => EspTypeUtils.getReturnClassForMethod(sv.method))

      classesToExtractDefinitions.flatMap(EspTypeUtils.clazzAndItsChildrenDefinition).toList.distinct
    }
  }

  case class ObjectDefinition(parameters: List[Parameter],
                              definedClass: ClazzRef,
                              returnType: Option[ClazzRef], categories: List[String]) extends ObjectMetadata

  case class Parameter(name: String, typ: ClazzRef)

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
    def noParam: ObjectDefinition = ObjectDefinition(List.empty, ClazzRef(classOf[Null]), None, List())
    def withParams(params: List[Parameter]): ObjectDefinition = ObjectDefinition(params, ClazzRef(classOf[Null]), None, List())

    def withParamsAndCategories(params: List[Parameter], categories: List[String]): ObjectDefinition =
      ObjectDefinition(params, ClazzRef(classOf[Null]), None, categories)

    def apply(parameters: List[Parameter], clazz: Class[_], returnType: Option[Class[_]], categories: List[String]): ObjectDefinition = {
      ObjectDefinition(parameters, ClazzRef(clazz), returnType.map(ClazzRef.apply), categories)
    }
  }

}