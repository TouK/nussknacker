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

  abstract class StaticMethodRunner(classLoader: ClassLoader, className: String, methodName: String) {

    import scala.reflect.runtime.{universe => ru}

    private val invoker: ru.MethodMirror = {
      val m = ru.runtimeMirror(classLoader)
      val module = m.staticModule(className)
      val im = m.reflectModule(module)
      val method = im.symbol.info.decl(ru.TermName(methodName)).asMethod
      val objMirror = m.reflect(im.instance)
      objMirror.reflectMethod(method)
    }

    //we have to use context loader, as in UI we have don't have e.g. nussknacker-process or user model on classpath...
    def tryToInvoke(args: Any*): Any = ThreadUtils.withThisAsContextClassLoader(classLoader) {
      try {
        invoker(args: _*)
      } catch {
        case e: InvocationTargetException => throw e.getTargetException
      }
    }

  }

}
