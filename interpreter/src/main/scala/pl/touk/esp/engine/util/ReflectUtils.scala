package pl.touk.esp.engine.util

object ReflectUtils {

  def fixedClassSimpleNameWithoutParentModule(ref: AnyRef): String = {
    // fix for https://issues.scala-lang.org/browse/SI-8110
    ref.getClass.getName
      .replaceFirst("^.*\\.", "") // package
      .replaceFirst("^.*\\$([^$])", "$1") // parent object
      .replaceFirst("\\$$", "") // module indicator
  }

  def fixedClassSimpleName(ref: AnyRef): String = {
    // fix for https://issues.scala-lang.org/browse/SI-8110
    ref.getClass.getName
      .replaceFirst("^.*\\.", "") // package
      .replaceFirst("\\$$", "") // module indicator
  }

}
