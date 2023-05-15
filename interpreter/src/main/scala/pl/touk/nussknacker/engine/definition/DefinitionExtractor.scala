package pl.touk.nussknacker.engine.definition

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.MethodToInvoke
import pl.touk.nussknacker.engine.api.component.SingleComponentConfig
import pl.touk.nussknacker.engine.api.context.transformation._
import pl.touk.nussknacker.engine.api.definition.{OutputVariableNameDependency, Parameter, TypedNodeDependency, WithExplicitTypesToExtract}
import pl.touk.nussknacker.engine.api.process.{ClassExtractionSettings, WithCategories}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor._
import pl.touk.nussknacker.engine.definition.MethodDefinitionExtractor.MethodDefinition
import pl.touk.nussknacker.engine.types.TypesInformationExtractor

import java.lang.reflect.Method
import scala.runtime.BoxedUnit

class DefinitionExtractor[T](methodDefinitionExtractor: MethodDefinitionExtractor[T]) {

  def extract(objWithCategories: WithCategories[T], mergedComponentConfig: SingleComponentConfig): ObjectWithMethodDef = {
    val obj = objWithCategories.value

    def fromMethodDefinition(methodDef: MethodDefinition): StandardObjectWithMethodDef = {
      // TODO: Use ContextTransformation API to check if custom node is adding some output variable
      def notReturnAnything(typ: TypingResult) = Set[TypingResult](Typed[Void], Typed[Unit], Typed[BoxedUnit]).contains(typ)
      val objectDefinition = ObjectDefinition(
        methodDef.definedParameters,
        Option(methodDef.returnType).filterNot(notReturnAnything),
        objWithCategories.categories,
        mergedComponentConfig)
      val implementationInvoker = new MethodBasedComponentImplementationInvoker(obj, methodDef)
      StandardObjectWithMethodDef(implementationInvoker, obj, objectDefinition, methodDef.runtimeClass)
    }

    (obj match {
      case e: GenericNodeTransformation[_] =>
        Right(GenericNodeTransformationMethodDef(e, objWithCategories.categories, mergedComponentConfig))
      case _ =>
        methodDefinitionExtractor.extractMethodDefinition(obj, findMethodToInvoke(obj), mergedComponentConfig).map(fromMethodDefinition)
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

  case class ObjectWithType(obj: Any, typ: TypingResult)

  // TODO: rename to ComponentDefinitionWithImplementation
  sealed trait ObjectWithMethodDef {

    // TODO: It should be exposed only for components - not for global variables
    def implementationInvoker: ComponentImplementationInvoker

    // For purpose of transforming (e.g.) stubbing of the implementation
    def withImplementationInvoker(implementationInvoker: ComponentImplementationInvoker): ObjectWithMethodDef

    def obj: Any

    // TODO: it should be available only for StandardObjectWithMethodDef
    def returnType: Option[TypingResult]

    protected[definition] def categories: Option[List[String]]

    def availableForCategory(category: String): Boolean = categories.isEmpty || categories.exists(_.contains(category))

    def componentConfig: SingleComponentConfig

  }

  trait ComponentImplementationInvoker extends Serializable {

    def invokeMethod(params: Map[String, Any],
                     outputVariableNameOpt: Option[String],
                     additional: Seq[AnyRef]): Any

  }

  object ComponentImplementationInvoker {

    val nullImplementationInvoker: ComponentImplementationInvoker = new ComponentImplementationInvoker {
      override def invokeMethod(params: Map[String, Any],
                                outputVariableNameOpt: Option[String],
                                additional: Seq[AnyRef]): Any = null
    }

  }


  case class GenericNodeTransformationMethodDef(override val implementationInvoker: ComponentImplementationInvoker,
                                                obj: GenericNodeTransformation[_],
                                                override protected[definition] val categories: Option[List[String]],
                                                override val componentConfig: SingleComponentConfig) extends ObjectWithMethodDef {
    override def withImplementationInvoker(implementationInvoker: ComponentImplementationInvoker): ObjectWithMethodDef =
      copy(implementationInvoker = implementationInvoker)

    def returnType: Option[TypingResult] = if (obj.nodeDependencies.contains(OutputVariableNameDependency)) Some(Unknown) else None

  }

  object GenericNodeTransformationMethodDef {

    def apply(obj: GenericNodeTransformation[_],
              categories: Option[List[String]],
              componentConfig: SingleComponentConfig): GenericNodeTransformationMethodDef = {
      val implementationInvoker = new ComponentImplementationInvoker {
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
      }
      new GenericNodeTransformationMethodDef(implementationInvoker, obj, categories, componentConfig)
    }

  }


  case class FinalStateValue(value: Option[Any])

  // TOOD: rename to MethodBasedComponentWithImplementation
  case class StandardObjectWithMethodDef(implementationInvoker: ComponentImplementationInvoker,
                                         obj: Any,
                                         objectDefinition: ObjectDefinition,
                                         // TODO: it should be removed - instead implementationInvoker should be transformed
                                         runtimeClass: Class[_]) extends ObjectWithMethodDef {
    override def withImplementationInvoker(implementationInvoker: ComponentImplementationInvoker): ObjectWithMethodDef =
      copy(implementationInvoker = implementationInvoker)

    def parameters: List[Parameter] = objectDefinition.parameters

    override def returnType: Option[TypingResult] = objectDefinition.returnType

    override protected[definition] def categories: Option[List[String]] = objectDefinition.categories

    override def componentConfig: SingleComponentConfig = objectDefinition.componentConfig

  }

  private[definition] class MethodBasedComponentImplementationInvoker(obj: Any, private[definition] val methodDef: MethodDefinition)
    extends ComponentImplementationInvoker with LazyLogging {

    override def invokeMethod(params: Map[String, Any], outputVariableNameOpt: Option[String], additional: Seq[AnyRef]): Any = {
      methodDef.invoke(obj, params, outputVariableNameOpt, additional)
    }
  }

  // TODO: rename to ComponentStaticDefinition
  case class ObjectDefinition(parameters: List[Parameter],
                              returnType: Option[TypingResult],
                              categories: Option[List[String]],
                              componentConfig: SingleComponentConfig)

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

      def typesFromParameters(obj: ObjectWithMethodDef): List[TypingResult] = {
        obj match {
          case static: StandardObjectWithMethodDef => static.parameters.flatMap(typesFromParameter)
          // WithExplicitTypesToExtract trait should be used in that case
          case _: GenericNodeTransformationMethodDef => List.empty
        }
      }

      def explicitTypes(obj: ObjectWithMethodDef): List[TypingResult] = {
        obj.obj match {
          case explicit: WithExplicitTypesToExtract => explicit.typesToExtract
          case _ => Nil
        }
      }

      obj.returnType.toList ::: typesFromParameters(obj) ::: explicitTypes(obj)
    }
  }

}
