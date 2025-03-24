package pl.touk.nussknacker.engine.flink.table.utils

import org.scalatest.{BeforeAndAfterAll, Suite}
import pl.touk.nussknacker.engine.classloader.ModelClassLoader

// This is only for purpose of using an empty URLClassLoader as contextClassLoader in tests ran from Intellij Idea
// TODO: check if this will still be necessary after changes in NodeDependency
trait ModelClassLoaderSimulationSuite extends BeforeAndAfterAll { this: Suite =>

  private val originalContextClassLoader: ClassLoader       = Thread.currentThread().getContextClassLoader
  protected val simulatedModelClassloader: ModelClassLoader = ModelClassLoader.flinkWorkAroundEmptyClassloader

  override protected def beforeAll(): Unit = {
    Thread.currentThread().setContextClassLoader(simulatedModelClassloader)
    super.beforeAll()
  }

  override protected def afterAll(): Unit = {
    Thread.currentThread().setContextClassLoader(originalContextClassLoader)
    super.afterAll()
  }

}
