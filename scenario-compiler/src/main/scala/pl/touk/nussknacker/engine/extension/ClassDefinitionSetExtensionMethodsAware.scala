package pl.touk.nussknacker.engine.extension

import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.Documentation
import pl.touk.nussknacker.engine.api.generics.{GenericFunctionTypingError, MethodTypeInfo, Parameter}
import pl.touk.nussknacker.engine.api.process.ClassExtractionSettings
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.definition.clazz.ClassDefinitionExtractor.{
  MethodDefinitionsExtension,
  MethodExtensions
}
import pl.touk.nussknacker.engine.definition.clazz.{
  ClassDefinition,
  ClassDefinitionExtractor,
  ClassDefinitionSet,
  FunctionalMethodDefinition,
  MethodDefinition
}
import pl.touk.nussknacker.engine.extension.ClassDefinitionSetExtensionMethodsAware.{
  createStaticallyDefinedMethodsMap,
  extractExtensionMethods
}
import pl.touk.nussknacker.engine.util.Implicits.{RichScalaMap, RichTupleList}

import java.lang.reflect.{Method, Modifier}

final case class ClassDefinitionSetExtensionMethodsAware(set: ClassDefinitionSet, settings: ClassExtractionSettings) {
  private lazy val extractor: ClassDefinitionExtractor = new ClassDefinitionExtractor(settings)
  // We cannot have a class as the key because of `visibleMembersPredicate` e.g. Cast.castTo may be accessible for many classes.
  private lazy val extensionMethodsMap: Map[Method, Map[String, List[MethodDefinition]]] =
    extractExtensionMethods(settings, extractor) ++ createStaticallyDefinedMethodsMap(set)

  lazy val unknown: Option[ClassDefinition] =
    getWithExtensionMethods(classOf[Any])

  def get(clazz: Class[_]): Option[ClassDefinition] =
    getWithExtensionMethods(clazz)

  private def getWithExtensionMethods(clazz: Class[_]): Option[ClassDefinition] =
    set
      .get(clazz)
      .map(classDefinition =>
        findMethodsForClass(clazz) match {
          case ext if ext.isEmpty => classDefinition
          case ext                => classDefinition.copy(methods = classDefinition.methods ++ ext)
        }
      )

  private def findMethodsForClass(clazz: Class[_]): Map[String, List[MethodDefinition]] = {
    val membersPredicate = settings.visibleMembersPredicate(clazz)
    extensionMethodsMap
      .filterKeysNow(membersPredicate.shouldBeVisible)
      .flatMap(_._2)
  }

}

object ClassDefinitionSetExtensionMethodsAware {
  private val stringClass: Class[String] = classOf[String]
  private val stringTyping: TypingResult = Typed.genericTypeClass(stringClass, Nil)

  def apply(modelData: ModelData): ClassDefinitionSetExtensionMethodsAware =
    new ClassDefinitionSetExtensionMethodsAware(
      modelData.modelDefinitionWithClasses.classDefinitions,
      modelData.modelDefinition.settings
    )

  private def extractExtensionMethods(
      settings: ClassExtractionSettings,
      extractor: ClassDefinitionExtractor
  ): Map[Method, Map[String, List[MethodDefinition]]] = {
    ExtensionMethods.registry
      .filter(filterAnnotatedClass)
      .flatMap(clazz => extractMethodsWithDefinitions(extractor, clazz))
      .groupBy(_._1)
      .mapValuesNow(definitionsSet => filterByVisibilityOfParams(settings, definitionsSet))
  }

  private def filterAnnotatedClass(clazz: Class[_]): Boolean =
    Option(clazz.getAnnotation(classOf[SkipAutoDiscovery])).isEmpty

  private def extractMethodsWithDefinitions(
      extractor: ClassDefinitionExtractor,
      clazz: Class[_]
  ): List[(Method, List[(String, MethodDefinition)])] =
    clazz.getMethods.toList
      .filter(m => !Modifier.isStatic(m.getModifiers))
      .filter(_.javaVersionOfVarArgMethod().isEmpty)
      .map(m => m -> extractor.extractMethod(clazz, m))

  private def filterByVisibilityOfParams(
      settings: ClassExtractionSettings,
      methodsWithDefinitions: Set[(Method, List[(String, MethodDefinition)])]
  ): Map[String, List[MethodDefinition]] =
    methodsWithDefinitions
      .flatMap(_._2)
      .toList
      .toGroupedMap
      .filterHiddenParameterAndReturnType(settings)

  private def createStaticallyDefinedMethodsMap(
      set: ClassDefinitionSet
  ): Map[Method, Map[String, List[MethodDefinition]]] = {
    val allowedClasses  = set.classDefinitionsMap.map(e => e._1.getName -> e._2.clazzName)
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

  private def castFunctionalMethodDefinition(
      method: Method,
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
