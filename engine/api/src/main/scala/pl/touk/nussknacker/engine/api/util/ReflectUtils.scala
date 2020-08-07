package pl.touk.nussknacker.engine.api.util

import java.lang.reflect.InvocationTargetException

object ReflectUtils {

  def fixedClassSimpleNameWithoutParentModule(clazz: Class[_]): String = {
    // fix for https://issues.scala-lang.org/browse/SI-8110
    clazz.getName
      .replaceFirst("^.*\\.", "") // package
      .replaceFirst("^.*\\$([^$])", "$1") // parent object
      .replaceFirst("\\$$", "") // module indicator
  }

}
