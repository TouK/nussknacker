package pl.touk.nussknacker.engine.api.util

object ReflectUtils {

  def simpleNameWithoutSuffix(clazz: Class[_]): String = {
    clazz.getSimpleName match {
      case ""   => "(anonymous class)"
      case name => name.stripSuffix("$")
    }
  }

}
