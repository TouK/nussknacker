package pl.touk.nussknacker.engine.definition

import cats.data.ValidatedNel
import cats.implicits.catsSyntaxValidatedId
import java.lang.annotation.Annotation
import java.lang.reflect.{InvocationTargetException, Method}
import com.typesafe.scalalogging.LazyLogging
import io.circe.Encoder
import pl.touk.nussknacker.engine.api.MethodToInvoke
import pl.touk.nussknacker.engine.api.component.SingleComponentConfig
import pl.touk.nussknacker.engine.api.context.transformation.{GenericNodeTransformation, JoinGenericNodeTransformation, OutputVariableNameValue, TypedNodeDependencyValue, WithLegacyStaticParameters}
import pl.touk.nussknacker.engine.api.definition.{OutputVariableNameDependency, Parameter, TypedNodeDependency, WithExplicitTypesToExtract}
import pl.touk.nussknacker.engine.api.expression.ExpressionParseError
import pl.touk.nussknacker.engine.api.expression.ExpressionParseError.{GenericFunctionError, NoVarArgumentTypeError, VarArgumentTypeError}
import pl.touk.nussknacker.engine.api.process.{ClassExtractionSettings, WithCategories}
import pl.touk.nussknacker.engine.api.typed.TypeEncoders
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypingResult, Unknown}
import pl.touk.nussknacker.engine.api.util.ReflectUtils
import pl.touk.nussknacker.engine.definition.DefinitionExtractor._
import pl.touk.nussknacker.engine.definition.MethodDefinitionExtractor.MethodDefinition
import pl.touk.nussknacker.engine.definition.parameter.StandardParameterEnrichment
import pl.touk.nussknacker.engine.types.TypesInformationExtractor

import scala.runtime.BoxedUnit

class DefinitionExtractor[T](methodDefinitionExtractor: MethodDefinitionExtractor[T]) {

  def extract(objWithCategories: WithCategories[T], mergedComponentConfig: SingleComponentConfig): ObjectWithMethodDef = {
    val obj = objWithCategories.value

    def fromMethodDefinition(methodDef: MethodDefinition): StandardObjectWithMethodDef = StandardObjectWithMethodDef(obj, methodDef, ObjectDefinition(
      methodDef.orderedDependencies.definedParameters,
      methodDef.returnType,
      objWithCategories.categories,
      mergedComponentConfig
    ))

    (obj match {
      //TODO: how validators/editors in NodeConfig should be handled for GenericNodeTransformation?
      case e: GenericNodeTransformation[_] =>
        // Here in general we do not have a specified "returnType", hence Undefined/Void
        val returnType = if (e.nodeDependencies.contains(OutputVariableNameDependency)) Unknown else Typed[Void]
        val definition = ObjectDefinition(extractInitialParameters(e, mergedComponentConfig), returnType, objWithCategories.categories, mergedComponentConfig)
        Right(GenericNodeTransformationMethodDef(e, definition))
      case _ =>
        methodDefinitionExtractor.extractMethodDefinition(obj, findMethodToInvoke(obj), mergedComponentConfig).right.map(fromMethodDefinition)
    }).fold(msg => throw new IllegalArgumentException(msg), identity)

  }

  private def extractInitialParameters(obj: GenericNodeTransformation[_], componentConfig: SingleComponentConfig): List[Parameter] = {
    obj match {
      case legacy: WithLegacyStaticParameters =>
        StandardParameterEnrichment.enrichParameterDefinitions(legacy.staticParameters, componentConfig)
      case j: JoinGenericNodeTransformation[_] =>
        // TODO: currently branch parameters must be determined on node template level - aren't enriched dynamically during node validation
        StandardParameterEnrichment.enrichParameterDefinitions(j.initialBranchParameters, componentConfig)
      case _ =>
        List.empty
    }
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

    def categories: Option[List[String]]

    // TODO: Use ContextTransformation API to check if custom node is adding some output variable
    def hasNoReturn: Boolean = Set[TypingResult](Typed[Void], Typed[Unit], Typed[BoxedUnit]).contains(returnType)

  }

  case class ObjectWithType(obj: Any, typ: TypingResult)

  trait ObjectWithMethodDef extends ObjectMetadata {

    def invokeMethod(params: Map[String, Any],
                     outputVariableNameOpt: Option[String],
                     additional: Seq[AnyRef]): Any

    def objectDefinition: ObjectDefinition

    def runtimeClass: Class[_]

    def obj: Any

    def annotations: List[Annotation]

    override def parameters: List[Parameter] = objectDefinition.parameters

