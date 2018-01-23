package pl.touk.nussknacker.engine.definition

import java.lang.reflect.{InvocationTargetException, Method}

import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.lang3.ClassUtils
import pl.touk.nussknacker.engine.api.MethodToInvoke
import pl.touk.nussknacker.engine.api.process.{ClassExtractionSettings, WithCategories}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor._
import pl.touk.nussknacker.engine.definition.MethodDefinitionExtractor.MethodDefinition
import pl.touk.nussknacker.engine.types.EspTypeUtils

import scala.reflect.ClassTag
import scala.runtime.BoxedUnit

class DefinitionExtractor[T](methodDefinitionExtractor: MethodDefinitionExtractor[T]) {

  def extract(obj: T, methodDef: MethodDefinition, categories: List[String]): ObjectDefinition = {
    ObjectDefinition(
      methodDef.orderedParameters.definedParameters,
      ClazzRef(methodDef.returnType),
      categories
    )
  }

  def extractMethodDefinition(obj: T): MethodDefinition = {
    methodDefinitionExtractor.extractMethodDefinition(obj, findMethodToInvoke(obj))
      .fold(msg => throw new IllegalArgumentException(msg), identity)
  }

  private def findMethodToInvoke(obj: T): Method = {
    val methodsToInvoke = obj.getClass.getMethods.toList.filter { m =>
      m.getAnnotation(classOf[MethodToInvoke]) != null
    }
    methodsToInvoke match {
      case Nil =>
        throw new IllegalArgumentException(s"Missing method to invoke for object: " + obj)
      case head :: Nil =>
        head
      case moreThanOne =>
        throw new IllegalArgumentException(s"More than one method to invoke: " + moreThanOne + " in object: " + obj)
    }
  }

}

object DefinitionExtractor {
  import TypeInfos._

  trait ObjectMetadata {
    def parameters: List[Parameter]

    def returnType: ClazzRef

    def categories: List[String]

    def hasNoReturn : Boolean = Set(classOf[Void], classOf[Unit], classOf[BoxedUnit]).map(_.getName).contains(returnType.refClazzName)

  }

  case class ObjectWithMethodDef(obj: Any,
                                 methodDef: MethodDefinition,
                                 objectDefinition: ObjectDefinition) extends ObjectMetadata with LazyLogging {
    def invokeMethod(paramFun: String => Option[AnyRef], additional: Seq[AnyRef]) : Any = {
      val paramsWithValues = methodDef.orderedParameters.prepareValues(paramFun, additional)
      validateParameters(paramsWithValues)
      val values = paramsWithValues.map(_._2)
      try {
        methodDef.method.invoke(obj, values.toArray: _*)
      } catch {
        case ex: IllegalArgumentException =>
          //this indicates that parameters do not match or argument list is incorrect
          logger.warn(s"Failed to invoke method: ${methodDef.method}, with params: $values", ex)
          throw ex
        //this is somehow an edge case - normally service returns failed future for exceptions
        case ex: InvocationTargetException =>
          throw ex.getTargetException
      }
    }

    override def parameters = methodDef.orderedParameters.definedParameters

    def additionalParameters = methodDef.orderedParameters.additionalParameters

    override def categories = objectDefinition.categories

    override def returnType = objectDefinition.returnType

    def as[T] : T = obj.asInstanceOf[T]

    private def validateParameters(values: List[(String, AnyRef)]) = {
      val method = methodDef.method
      if (method.getParameterCount != values.size) {
        throw new IllegalArgumentException(s"Failed to invoke method: ${methodDef.method}, " +
          s"with params: $values, invalid parameter count")
      }
      method.getParameterTypes.zip(values).zipWithIndex.foreach { case ((klass, (paramName, value)), idx) =>
        if (value != null && !EspTypeUtils.signatureElementMatches(klass, value.getClass)) {
          throw new IllegalArgumentException(s"Parameter $paramName has invalid class: ${value.getClass.getName}, should be: ${klass.getName}")
        }
      }
    }

  }

  case class ClazzRef(refClazzName: String) {
    def toClass(classLoader: ClassLoader) : Class[_] = ClassUtils.getClass(classLoader, refClazzName)
  }

  case class ObjectDefinition(parameters: List[Parameter],
                              returnType: ClazzRef, categories: List[String]) extends ObjectMetadata

  case class Parameter(name: String, typ: ClazzRef, restriction: Option[ParameterRestriction] = None)

  //TODO: add validation of restrictions during compilation...
  //this can be used for different restrictions than list of values, e.g. encode '> 0' conditions and so on...
  sealed trait ParameterRestriction

  case class StringValues(values: List[String]) extends ParameterRestriction

  object ObjectWithMethodDef {
    def apply[T](obj: WithCategories[_<:T], methodExtractor: MethodDefinitionExtractor[T]): ObjectWithMethodDef = {
      val objectExtractor = new DefinitionExtractor(methodExtractor)
      val methodDefinition = objectExtractor.extractMethodDefinition(obj.value)
      ObjectWithMethodDef(obj.value, methodDefinition, objectExtractor.extract(obj.value, methodDefinition, obj.categories))
    }
  }

  object ClazzRef {

    def unknown: ClazzRef = ClazzRef[Any]

    def apply(clazz: Class[_]): ClazzRef = {
      ClazzRef(clazz.getName)
    }
    def apply[T:ClassTag]: ClazzRef = {
      ClazzRef(implicitly[ClassTag[T]].runtimeClass.getName)
    }
  }

  object TypesInformation {
    def extract(services: Iterable[ObjectWithMethodDef],
                sourceFactories: Iterable[ObjectWithMethodDef],
                customNodeTransformers: Iterable[ObjectWithMethodDef],
                signalsFactories: Iterable[ObjectWithMethodDef],
                globalProcessVariables: Iterable[Class[_]])
               (implicit settings: ClassExtractionSettings): List[ClazzDefinition] = {

      //TODO: do we need services here?
      val classesToExtractDefinitions =
      globalProcessVariables ++
        (services ++ customNodeTransformers ++ sourceFactories ++ signalsFactories).map(sv => sv.methodDef.returnType)

      EspTypeUtils.clazzAndItsChildrenDefinition(classesToExtractDefinitions)
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

object TypeInfos {

  //FIXME we should use ClazzRef instead of String here, but it will require some frontend changes
  case class Parameter private(name: String, refClazzName: String)
  object Parameter {
    def apply(name: String, clazz: ClazzRef): Parameter = {
      new Parameter(name, clazz.refClazzName)
    }
  }

  //FIXME we should use ClazzRef instead of String here, but it will require some frontend changes
  case class MethodInfo private(parameters: List[Parameter], refClazzName: String, description: Option[String])
  object MethodInfo {
    def apply(parameters: List[Parameter], returnType: ClazzRef, description: Option[String]): MethodInfo = {
      new MethodInfo(parameters, returnType.refClazzName, description)
    }
  }

  case class ClazzDefinition(clazzName: ClazzRef, methods: Map[String, MethodInfo]) {
    def getMethod(methodName: String): Option[ClazzRef] = {
      methods.get(methodName).map(m => ClazzRef(m.refClazzName))
    }
  }

}