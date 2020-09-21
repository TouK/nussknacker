package pl.touk.nussknacker.engine.definition

import java.lang.annotation.Annotation
import java.lang.reflect.{InvocationTargetException, Method}

import com.typesafe.scalalogging.LazyLogging
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.MethodToInvoke
import pl.touk.nussknacker.engine.api.context.transformation.{GenericNodeTransformation, OutputVariableNameValue, SingleInputGenericNodeTransformation, TypedNodeDependencyValue}
import pl.touk.nussknacker.engine.api.definition.{OutputVariableNameDependency, Parameter, TypedNodeDependency, WithExplicitMethodToInvoke}
import pl.touk.nussknacker.engine.api.process.{ClassExtractionSettings, SingleNodeConfig, WithCategories}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.{ObjectMetadata, _}
import pl.touk.nussknacker.engine.definition.MethodDefinitionExtractor.MethodDefinition
import pl.touk.nussknacker.engine.definition.parameter.GenericParameterEnrichment
import pl.touk.nussknacker.engine.types.TypesInformationExtractor

import scala.runtime.BoxedUnit

class DefinitionExtractor[T](methodDefinitionExtractor: MethodDefinitionExtractor[T]) {

  def extract(objWithCategories: WithCategories[T], nodeConfig: SingleNodeConfig): ObjectWithMethodDef = {
    val obj = objWithCategories.value

    def fromMethodDefinition(methodDef: MethodDefinition): StandardObjectWithMethodDef = StandardObjectWithMethodDef(obj, methodDef, ObjectDefinition(
      methodDef.orderedDependencies.definedParameters,
      methodDef.returnType,
      objWithCategories.categories,
      nodeConfig
    ))
    (obj match {
      //TODO: how validators/editors in NodeConfig should be handled for GenericNodeTransformation/WithExplicitMethodToInvoke?
      case e:GenericNodeTransformation[_] =>
        val returnType = if (e.nodeDependencies.contains(OutputVariableNameDependency)) Unknown else Typed[Void]
        val parametersList = GenericParameterEnrichment.enrichParameterDefinitions(e.initialParameters, objWithCategories.nodeConfig)
        val definition = ObjectDefinition(parametersList, returnType, objWithCategories.categories, objWithCategories.nodeConfig)
        Right(GenericNodeTransformationMethodDef(e, definition))
      case e:WithExplicitMethodToInvoke =>
        WithExplicitMethodToInvokeMethodDefinitionExtractor.extractMethodDefinition(e,
          classOf[WithExplicitMethodToInvoke].getMethods.find(_.getName == "invoke").get, nodeConfig).right.map(fromMethodDefinition)
      case _ =>
        methodDefinitionExtractor.extractMethodDefinition(obj, findMethodToInvoke(obj), nodeConfig).right.map(fromMethodDefinition)
    }).fold(msg => throw new IllegalArgumentException(msg), identity)

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

  trait ObjectWithMethodDef extends ObjectMetadata {

    def invokeMethod(params: Map[String, Any],
                     outputVariableNameOpt: Option[String],
                     additional: Seq[AnyRef]) : Any

    def objectDefinition: ObjectDefinition

    def runtimeClass: Class[_]

    def obj: Any

    def annotations: List[Annotation]

    override def parameters: List[Parameter] = objectDefinition.parameters

    override def categories: List[String] = objectDefinition.categories

    override def returnType: TypingResult = objectDefinition.returnType

  }

  abstract class OverriddenObjectWithMethodDef(original: ObjectWithMethodDef) extends ObjectWithMethodDef {

    override def invokeMethod(params: Map[String, Any], outputVariableNameOpt: Option[String], additional: Seq[AnyRef]): Any

    override def objectDefinition: ObjectDefinition = original.objectDefinition

    override def runtimeClass: Class[_] = original.runtimeClass

    override def obj: Any = original.obj

    override def annotations: List[Annotation] = original.annotations
  }

  case class GenericNodeTransformationMethodDef(obj: GenericNodeTransformation[_], objectDefinition: ObjectDefinition) extends ObjectWithMethodDef {

    override def invokeMethod(params: Map[String, Any], outputVariableNameOpt: Option[String], additional: Seq[AnyRef]): Any = {
      val additionalParams = obj.nodeDependencies.map {
        case TypedNodeDependency(klazz) =>
          additional.find(klazz.isInstance).map(TypedNodeDependencyValue)
            .getOrElse(throw new IllegalArgumentException(s"Failed to find dependency: $klazz"))
        case OutputVariableNameDependency => outputVariableNameOpt.map(OutputVariableNameValue).getOrElse(throw new IllegalArgumentException("Output variable not defined"))
        case other => throw new IllegalArgumentException(s"Cannot handle dependency $other")
      }
      val finalStateValue = additional.collectFirst {
        case FinalStateValue(value) => value
      }.getOrElse(throw new IllegalArgumentException("Final state not passed to invokeMethod"))
      //we assume parameters were already validated!
      obj.implementation(params, additionalParams, finalStateValue.asInstanceOf[Option[obj.State]])
    }

    override def runtimeClass: Class[_] = classOf[Any]

    override def annotations: List[Annotation] = Nil
  }

  case class FinalStateValue(value: Option[Any])

  case class StandardObjectWithMethodDef(obj: Any,
                                 methodDef: MethodDefinition,
                                 objectDefinition: ObjectDefinition) extends ObjectWithMethodDef with LazyLogging {
    def invokeMethod(params: Map[String, Any],
                     outputVariableNameOpt: Option[String],
                     additional: Seq[AnyRef]) : Any = {
      val values = methodDef.orderedDependencies.prepareValues(params, outputVariableNameOpt, additional)
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

    override def annotations: List[Annotation] = methodDef.annotations

    override def runtimeClass: Class[_] = methodDef.runtimeClass
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
    def extract(objectToExtractClassesFrom: Iterable[ObjectWithMethodDef])
               (implicit settings: ClassExtractionSettings): Set[TypeInfos.ClazzDefinition] = {
      val classesToExtractDefinitions = objectToExtractClassesFrom.flatMap(extractTypesFromObjectDefinition)
      TypesInformationExtractor.clazzAndItsChildrenDefinition(classesToExtractDefinitions)
    }

    private def extractTypesFromObjectDefinition(obj: ObjectWithMethodDef): List[TypingResult] = {
      def typesFromParameter(parameter: Parameter): Iterable[TypingResult] = {
        val fromAdditionalVars = parameter.additionalVariables.values
        fromAdditionalVars.toList :+ parameter.typ
      }

      //FIXME: it was obj.methodDef.returnType, is it ok to replace with obj.returnType??
      obj.returnType :: obj.parameters.flatMap(typesFromParameter)
    }
  }

  object ObjectDefinition {

    def noParam: ObjectDefinition = ObjectDefinition(List.empty, Unknown, List(), SingleNodeConfig.zero)

    def withParams(params: List[Parameter]): ObjectDefinition = ObjectDefinition(params, Unknown, List(), SingleNodeConfig.zero)

    def withParamsAndCategories(params: List[Parameter], categories: List[String]): ObjectDefinition =
      ObjectDefinition(params, Unknown, categories, SingleNodeConfig.zero)

    def apply(parameters: List[Parameter], returnType: TypingResult, categories: List[String]): ObjectDefinition = {
      ObjectDefinition(parameters, returnType, categories, SingleNodeConfig.zero)
    }
  }

}

object TypeInfos {
  
  @JsonCodec(encodeOnly = true) case class Parameter(name: String, refClazz: TypingResult)

  @JsonCodec(encodeOnly = true) case class MethodInfo(parameters: List[Parameter], refClazz: TypingResult, description: Option[String], varArgs: Boolean)

  case class ClazzDefinition(clazzName: TypingResult, methods: Map[String, List[MethodInfo]]) {

    def getPropertyOrFieldType(methodName: String): Option[TypingResult] = {
      val filtered = methods.get(methodName).toList
        .flatMap(_.filter(_.parameters.isEmpty))
        .map(_.refClazz)
      filtered match {
        case Nil => None
        case nonEmpty => Some(Typed(nonEmpty.toSet))
      }
    }

  }

}
