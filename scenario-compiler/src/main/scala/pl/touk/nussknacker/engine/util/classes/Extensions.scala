package pl.touk.nussknacker.engine.util.classes

import pl.touk.nussknacker.engine.api.util.ReflectUtils
import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinition, ClassDefinitionSet}
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

object Extensions {

  implicit class ClassExtensions(private val clazz: Class[_]) extends AnyVal {

    def isChildOf(targetClazz: Class[_]): Boolean =
      clazz != targetClazz &&
        targetClazz.isAssignableFrom(clazz)

    def isAOrChildOf(targetClazz: Class[_]): Boolean =
      targetClazz.isAssignableFrom(clazz)

    def isNotFromNuUtilPackage(): Boolean = {
      val name = clazz.getName
      !(name.contains("nussknacker") && name.contains("util"))
    }

    def equalsScalaClassNameIgnoringCase(clazzName: String): Boolean =
      clazz.getName.equalsIgnoreCase(clazzName) ||
        ReflectUtils.simpleNameWithoutSuffix(clazz).equalsIgnoreCase(clazzName)

    def findAllowedClassesForCastParameter(set: ClassDefinitionSet): Map[Class[_], ClassDefinition] =
      set.classDefinitionsMap
        .filterKeysNow(targetClazz => targetClazz.isChildOf(clazz) && targetClazz.isNotFromNuUtilPackage())

  }

  implicit class ClassesExtensions(private val set: Set[Class[_]]) extends AnyVal {

    def classesBySimpleNamesRegardingClashes(): Map[String, Class[_]] = {
      val nonUniqueClassNames = nonUniqueNames()
      set
        .map {
          case c if nonUniqueClassNames.contains(ReflectUtils.simpleNameWithoutSuffix(c)) => c.getName -> c
          case c => ReflectUtils.simpleNameWithoutSuffix(c) -> c
        }
        .toMap[String, Class[_]]
    }

    private def nonUniqueNames(): Set[String] = {
      val simpleNames = set.toList.map(c => ReflectUtils.simpleNameWithoutSuffix(c))
      simpleNames.diff(simpleNames.distinct).toSet
    }

  }

}
