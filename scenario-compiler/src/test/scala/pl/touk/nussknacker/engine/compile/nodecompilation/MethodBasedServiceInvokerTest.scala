package pl.touk.nussknacker.engine.compile.nodecompilation

import org.scalatest.OptionValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.ScenarioCompilationDependencies
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.definition.EngineScenarioCompilationDependencies
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.ComponentUseContext
import pl.touk.nussknacker.engine.definition.component.{
  ComponentDefinitionWithImplementation,
  NodeCompilationDependencies
}
import pl.touk.nussknacker.engine.graph.node.Processor
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.util.definition.WithJobData
import pl.touk.nussknacker.test.PatientScalaFutures

import scala.concurrent.{ExecutionContext, Future}

class ServiceInvokerTest extends AnyFlatSpec with PatientScalaFutures with OptionValues with Matchers {

  import pl.touk.nussknacker.engine.api.test.EmptyInvocationCollector.Instance

  import scala.concurrent.ExecutionContext.Implicits.global

  private implicit val metadata: MetaData                       = MetaData("proc1", StreamMetaData())
  private implicit val componentUseContext: ComponentUseContext = ComponentUseContext.LiveRuntime(None)
  private val context: Context                                  = Context.dummy

  private val jobData: JobData = JobData(metadata, ProcessVersion.empty.copy(processName = metadata.name))
  private val scenarioCompilationDependencies =
    new ScenarioCompilationDependencies(jobData, EngineScenarioCompilationDependencies.empty)

  private val nodeCompilationContext = new NodeCompilationDependencies(
    scenarioCompilationDependencies = scenarioCompilationDependencies,
    nodeData = Processor(NodeId("id"), NodeName("id"), ServiceRef("id", List.empty)),
    componentUseContext = componentUseContext,
    inputValidationContext = SingleInputNodeInputValidationContext(ValidationContext.empty)
  )

  it should "invoke service method with declared parameters as scala params" in {
    val mock       = new MockService(jobData)
    val definition = ComponentDefinitionWithImplementation.withEmptyConfig("foo", mock)
    val invoker =
      new MethodBasedServiceInvoker(
        componentDefinition = definition,
        nodeCompilationContext = nodeCompilationContext,
        parametersProvider = _ => Params.fromRawValuesMap(Map(ParameterName("foo") -> "aa", ParameterName("bar") -> 1))
      )

    whenReady(invoker.invoke(context)) { _ =>
      mock.invoked.value.shouldEqual(("aa", 1, metadata))
    }
  }

  it should "throw excpetion with nice message when parameters do not match" in {
    val mock       = new MockService(jobData)
    val definition = ComponentDefinitionWithImplementation.withEmptyConfig("foo", mock)
    val invoker = new MethodBasedServiceInvoker(
      componentDefinition = definition,
      nodeCompilationContext = nodeCompilationContext,
      parametersProvider =
        _ => Params.fromRawValuesMap(Map(ParameterName("foo") -> "aa", ParameterName("bar") -> "terefere"))
    )

    intercept[IllegalArgumentException](
      invoker.invoke(context)
    ).getMessage shouldBe """Failed to invoke "invoke" on MockService with parameter types: List(String, String): argument type mismatch"""
  }

}

class MockService(override val jobData: JobData) extends Service with WithJobData {

  @volatile var invoked: Option[(String, Int, MetaData)] = None

  @MethodToInvoke
  def invoke(@ParamName("foo") foo: String, @ParamName("bar") bar: Int)(implicit ec: ExecutionContext): Future[Any] = {
    invoked = Some((foo, bar, jobData.metaData))
    Future.successful(())
  }

}
