package pl.touk.nussknacker.engine.util.classes

import pl.touk.nussknacker.engine.api.util.ReflectUtils

object Extensions {

  implicit class ClassExtensions(clazz: Class[_]) {

    def isChildOf(targetClazz: Class[_]): Boolean =
      clazz != targetClazz &&
        targetClazz.isAssignableFrom(clazz)

    def isNotFromNuUtilPackage(): Boolean = {
      val name = clazz.getName
      !(name.contains("nussknacker") && name.contains("util"))
    }

  }

  implicit class ClassesExtensions(set: Set[Class[_]]) {

    def classesBySimpleNames(): Map[String, Class[_]] = {
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