    override def categories: Option[List[String]] = objectDefinition.categories

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
                     additional: Seq[AnyRef]): Any = {
      val values = methodDef.orderedDependencies.prepareValues(params, outputVariableNameOpt, additional)
      try {
        methodDef.invocation(obj, values)
      } catch {
        case ex: IllegalArgumentException =>
          //this usually indicates that parameters do not match or argument list is incorrect
          logger.debug(s"Failed to invoke method: ${methodDef.name}, with params: $values", ex)
          def className(obj: Any) = Option(obj).map(o => ReflectUtils.simpleNameWithoutSuffix(o.getClass)).getOrElse("null")
          throw new IllegalArgumentException(
            s"""Failed to invoke "${methodDef.name}" on ${className(obj)} with parameter types: ${values.map(className)}: ${ex.getMessage}""", ex)
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
                              categories: Option[List[String]],
                              componentConfig: SingleComponentConfig) extends ObjectMetadata


  object ObjectWithMethodDef {

    import cats.syntax.semigroup._

    def forMap[T](objs: Map[String, WithCategories[_ <: T]], methodExtractor: MethodDefinitionExtractor[T], externalConfig: Map[String, SingleComponentConfig]): Map[String, ObjectWithMethodDef] = {
      objs.map { case (id, obj) =>
        val config = externalConfig.getOrElse(id, SingleComponentConfig.zero) |+| obj.componentConfig
        id -> (obj, config)
      }.collect {
        case (id, (obj, config)) if !config.disabled =>
          id -> new DefinitionExtractor(methodExtractor).extract(obj, config)
      }
    }

    def withEmptyConfig[T](obj: T, methodExtractor: MethodDefinitionExtractor[T]): ObjectWithMethodDef = {
      new DefinitionExtractor(methodExtractor).extract(WithCategories(obj), SingleComponentConfig.zero)
    }
  }

  object TypesInformation {
    def extract(objectToExtractClassesFrom: Iterable[ObjectWithMethodDef])
               (implicit settings: ClassExtractionSettings): Set[TypeInfos.ClazzDefinition] = {
      val classesToExtractDefinitions = objectToExtractClassesFrom.flatMap(extractTypesFromObjectDefinition)
      TypesInformationExtractor.clazzAndItsChildrenDefinition(classesToExtractDefinitions)
    }

    def extractFromClassList(objectToExtractClassesFromCollection: Iterable[Class[_]])
                       (implicit settings: ClassExtractionSettings): Set[TypeInfos.ClazzDefinition] = {
      val ref = objectToExtractClassesFromCollection.map(Typed.apply)
      TypesInformationExtractor.clazzAndItsChildrenDefinition(ref)
    }

    private def extractTypesFromObjectDefinition(obj: ObjectWithMethodDef): List[TypingResult] = {
      def typesFromParameter(parameter: Parameter): List[TypingResult] = {
        val fromAdditionalVars = parameter.additionalVariables.values.map(_.typingResult)
        fromAdditionalVars.toList :+ parameter.typ
      }

      def explicitTypes(obj: ObjectWithMethodDef): List[TypingResult] = {
        obj.obj match {
          case explicit: WithExplicitTypesToExtract => explicit.typesToExtract
          case _ => Nil
        }
      }

      //FIXME: it was obj.methodDef.returnType, is it ok to replace with obj.returnType??
      obj.returnType :: obj.parameters.flatMap(typesFromParameter) ::: explicitTypes(obj)
    }
  }

  object ObjectDefinition {

    def noParam: ObjectDefinition = ObjectDefinition(List.empty, Unknown, None, SingleComponentConfig.zero)

    def withParams(params: List[Parameter]): ObjectDefinition = ObjectDefinition(params, Unknown, None, SingleComponentConfig.zero)

    def apply(parameters: List[Parameter], returnType: TypingResult): ObjectDefinition = {
      ObjectDefinition(parameters, returnType, None, SingleComponentConfig.zero)
    }
  }

}

object TypeInfos {
  //a bit sad that it isn't derived automatically, but...
  private implicit val tce: Encoder[TypedClass] = TypeEncoders.typingResultEncoder.contramap[TypedClass](identity)

  case class Parameter(name: String, refClazz: TypingResult)

  object MethodInfo {
    def apply(parameters: List[Parameter],
              refClazz: TypingResult,
              name: String,
              description: Option[String],
              varArgs: Boolean): StaticMethodInfo  =
      if (varArgs && parameters.nonEmpty) {
        val (noVarArgParameters, varArgParameter) = parameters.splitAt(parameters.length - 1)
        VarArgsMethodInfo(noVarArgParameters, varArgParameter.head, refClazz, name, description)
      } else {
        SimpleMethodInfo(parameters, refClazz, name, description)
      }
  }

