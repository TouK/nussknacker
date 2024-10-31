package pl.touk.nussknacker.engine.util.classes

import pl.touk.nussknacker.engine.api.util.ReflectUtils
import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinition, ClassDefinitionSet}
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

object Extensions {

  implicit class ClassExtensions(private val clazz: Class[_]) extends AnyVal {

    def isAOrChildOf(targetClazz: Class[_]): Boolean =
      targetClazz.isAssignableFrom(clazz)

    def isChildOf(targetClazz: Class[_]): Boolean =
      clazz != targetClazz &&
        targetClazz.isAssignableFrom(clazz)

    def isNotFromNuUtilPackage(): Boolean = {
      val name = clazz.getName
      !(name.contains("nussknacker") && name.contains("util"))
    }

    def equalsScalaClassNameIgnoringCase(clazzName: String): Boolean =
      clazz.getName.equalsIgnoreCase(clazzName) ||
        clazz.simpleName().equalsIgnoreCase(clazzName)

    def findAllowedClassesForCastParameter(set: ClassDefinitionSet): Map[Class[_], ClassDefinition] =
      set.classDefinitionsMap
        .filterKeysNow(targetClazz => targetClazz.isChildOf(clazz) && targetClazz.isNotFromNuUtilPackage())

    def simpleName(): String =
      ReflectUtils.simpleNameWithoutSuffix(clazz)

    def classByNameAndSimpleNameLowerCase(): List[(String, Class[_])] =
      List(
        clazz.getName.toLowerCase()      -> clazz,
        clazz.simpleName().toLowerCase() -> clazz
      )

  }

  implicit class ClassesExtensions(private val set: Set[Class[_]]) extends AnyVal {

    def classesBySimpleNamesRegardingClashes(): Map[String, Class[_]] = {
      val nonUniqueClassNames = nonUniqueNames()
      set
        .map {
          case c if nonUniqueClassNames.contains(c.simpleName()) => c.getName      -> c
          case c                                                 => c.simpleName() -> c
        }
        .toMap[String, Class[_]]
    }

    def classesByNamesAndSimpleNamesLowerCase(): Map[String, Class[_]] = set
      .flatMap(_.classByNameAndSimpleNameLowerCase())
      .toMap

    private def nonUniqueNames(): Set[String] = {
      val simpleNames = set.toList.map(c => c.simpleName())
      simpleNames.diff(simpleNames.distinct).toSet
    }

  }

}
