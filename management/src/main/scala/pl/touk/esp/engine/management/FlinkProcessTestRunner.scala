package pl.touk.esp.engine.management

import java.io.File
import java.net.{URL, URLClassLoader}

import com.typesafe.config.Config
import pl.touk.esp.engine.api.deployment.test.{TestData, TestResults}
import pl.touk.esp.engine.api.deployment.{GraphProcess, ProcessDeploymentData}
import pl.touk.esp.engine.util.ThreadUtils

import scala.reflect.runtime.{universe => ru}

object FlinkProcessTestRunner {
  def apply(config: Config, jarFile: File) = {
    new FlinkProcessTestRunner(config, List(jarFile.toURI.toURL))
  }
}

class FlinkProcessTestRunner(config: Config, jars: List[URL]) {

  val classLoader = new URLClassLoader(jars.toArray, getClass.getClassLoader)

  private val invoker: ru.MethodMirror = {
    val m = ru.runtimeMirror(classLoader)
    val module = m.staticModule("pl.touk.esp.engine.process.runner.FlinkTestMain")
    val im = m.reflectModule(module)
    val method = im.symbol.info.decl(ru.TermName("run")).asMethod
    val objMirror = m.reflect(im.instance)
    objMirror.reflectMethod(method)
  }

  def test(processId: String, processDeploymentData: ProcessDeploymentData, testData: TestData): TestResults = {

    //we have to use context loader, as in UI we have don't have esp-process on classpath...
    ThreadUtils.withThisAsContextClassLoader(classLoader) {
      processDeploymentData match {
        case GraphProcess(json) => invoker(json, config, testData, jars).asInstanceOf[TestResults]
        case _ => throw new IllegalArgumentException(s"Process $processId with deploymentData $processDeploymentData cannot be tested")
      }
    }
  }

}
