package pl.touk.nussknacker.engine.util.test

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.component.{Component, ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.testmode.TestRunId
import pl.touk.nussknacker.engine.util.test.TestComponentsProvider.testRunIdConfig

import java.util.UUID
import scala.reflect.ClassTag

case class TestExtensionsHolder(runId: TestRunId) extends Serializable {
  def components: List[ComponentDefinition] = TestExtensionsHolder.componentsForId(runId)

  def globalVariables: Map[String, AnyRef] = TestExtensionsHolder.globalVariablesForId(runId)

  def clean(): Unit = TestExtensionsHolder.clean(runId)

}

object TestExtensionsHolder {

  private var extensions = Map[TestRunId, Extensions]()

  def componentsForId(id: TestRunId): List[ComponentDefinition] =
    extensions(id).components

  def globalVariablesForId(id: TestRunId): Map[String, AnyRef] = extensions(id).globalVariables

  def registerTestExtensions(
      componentDefinitions: List[ComponentDefinition],
      globalVariables: Map[String, AnyRef]
  ): TestExtensionsHolder = synchronized {
    val runId = TestRunId.generate
    extensions += (runId -> Extensions(componentDefinitions, globalVariables))
    TestExtensionsHolder(runId)
  }

  def clean(runId: TestRunId): Unit = synchronized {
    extensions -= runId
  }

  private case class Extensions(components: List[ComponentDefinition], globalVariables: Map[String, AnyRef])

}

object TestComponentsProvider {

  val name = "test"

  val testRunIdConfig = "testRunId"

}

class TestComponentsProvider extends ComponentProvider {

  override def providerName: String = TestComponentsProvider.name

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = {
    TestExtensionsHolder.componentsForId(TestRunId(config.getString(testRunIdConfig)))
  }

  override def isCompatible(version: NussknackerVersion): Boolean = true

}
