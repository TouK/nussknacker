package pl.touk.nussknacker.engine.extension

import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.Documentation
import pl.touk.nussknacker.engine.api.generics.{GenericFunctionTypingError, MethodTypeInfo, Parameter}
import pl.touk.nussknacker.engine.api.process.ClassExtractionSettings
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.definition.clazz.ClassDefinitionExtractor.{MethodDefinitionsExtension, MethodExtensions}
import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinition, ClassDefinitionExtractor, ClassDefinitionSet, FunctionalMethodDefinition, MethodDefinition}
import pl.touk.nussknacker.engine.util.Implicits.{RichScalaMap, RichTupleList}

import java.lang.reflect.{Method, Modifier}

class ClassDefinitionSetWithExtensionMethods(set: ClassDefinitionSet, settings: ClassExtractionSettings) {
  private val extractor: ClassDefinitionExtractor = new ClassDefinitionExtractor(settings)
  private val stringClass: Class[String] = classOf[String]
  private val stringTyping: TypingResult = Typed.genericTypeClass(stringClass, Nil)
  // We cannot have a class as the key because of `visibleMembersPredicate` e.g. Cast.castTo may be accessible for many classes.
  private val extensionMethodsMap: Map[Method, Map[String, List[MethodDefinition]]] =
    extractExtensionMethods() ++ createStaticallyDefinedMethodsMap(set)

  val value = new ClassDefinitionSet(
    set
      .classDefinitionsMap
      .map {
        case (clazz, definition) => clazz -> enrichWithExtensionMethods(clazz, definition)
      }
      .toMap // .toMap is needed by scala 2.12
  )

  private def enrichWithExtensionMethods(clazz: Class[_], classDefinition: ClassDefinition): ClassDefinition =
    findMethodsForClass(clazz) match {
      case ext if ext.isEmpty => classDefinition
      case ext                => classDefinition.copy(methods = classDefinition.methods ++ ext)
    }

  private def findMethodsForClass(clazz: Class[_]): Map[String, List[MethodDefinition]] = {
    val membersPredicate = settings.visibleMembersPredicate(clazz)
    extensionMethodsMap
      .filterKeysNow(membersPredicate.shouldBeVisible)
      .flatMap(_._2)
  }

  private def extractExtensionMethods(): Map[Method, Map[String, List[MethodDefinition]]] = {
    ExtensionMethods.registry
      .filter(filterAnnotatedClass)
      .flatMap(clazz => extractMethodsWithDefinitions(clazz))
      .groupBy(_._1)
      .mapValuesNow(definitionsSet => filterByVisibilityOfParams(definitionsSet))
  }

  private def filterAnnotatedClass(clazz: Class[_]): Boolean =
    Option(clazz.getAnnotation(classOf[SkipAutoDiscovery])).isEmpty

  private def extractMethodsWithDefinitions(clazz: Class[_]): List[(Method, List[(String, MethodDefinition)])] =
    clazz.getMethods.toList
      .filter(m => !Modifier.isStatic(m.getModifiers))
      .filter(_.javaVersionOfVarArgMethod().isEmpty)
      .map(m => m -> extractor.extractMethod(clazz, m))

  private def filterByVisibilityOfParams(methodsWithDefinitions: Set[(Method, List[(String, MethodDefinition)])]
                                        ): Map[String, List[MethodDefinition]] =
    methodsWithDefinitions
      .flatMap(_._2)
      .toList
      .toGroupedMap
      .filterHiddenParameterAndReturnType(settings)

  private def createStaticallyDefinedMethodsMap(set: ClassDefinitionSet
                                               ): Map[Method, Map[String, List[MethodDefinition]]] = {
    val allowedClasses  = AllowedClasses(set)
    val castClass       = classOf[Cast]
    val canCastToMethod = castClass.getDeclaredMethod("canCastTo", stringClass)
    val castToMethod    = castClass.getDeclaredMethod("castTo", stringClass)
    Map(
      canCastToMethod -> Map(
        canCastToMethod.getName -> List(
          castFunctionalMethodDefinition(canCastToMethod, CastTyping.canCastToTyping(allowedClasses))
        )
      ),
      castToMethod -> Map(
        castToMethod.getName -> List(
          castFunctionalMethodDefinition(castToMethod, CastTyping.castToTyping(allowedClasses))
        )
      )
    )
  }

  private def castFunctionalMethodDefinition(method: Method,
                                             typeFunction: (TypingResult, List[TypingResult]) => ValidatedNel[GenericFunctionTypingError, TypingResult]
                                            ): FunctionalMethodDefinition =
    FunctionalMethodDefinition(
      typeFunction = typeFunction,
      signature = MethodTypeInfo(
        noVarArgs = List(
          Parameter("clazzType", stringTyping)
        ),
        varArg = None,
        result = Unknown
      ),
      name = method.getName,
      description = getDocumentationAnnotationValue(method)
    )

  private def getDocumentationAnnotationValue(method: Method): Option[String] =
    Option(method.getAnnotation(classOf[Documentation])).map(_.description())
}

object ClassDefinitionSetWithExtensionMethods {

  def apply(modelData: ModelData): ClassDefinitionSetWithExtensionMethods =
    new ClassDefinitionSetWithExtensionMethods(
      modelData.modelDefinitionWithClasses.classDefinitions,
      modelData.modelDefinition.settings
    )
}
