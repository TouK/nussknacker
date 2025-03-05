package pl.touk.nussknacker.engine.util

import com.typesafe.scalalogging.LazyLogging

import java.lang.reflect.InvocationTargetException

final class ReflectiveMethodInvoker[Result](classLoader: ClassLoader, className: String, methodName: String)
    extends LazyLogging {

  import scala.reflect.runtime.{universe => ru}

  private val invoker: ru.MethodMirror = {
    try {
      val m         = ru.runtimeMirror(classLoader)
      val module    = m.staticModule(className)
      val im        = m.reflectModule(module)
      val method    = im.symbol.info.decl(ru.TermName(methodName)).asMethod
      val objMirror = m.reflect(im.instance)
      val r         = objMirror.reflectMethod(method)
      r
    } catch {
      case e: ScalaReflectionException =>
        logger.error(s"Error while invoking method $className.$methodName with classloader: $classLoader", e)
        throw e
    }
  }

  // we have to use context loader, as in UI we have don't have e.g. nussknacker-process or user model on classpath...
  def invokeStaticMethod(args: Any*): Result = ThreadUtils.withThisAsContextClassLoader(classLoader) {
    try {
      invoker(args: _*).asInstanceOf[Result]
    } catch {
      case e: InvocationTargetException => throw e.getTargetException
    }
  }

}
