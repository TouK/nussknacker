package pl.touk.nussknacker.engine.management

import java.io.File
import java.lang.reflect.InvocationTargetException

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.deployment.test.{TestData, TestResults}
import pl.touk.nussknacker.engine.util.ThreadUtils
import pl.touk.nussknacker.engine.util.loader.JarClassLoader

import scala.reflect.runtime.{universe => ru}

object FlinkProcessTestRunner {
  def apply(config: Config, jarFile: File) = {
    new FlinkProcessTestRunner(config, JarClassLoader(jarFile))
  }
}

class FlinkProcessTestRunner(config: Config, jarClassLoader: JarClassLoader) {
  private val invoker: ru.MethodMirror = {
    val m = ru.runtimeMirror(jarClassLoader.classLoader)
    val module = m.staticModule("pl.touk.nussknacker.engine.process.runner.FlinkTestMain")
    val im = m.reflectModule(module)
    val method = im.symbol.info.decl(ru.TermName("run")).asMethod
    val objMirror = m.reflect(im.instance)
    objMirror.reflectMethod(method)
  }

  def test(processId: String, json: String, testData: TestData): TestResults = {
    //we have to use context loader, as in UI we have don't have nussknacker-process on classpath...
    ThreadUtils.withThisAsContextClassLoader(jarClassLoader.classLoader) {
      tryToInvoke(testData, json).asInstanceOf[TestResults]
    }
  }

  def tryToInvoke(testData: TestData, json: String): Any = try {
    invoker(json, config, testData, List(jarClassLoader.jarUrl))
  } catch {
    case e: InvocationTargetException => throw e.getTargetException
  }
}
