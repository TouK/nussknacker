package pl.touk.nussknacker.engine.util.test

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.component.{Component, ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, WithCategories}
import pl.touk.nussknacker.engine.testmode.TestRunId
import pl.touk.nussknacker.engine.util.test.TestComponentsProvider.testRunIdConfig

import java.util.UUID
import scala.reflect.ClassTag

case class TestComponentsHolder(runId: TestRunId) extends Serializable {

  def components[T <: Component: ClassTag]: List[ComponentDefinition] = TestComponentsHolder.componentsForId[T](runId)

  def globalVariables: Map[String, WithCategories[AnyRef]] = TestComponentsHolder.variables(runId)

  def clean(): Unit = TestComponentsHolder.clean(runId)

}

object TestComponentsHolder {

  private var components = Map[TestRunId, List[ComponentDefinition]]()
  private var variables  = Map[TestRunId, Map[String, WithCategories[AnyRef]]]()

  def componentsForId[T <: Component: ClassTag](id: TestRunId): List[ComponentDefinition] = components(id).collect {
    case ComponentDefinition(name, component: T, _, _) => ComponentDefinition(name, component)
  }

  def globalVariables(id: TestRunId): Map[String, WithCategories[AnyRef]] = variables(id)

  def registerTestComponents(
      componentDefinitions: List[ComponentDefinition],
      globalVariables: Map[String, WithCategories[AnyRef]]
  ): TestComponentsHolder = synchronized {
    val runId = TestRunId(UUID.randomUUID().toString)
    components += (runId -> componentDefinitions)
    variables += (runId  -> globalVariables)
    TestComponentsHolder(runId)
  }

  def clean(runId: TestRunId): Unit = synchronized {
    components -= runId
    variables -= runId
  }

}

object TestComponentsProvider {

  val name = "test"

  val testRunIdConfig = "testRunId"

}

class TestComponentsProvider extends ComponentProvider {

  override def providerName: String = TestComponentsProvider.name

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = {
    TestComponentsHolder.componentsForId[Component](TestRunId(config.getString(testRunIdConfig)))
  }

  override def isCompatible(version: NussknackerVersion): Boolean = true

}
