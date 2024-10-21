package pl.touk.nussknacker.engine.util.classes

object Extensions {

  implicit class ClassExtensions(private val clazz: Class[_]) extends AnyVal {

    def isAOrChildOf(targetClazz: Class[_]): Boolean =
      targetClazz.isAssignableFrom(clazz)

  }

}
