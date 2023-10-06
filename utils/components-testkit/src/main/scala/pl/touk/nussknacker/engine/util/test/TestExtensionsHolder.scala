package pl.touk.nussknacker.engine.util.test

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.component.{Component, ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.testmode.TestRunId
import pl.touk.nussknacker.engine.util.test.TestComponentsProvider.testRunIdConfig

import java.util.UUID
import scala.reflect.ClassTag

case class TestExtensionsHolder(runId: TestRunId) extends Serializable {

  def components[T <: Component: ClassTag]: List[ComponentDefinition] = TestExtensionsHolder.componentsForId[T](runId)

  def globalVariables: Map[String, AnyRef] = TestExtensionsHolder.globalVariablesForId(runId)

  def clean(): Unit = TestExtensionsHolder.clean(runId)

}

object TestExtensionsHolder {

  private var extensions = Map[TestRunId, Extensions]()

  def componentsForId[T <: Component: ClassTag](id: TestRunId): List[ComponentDefinition] =
    extensions(id).components.collect { case ComponentDefinition(name, component: T, _, _) =>
      ComponentDefinition(name, component)
    }

  def globalVariablesForId(id: TestRunId): Map[String, AnyRef] = extensions(id).variables

  def registerTestExtensions(
      componentDefinitions: List[ComponentDefinition],
      globalVariables: Map[String, AnyRef]
  ): TestExtensionsHolder = synchronized {
    val runId = TestRunId(UUID.randomUUID().toString)
    extensions += (runId -> Extensions(componentDefinitions, globalVariables))
    TestExtensionsHolder(runId)
  }

  def clean(runId: TestRunId): Unit = synchronized {
    extensions -= runId
  }

  private case class Extensions(components: List[ComponentDefinition], variables: Map[String, AnyRef])

}

object TestComponentsProvider {

  val name = "test"

  val testRunIdConfig = "testRunId"

}

class TestComponentsProvider extends ComponentProvider {

  override def providerName: String = TestComponentsProvider.name

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = {
    TestExtensionsHolder.componentsForId[Component](TestRunId(config.getString(testRunIdConfig)))
  }

  override def isCompatible(version: NussknackerVersion): Boolean = true

}
