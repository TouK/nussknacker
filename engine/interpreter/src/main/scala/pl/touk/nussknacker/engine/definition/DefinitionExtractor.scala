package pl.touk.nussknacker.engine.definition

import java.lang.reflect.{InvocationTargetException, Method}

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.MethodToInvoke
import pl.touk.nussknacker.engine.api.process.{ClassExtractionSettings, SingleNodeConfig, WithCategories}
import pl.touk.nussknacker.engine.api.typed.ClazzRef
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.api.definition.{Parameter, WithExplicitMethodToInvoke}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor._
import pl.touk.nussknacker.engine.definition.MethodDefinitionExtractor.MethodDefinition
import pl.touk.nussknacker.engine.types.TypesInformationExtractor

import scala.runtime.BoxedUnit

class DefinitionExtractor[T](methodDefinitionExtractor: MethodDefinitionExtractor[T]) {

  def extract(objWithCategories: WithCategories[T]): ObjectWithMethodDef = {
    val obj = objWithCategories.value
    val methodDef = (obj match {
      case e:WithExplicitMethodToInvoke =>
        WithExplicitMethodToInvokeMethodDefinitionExtractor.extractMethodDefinition(e,
          classOf[WithExplicitMethodToInvoke].getMethods.find(_.getName == "invoke").get)
      case _ =>
        methodDefinitionExtractor.extractMethodDefinition(obj, findMethodToInvoke(obj))
    }).fold(msg => throw new IllegalArgumentException(msg), identity)
    ObjectWithMethodDef(obj, methodDef, ObjectDefinition(
      methodDef.orderedParameters.definedParameters,
      methodDef.returnType,
      objWithCategories.categories,
      objWithCategories.nodeConfig
    ))
  }

  private def findMethodToInvoke(obj: Any): Method = {
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
  //import TypeInfos._

  trait ObjectMetadata {
    def parameters: List[Parameter]

    def returnType: TypingResult

    def categories: List[String]

    def hasNoReturn : Boolean = Set(Typed[Void], Typed[Unit], Typed[BoxedUnit]).contains(returnType)

  }

  case class ObjectWithMethodDef(obj: Any,
                                 methodDef: MethodDefinition,
                                 objectDefinition: ObjectDefinition) extends ObjectMetadata with LazyLogging {
    def invokeMethod(paramFun: String => Option[AnyRef], additional: Seq[AnyRef]) : Any = {
      val values = methodDef.orderedParameters.prepareValues(paramFun, additional)
      try {
        methodDef.invocation(obj, values)
      } catch {
        case ex: IllegalArgumentException =>
          //this indicates that parameters do not match or argument list is incorrect
          logger.debug(s"Failed to invoke method: ${methodDef.name}, with params: $values", ex)
          throw ex
        //this is somehow an edge case - normally service returns failed future for exceptions
        case ex: InvocationTargetException =>
          throw ex.getTargetException
      }
    }

    override def parameters = objectDefinition.parameters

    override def categories = objectDefinition.categories

    override def returnType = objectDefinition.returnType

    def as[T] : T = obj.asInstanceOf[T]

  }

  case class PlainClazzDefinition(clazzName: ClazzRef, methods: Map[String, ClazzRef]) {
    def getMethod(methodName: String): Option[ClazzRef] = {
      methods.get(methodName)
    }
  }

  case class ObjectDefinition(parameters: List[Parameter],
                              returnType: TypingResult,
                              categories: List[String],
                              nodeConfig: SingleNodeConfig) extends ObjectMetadata


  object ObjectWithMethodDef {
    def apply[T](obj: WithCategories[_<:T], methodExtractor: MethodDefinitionExtractor[T]): ObjectWithMethodDef = {
      new DefinitionExtractor(methodExtractor).extract(obj)
    }
  }

  object TypesInformation {
    def extract(services: Iterable[ObjectWithMethodDef],
                sourceFactories: Iterable[ObjectWithMethodDef],
                customNodeTransformers: Iterable[ObjectWithMethodDef],
                signalsFactories: Iterable[ObjectWithMethodDef],
                globalProcessVariables: Iterable[TypingResult])
               (implicit settings: ClassExtractionSettings): List[TypeInfos.ClazzDefinition] = {

      val objectToExtractClassesFrom = services ++ customNodeTransformers ++ sourceFactories ++ signalsFactories
      val classesToExtractDefinitions = globalProcessVariables ++ objectToExtractClassesFrom.flatMap(extractTypesFromObjectDefinition)
      TypesInformationExtractor.clazzAndItsChildrenDefinition(classesToExtractDefinitions)
    }

    private def extractTypesFromObjectDefinition(obj: ObjectWithMethodDef): List[TypingResult] = {
      def clazzRefFromTyped(typed: TypingResult): Iterable[ClazzRef] = typed match {
        case Typed(possibleTypes) => possibleTypes.map(t => ClazzRef(t.klass))
        case _ => Set()
      }

      def clazzRefsFromParameter(parameter: Parameter): Iterable[ClazzRef] = {
        val fromAdditionalVars = parameter.additionalVariables.values.flatMap(clazzRefFromTyped)
        fromAdditionalVars.toList :+ parameter.typ
      }

      obj.methodDef.returnType :: obj.parameters.flatMap(clazzRefsFromParameter).map(Typed(_))
    }
  }

  object ObjectDefinition {

    def noParam: ObjectDefinition = ObjectDefinition(List.empty, Typed[Null], List(), SingleNodeConfig.zero)

    def withParams(params: List[Parameter]): ObjectDefinition = ObjectDefinition(params, Typed[Null], List(), SingleNodeConfig.zero)

    def withParamsAndCategories(params: List[Parameter], categories: List[String]): ObjectDefinition =
      ObjectDefinition(params, Typed[Null], categories, SingleNodeConfig.zero)

    def apply(parameters: List[Parameter], returnType: ClazzRef, categories: List[String]): ObjectDefinition = {
      ObjectDefinition(parameters, Typed(returnType), categories, SingleNodeConfig.zero)
    }
  }

}

object TypeInfos {

  case class Parameter(name: String, refClazz: ClazzRef)

  case class MethodInfo(parameters: List[Parameter], refClazz: ClazzRef, description: Option[String])

  case class ClazzDefinition(clazzName: ClazzRef, methods: Map[String, MethodInfo]) {
    def getMethodClazzRef(methodName: String): Option[ClazzRef] = {
      methods.get(methodName).map(_.refClazz)
    }

    def getPropertyOrFieldClazzRef(methodName: String): Option[ClazzRef] = {
      val filteredMethods = methods.filter(_._2.parameters.isEmpty)
      val methodInfoes = filteredMethods.get(methodName)
      methodInfoes.map(_.refClazz)
    }
  }

}