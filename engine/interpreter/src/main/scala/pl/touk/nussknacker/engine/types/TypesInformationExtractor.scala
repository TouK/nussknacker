package pl.touk.nussknacker.engine.types

import org.apache.commons.lang3.ClassUtils
import pl.touk.nussknacker.engine.api.process.ClassExtractionSettings
import pl.touk.nussknacker.engine.api.typed.ClazzRef
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypedObjectTypingResult, TypedUnion, TypingResult, Unknown}
import pl.touk.nussknacker.engine.definition.TypeInfos.{ClazzDefinition, MethodInfo}
import pl.touk.nussknacker.engine.types.EspTypeUtils.clazzDefinition
import pl.touk.nussknacker.engine.variables.MetaVariables

object TypesInformationExtractor {

  //TODO: this should be somewhere in utils??
  private val primitiveTypes : List[Class[_]] = List(
    Void.TYPE,
    java.lang.Boolean.TYPE,
    java.lang.Integer.TYPE,
    java.lang.Long.TYPE,
    java.lang.Float.TYPE,
    java.lang.Double.TYPE,
    java.lang.Byte.TYPE,
    java.lang.Short.TYPE,
    java.lang.Character.TYPE
  )

  //they can always appear...
  //TODO: what else should be here?
  private val mandatoryClasses = (Set(
    classOf[java.util.List[_]],
    classOf[java.util.Map[_, _]],
    classOf[java.math.BigDecimal],
    classOf[Number],
    classOf[String],
    classOf[MetaVariables]
  ) ++ primitiveTypes ++ primitiveTypes.map(ClassUtils.primitiveToWrapper)).map(ClazzRef(_))

  private val primitiveTypesSimpleNames = primitiveTypes.map(_.getName).toSet

  private val baseClazzPackagePrefix = Set("java", "scala")

  private val blacklistedClazzPackagePrefix = Set(
    "scala.collection", "scala.Function", "scala.xml",
    "javax.xml", "java.util",
    "cats", "argonaut", "dispatch", "io.circe",
    "org.apache.flink.api.common.typeinfo.TypeInformation"
  )

  def clazzAndItsChildrenDefinition(clazzes: Iterable[TypingResult])
                                   (implicit settings: ClassExtractionSettings): Set[ClazzDefinition] = {
    (clazzes.flatMap(clazzRefsFromTypingResult) ++ mandatoryClasses).flatMap(clazzAndItsChildrenDefinition(_, Set())).toSet
  }

  private def clazzRefsFromTypingResult(typingResult: TypingResult): Set[ClazzRef] = typingResult match {
    case tc: TypedClass =>
      clazzRefsFromTypedClass(tc)
    case TypedUnion(set) =>
      set.flatMap(clazzRefsFromTypingResult)
    case TypedObjectTypingResult(fields, clazz) =>
      clazzRefsFromTypedClass(clazz) ++ fields.values.flatMap(clazzRefsFromTypingResult)
    case Unknown =>
      Set()
  }

  private def clazzRefsFromTypedClass(typedClass: TypedClass): Set[ClazzRef]
    = typedClass.params.flatMap(clazzRefsFromTypingResult).toSet + ClazzRef(typedClass.klass)

  private def clazzAndItsChildrenDefinition(clazzRef: ClazzRef, currentSet: Set[ClazzRef])
                                   (implicit settings: ClassExtractionSettings): Set[ClazzDefinition] = {
    val clazz = clazzRef.clazz
    val newSet = currentSet + clazzRef

    val ignoreClass = primitiveTypesSimpleNames.contains(clazzRef.refClazzName) || blacklistedClazzPackagePrefix.exists(clazzRef.refClazzName.startsWith)
    val ignoreMethods = clazz.isPrimitive || baseClazzPackagePrefix.exists(clazz.getName.startsWith)

    definitionsFromParameters(clazzRef, newSet) ++
      (if (ignoreClass || ignoreMethods) Set() else definitionsFromMethods(clazzRef, newSet)) ++
      (if (ignoreClass) Set() else Set(clazzDefinition(clazz)))
  }

  private def definitionsFromParameters(clazzRef: ClazzRef, currentSet: Set[ClazzRef])
                                       (implicit settings: ClassExtractionSettings): Set[ClazzDefinition] = {
    clazzRef.params.toSet.diff(currentSet)
      .flatMap((k: ClazzRef) => clazzAndItsChildrenDefinition(k, currentSet))
  }

  private def definitionsFromMethods(clazzRef: ClazzRef, currentSet: Set[ClazzRef])
                                     (implicit settings: ClassExtractionSettings): Set[ClazzDefinition] = {

    clazzDefinition(clazzRef.clazz).methods.values.toSet
            .flatMap((kl: MethodInfo) => kl.refClazz +: kl.parameters.map(_.refClazz))
            .diff(currentSet)
            .filterNot(m => m.refClazzName.startsWith("["))
            .flatMap(m => clazzAndItsChildrenDefinition(m, currentSet))

  }

}
