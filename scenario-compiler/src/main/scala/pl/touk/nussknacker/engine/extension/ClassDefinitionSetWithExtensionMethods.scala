package pl.touk.nussknacker.engine.extension

import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.Documentation
import pl.touk.nussknacker.engine.api.generics.{GenericFunctionTypingError, MethodTypeInfo, Parameter}
import pl.touk.nussknacker.engine.api.process.ClassExtractionSettings
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.definition.clazz.{
  ClassDefinition,
  ClassDefinitionSet,
  FunctionalMethodDefinition,
  MethodDefinition
}
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

import java.lang.reflect.Method

class ClassDefinitionSetWithExtensionMethods(set: ClassDefinitionSet, settings: ClassExtractionSettings) {
  private val stringClass: Class[String] = classOf[String]
  private val stringTyping: TypingResult = Typed.genericTypeClass(stringClass, Nil)
  // We cannot have a class as the key because of `visibleMembersPredicate` e.g. Cast.castTo may be accessible for many classes.
  // We only added statically defined methods because we needed a typingFunction which has access to some state.
  // This functionality can be easily extended using dynamic methods discovery what you only need to do is:
  // - Take all extension classes from class: ExtensionMethods, and then filter out those classes that are statically defined e.g. using annotation.
  // - Filter out static and scala varargs methods (Method.isStatic and ClassDefinitionExtractor.javaVersionOfVarArgMethod)
  // - Then invoke ClassDefinitionExtractor.extractMethod (you can easily build ClassDefinitionExtractor using settings).
  // - After this we should filter out hidden members using ClassDefinitionExtractor.filterHiddenParameterAndReturnType.
  // - And calculated definitions should be combined with static definitions.
  private val extensionMethodsMap: Map[Method, Map[String, List[MethodDefinition]]] =
    createStaticallyDefinedMethodsMap(set)

  val value = new ClassDefinitionSet(
    set.classDefinitionsMap.map { case (clazz, definition) =>
      clazz -> enrichWithExtensionMethods(clazz, definition)
    }.toMap // .toMap is needed by scala 2.12
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

  private def createStaticallyDefinedMethodsMap(
      set: ClassDefinitionSet
  ): Map[Method, Map[String, List[MethodDefinition]]] = {
    val allowedClasses  = AllowedCastParametersClasses(set)
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
          Parameter("className", stringTyping)
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
