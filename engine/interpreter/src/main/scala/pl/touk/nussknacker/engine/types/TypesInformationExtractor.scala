package pl.touk.nussknacker.engine.types

import org.apache.commons.lang3.ClassUtils
import pl.touk.nussknacker.engine.api.process.ClassExtractionSettings
import pl.touk.nussknacker.engine.api.typed.ClazzRef
import pl.touk.nussknacker.engine.definition.TypeInfos.ClazzDefinition
import pl.touk.nussknacker.engine.types.EspTypeUtils.clazzDefinition

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
    classOf[String]
  ) ++ primitiveTypes ++ primitiveTypes.map(ClassUtils.primitiveToWrapper)).map(ClazzRef(_))

  private val primitiveTypesSimpleNames = primitiveTypes.map(_.getName).toSet

  private val baseClazzPackagePrefix = Set("java", "scala")

  private val blacklistedClazzPackagePrefix = Set(
    "scala.collection", "scala.Function", "scala.xml",
    "javax.xml", "java.util",
    "cats", "argonaut", "dispatch",
    "org.apache.flink.api.common.typeinfo.TypeInformation"
  )

  def clazzAndItsChildrenDefinition(clazzes: Iterable[ClazzRef])
                                   (implicit settings: ClassExtractionSettings): List[ClazzDefinition] = {
    (clazzes ++ mandatoryClasses).flatMap(clazzAndItsChildrenDefinition).toList.distinct
  }

  //TODO: make it work for cases like: def list: Future[List[Value]]
  //also better handling of recursive data
  private def clazzAndItsChildrenDefinition(clazzRef: ClazzRef)
                                   (implicit settings: ClassExtractionSettings): List[ClazzDefinition] = {
    val clazz = clazzRef.clazz
    val result = if (clazz.isPrimitive || baseClazzPackagePrefix.exists(clazz.getName.startsWith)) {
      List(clazzDefinition(clazz))
    } else {
      val mainClazzDefinition = clazzDefinition(clazz)
      val recursiveClazzes = mainClazzDefinition.methods.values.toList
        .filter(m => !primitiveTypesSimpleNames.contains(m.refClazzName) && m.refClazzName != clazz.getName)
        .filter(m => !blacklistedClazzPackagePrefix.exists(m.refClazzName.startsWith))
        .filter(m => !m.refClazzName.startsWith("["))
        .map(_.refClazz).distinct
        .flatMap(m => clazzAndItsChildrenDefinition(m))
      mainClazzDefinition :: recursiveClazzes
    }
    result.distinct
  }

}
