package pl.touk.nussknacker.engine.definition

import java.lang.reflect.{InvocationTargetException, Method}

import com.typesafe.scalalogging.LazyLogging
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.MethodToInvoke
import pl.touk.nussknacker.engine.api.definition.{Parameter, WithExplicitMethodToInvoke}
import pl.touk.nussknacker.engine.api.process.{ClassExtractionSettings, SingleNodeConfig, WithCategories}
import pl.touk.nussknacker.engine.api.typed.ClazzRef
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor._
import pl.touk.nussknacker.engine.definition.MethodDefinitionExtractor.MethodDefinition
import pl.touk.nussknacker.engine.types.TypesInformationExtractor

import scala.runtime.BoxedUnit

class DefinitionExtractor[T](methodDefinitionExtractor: MethodDefinitionExtractor[T]) {

  def extract(objWithCategories: WithCategories[T], nodeConfig: SingleNodeConfig): ObjectWithMethodDef = {
    val obj = objWithCategories.value
    val methodDef = (obj match {
      case e:WithExplicitMethodToInvoke =>
        WithExplicitMethodToInvokeMethodDefinitionExtractor.extractMethodDefinition(e,
          classOf[WithExplicitMethodToInvoke].getMethods.find(_.getName == "invoke").get, nodeConfig)
      case _ =>
        methodDefinitionExtractor.extractMethodDefinition(obj, findMethodToInvoke(obj), nodeConfig)
    }).fold(msg => throw new IllegalArgumentException(msg), identity)
    ObjectWithMethodDef(obj, methodDef, ObjectDefinition(
      methodDef.orderedDependencies.definedParameters,
      methodDef.returnType,
      objWithCategories.categories,
      nodeConfig
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

    // TODO: Use ContextTransformation API to check if custom node is adding some output variable
    def hasNoReturn : Boolean = Set[TypingResult](Typed[Void], Typed[Unit], Typed[BoxedUnit]).contains(returnType)

  }

  case class ObjectWithType(obj: Any, typ: TypingResult)

  case class ObjectWithMethodDef(obj: Any,
                                 methodDef: MethodDefinition,
                                 objectDefinition: ObjectDefinition) extends ObjectMetadata with LazyLogging {
    def invokeMethod(paramFun: String => Option[AnyRef],
                     outputVariableNameOpt: Option[String],
                     additional: Seq[AnyRef]) : Any = {
      val values = methodDef.orderedDependencies.prepareValues(paramFun, outputVariableNameOpt, additional)
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

    import cats.syntax.semigroup._

    def forMap[T](objs: Map[String, WithCategories[_<:T]], methodExtractor: MethodDefinitionExtractor[T], externalConfig: Map[String, SingleNodeConfig]): Map[String, ObjectWithMethodDef] = {
      objs.map {
        case (id, obj) =>
          val config = externalConfig.getOrElse(id, SingleNodeConfig.zero) |+| obj.nodeConfig
          id -> new DefinitionExtractor(methodExtractor).extract(obj, config)
      }

    }

    def withEmptyConfig[T](obj: T, methodExtractor: MethodDefinitionExtractor[T]): ObjectWithMethodDef = {
      new DefinitionExtractor(methodExtractor).extract(WithCategories(obj), SingleNodeConfig.zero)
    }
  }

  object TypesInformation {
    def extract(services: Iterable[ObjectWithMethodDef],
                sourceFactories: Iterable[ObjectWithMethodDef],
                customNodeTransformers: Iterable[ObjectWithMethodDef],
                signalsFactories: Iterable[ObjectWithMethodDef],
                globalProcessVariables: Iterable[TypingResult])
               (implicit settings: ClassExtractionSettings): Set[TypeInfos.ClazzDefinition] = {

      val objectToExtractClassesFrom = services ++ customNodeTransformers ++ sourceFactories ++ signalsFactories
      val classesToExtractDefinitions = globalProcessVariables ++ objectToExtractClassesFrom.flatMap(extractTypesFromObjectDefinition)
      TypesInformationExtractor.clazzAndItsChildrenDefinition(classesToExtractDefinitions)
    }

    private def extractTypesFromObjectDefinition(obj: ObjectWithMethodDef): List[TypingResult] = {
      def clazzRefsFromParameter(parameter: Parameter): Iterable[TypingResult] = {
        val fromAdditionalVars = parameter.additionalVariables.values
        fromAdditionalVars.toList :+ parameter.typ
      }

      obj.methodDef.returnType :: obj.parameters.flatMap(clazzRefsFromParameter)
    }
  }

  object ObjectDefinition {

    def noParam: ObjectDefinition = ObjectDefinition(List.empty, Typed[Any], List(), SingleNodeConfig.zero)

    def withParams(params: List[Parameter]): ObjectDefinition = ObjectDefinition(params, Typed[Any], List(), SingleNodeConfig.zero)

    def withParamsAndCategories(params: List[Parameter], categories: List[String]): ObjectDefinition =
      ObjectDefinition(params, Typed[Any], categories, SingleNodeConfig.zero)

    def apply(parameters: List[Parameter], returnType: ClazzRef, categories: List[String]): ObjectDefinition = {
      ObjectDefinition(parameters, Typed(returnType), categories, SingleNodeConfig.zero)
    }
  }

}

object TypeInfos {

  @JsonCodec(encodeOnly = true) case class Parameter(name: String, refClazz: ClazzRef)

  @JsonCodec(encodeOnly = true) case class MethodInfo(parameters: List[Parameter], refClazz: ClazzRef, description: Option[String])

  @JsonCodec(encodeOnly = true) case class ClazzDefinition(clazzName: ClazzRef, methods: Map[String, MethodInfo]) {
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