  sealed trait MethodInfo {
    def apply(arguments: List[TypingResult]): ValidatedNel[ExpressionParseError, TypingResult]

    def name: String

    def interfaceParameters: List[Parameter]

    def interfaceResult: TypingResult

    def description: Option[String]

    def varArgs: Boolean

    def asProperty: Option[TypingResult] = apply(List()).toOption
  }

  sealed trait StaticMethodInfo extends MethodInfo {
    def parameters: List[Parameter]

    def refClazz: TypingResult

    override def interfaceParameters: List[Parameter] = parameters

    override def interfaceResult: TypingResult = refClazz

    protected def checkNoVarArguments(arguments: List[TypingResult], parameters: List[Parameter]): Boolean =
      arguments.length == parameters.length &&
        arguments.zip(parameters).forall{ case(arg, param) => arg.canBeSubclassOf(param.refClazz)}
  }

  case class SimpleMethodInfo(parameters: List[Parameter],
                              refClazz: TypingResult,
                              name: String,
                              description: Option[String])
    extends StaticMethodInfo {
    override def interfaceParameters: List[Parameter] = parameters

    override def apply(arguments: List[TypingResult]): ValidatedNel[ExpressionParseError, TypingResult] = {
      if (checkNoVarArguments(arguments, parameters)) refClazz.validNel
      else NoVarArgumentTypeError(parameters.map(_.refClazz), arguments, name).invalidNel
    }

    override def varArgs: Boolean = false
  }

  case class VarArgsMethodInfo(noVarParameters: List[Parameter],
                               varParameter: Parameter,
                               refClazz: TypingResult,
                               name: String,
                               description: Option[String])
    extends StaticMethodInfo {
    private def checkArgumentsLength(arguments: List[TypingResult]): Boolean =
      arguments.length >= noVarParameters.length

    private def checkVarArguments(varArguments: List[TypingResult]): Boolean =
      varArguments.forall(_.canBeSubclassOf(varParameter.refClazz))

    private def checkArguments(arguments: List[TypingResult]): Boolean = {
      val (noVarArguments, varArguments) = arguments.splitAt(noVarParameters.length)
      checkNoVarArguments(noVarArguments, noVarParameters) && checkVarArguments(varArguments)
    }

    override def apply(arguments: List[TypingResult]): ValidatedNel[ExpressionParseError, TypingResult] = {
      if (checkArgumentsLength(arguments) && checkArguments(arguments)) refClazz.validNel
      else VarArgumentTypeError(noVarParameters.map(_.refClazz), varParameter.refClazz, arguments, name).invalidNel
    }

    override def parameters: List[Parameter] = noVarParameters :+ varParameter

    override def varArgs: Boolean = true

  }

  case class FunctionalMethodInfo(typeFunction: List[TypingResult] => ValidatedNel[ExpressionParseError, TypingResult],
                                  name: String,
                                  description: Option[String],
                                  varArgs: Boolean,
                                  interfaceParameters: List[Parameter],
                                  interfaceResult: TypingResult)
    extends MethodInfo {
    override def apply(arguments: List[TypingResult]): ValidatedNel[ExpressionParseError, TypingResult] =
      typeFunction(arguments)
  }

  object FunctionalMethodInfo {
    private def toParseErrorValidation[T](v: ValidatedNel[String, T]): ValidatedNel[ExpressionParseError, T] =
      v.leftMap(_.map(GenericFunctionError))

    def fromStringErrorTypeFunction(typeFunction: List[TypingResult] => ValidatedNel[String, TypingResult],
                                    name: String,
                                    description: Option[String] = None,
                                    varArgs: Boolean = false,
                                    interfaceParameters: List[Parameter] = Nil,
                                    interfaceResult: TypingResult = Unknown): FunctionalMethodInfo =
      FunctionalMethodInfo(x => toParseErrorValidation(typeFunction(x)), name, description, varArgs, interfaceParameters, interfaceResult)
  }

  case class ClazzDefinition(clazzName: TypedClass,
                                      methods: Map[String, List[MethodInfo]],
                                      staticMethods: Map[String, List[MethodInfo]]) {
    def getPropertyOrFieldType(methodName: String): Option[TypingResult] = {
      def filterMethods(candidates: Map[String, List[MethodInfo]]): List[TypingResult] =
        candidates.get(methodName).toList.flatMap(_.map(_.asProperty)).collect{ case Some(x) => x }
      val filteredMethods = filterMethods(methods)
      val filteredStaticMethods = filterMethods(staticMethods)
      val filtered = filteredMethods ++ filteredStaticMethods
      filtered match {
        case Nil => None
        case nonEmpty => Some(Typed(nonEmpty.toSet))
      }
    }
  }
}
