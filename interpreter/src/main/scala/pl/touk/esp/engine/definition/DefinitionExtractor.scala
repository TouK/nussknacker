package pl.touk.esp.engine.definition

import java.lang.reflect.Method

import pl.touk.esp.engine.api.process.WithCategories
import pl.touk.esp.engine.api.{MethodToInvoke, ParamName}
import pl.touk.esp.engine.definition.DefinitionExtractor._
import pl.touk.esp.engine.types.EspTypeUtils

trait DefinitionExtractor[T] {

  def extract(obj: T, methodDef: MethodDefinition, categories: List[String]): ObjectDefinition = {
    ObjectDefinition(
      methodDef.orderedParameters.definedParameters,
      ClazzRef(methodDef.returnType),
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
          .getOrElse(throw new IllegalArgumentException(s"Parameter $p of $obj and method : ${method.getName} has missing @ParamName annotation"))
        Left(Parameter(name, ClazzRef(extractParameterType(p))))
      }
    }.toList
    MethodDefinition(method, extractReturnTypeFromMethod(obj, method), new OrderedParameters(params))
  }
  protected def extractReturnTypeFromMethod(obj: T, method: Method) = {
    val typeFromAnnotation = Option(method.getAnnotation(classOf[MethodToInvoke]))
      .filterNot(_.returnType() == classOf[Object])
      .flatMap[Class[_]](ann => Option(ann.returnType()))
    val typeFromSignature = EspTypeUtils.getGenericMethodType(method)

    typeFromAnnotation.orElse(typeFromSignature).getOrElse(classOf[Any])
  }

  protected def returnType: Class[_]

  protected def additionalParameters: Set[Class[_]]

  protected def extractParameterType(p: java.lang.reflect.Parameter) = p.getType

}

object DefinitionExtractor {

  trait ObjectMetadata {
    def parameters: List[Parameter]

    def returnType: ClazzRef

    def categories: List[String]
  }

  case class ObjectWithMethodDef(obj: Any,
                                 methodDef: MethodDefinition,
                                 objectDefinition: ObjectDefinition) extends ObjectMetadata {
    def invokeMethod(args: List[AnyRef]) = {
      methodDef.method.invoke(obj, args.toArray: _*)
    }

    override def parameters = orderedParameters.definedParameters

    def orderedParameters = methodDef.orderedParameters

    override def categories = objectDefinition.categories

    override def returnType = objectDefinition.returnType

  }

  case class MethodDefinition(method: Method, returnType: Class[_], orderedParameters: OrderedParameters)

  case class ClazzRef(refClazzName: String)

  case class PlainClazzDefinition(clazzName: ClazzRef, methods: Map[String, ClazzRef]) {
    def getMethod(methodName: String): Option[ClazzRef] = {
      methods.get(methodName)
    }
  }

  case class ObjectDefinition(parameters: List[Parameter],
                              returnType: ClazzRef, categories: List[String]) extends ObjectMetadata

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

  object ObjectWithMethodDef {
    def apply[T](obj: WithCategories[T], extractor: DefinitionExtractor[T]): ObjectWithMethodDef = {
      val methodDefinition = extractor.extractMethodDefinition(obj.value)
      ObjectWithMethodDef(obj.value, methodDefinition, extractor.extract(obj.value, methodDefinition, obj.categories))
    }
  }

  object ClazzRef {
    def apply(clazz: Class[_]): ClazzRef = {
      ClazzRef(clazz.getName)
    }
  }

  object TypesInformation {
    def extract(services: Iterable[ObjectWithMethodDef],
                sourceFactories: Iterable[ObjectWithMethodDef],
                customNodeTransformers: Iterable[ObjectWithMethodDef],
                globalProcessVariables: Iterable[Class[_]]): List[PlainClazzDefinition] = {

      //TODO: czy tutaj potrzebujemy serwisów jako takich?
      val classesToExtractDefinitions =
      globalProcessVariables ++
        (services ++ customNodeTransformers ++ sourceFactories).map(sv => sv.methodDef.returnType)

      classesToExtractDefinitions.flatMap(EspTypeUtils.clazzAndItsChildrenDefinition).toList.distinct
    }
  }

  object ObjectDefinition {
    def noParam: ObjectDefinition = ObjectDefinition(List.empty, ClazzRef(classOf[Null]), List())

    def withParams(params: List[Parameter]): ObjectDefinition = ObjectDefinition(params, ClazzRef(classOf[Null]), List())

    def withParamsAndCategories(params: List[Parameter], categories: List[String]): ObjectDefinition =
      ObjectDefinition(params, ClazzRef(classOf[Null]), categories)

    def apply(parameters: List[Parameter], returnType: Class[_], categories: List[String]): ObjectDefinition = {
      ObjectDefinition(parameters, ClazzRef(returnType), categories)
    }
  }

}