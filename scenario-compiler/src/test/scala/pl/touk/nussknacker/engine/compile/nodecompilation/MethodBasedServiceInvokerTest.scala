package pl.touk.nussknacker.engine.compile.nodecompilation

import org.scalatest.OptionValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.NodesDeploymentData
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.ComponentUseContext
import pl.touk.nussknacker.engine.definition.component.ComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.util.definition.WithJobData
import pl.touk.nussknacker.test.PatientScalaFutures

import scala.concurrent.{ExecutionContext, Future}

class ServiceInvokerTest extends AnyFlatSpec with PatientScalaFutures with OptionValues with Matchers {

  import pl.touk.nussknacker.engine.api.test.EmptyInvocationCollector.Instance

  import scala.concurrent.ExecutionContext.Implicits.global

  private implicit val metadata: MetaData                       = MetaData("proc1", StreamMetaData())
  private implicit val componentUseContext: ComponentUseContext = ComponentUseContext.EngineRuntime(None)
  private val context: Context                                  = Context.withInitialId

  private val nodeId           = NodeId("id")
  private val jobData: JobData = JobData(metadata, ProcessVersion.empty.copy(processName = metadata.name))

  it should "invoke service method with declared parameters as scala params" in {
    val mock       = new MockService(jobData)
    val definition = ComponentDefinitionWithImplementation.withEmptyConfig("foo", mock)
    val invoker =
      new MethodBasedServiceInvoker(
        metaData = metadata,
        nodeId = nodeId,
        outputVariableNameOpt = None,
        componentDefinition = definition,
        parametersProvider = _ => Params(Map(ParameterName("foo") -> "aa", ParameterName("bar") -> 1))
      )

    whenReady(invoker.invoke(context)) { _ =>
      mock.invoked.value.shouldEqual(("aa", 1, metadata))
    }
  }

  it should "throw excpetion with nice message when parameters do not match" in {
    val mock       = new MockService(jobData)
    val definition = ComponentDefinitionWithImplementation.withEmptyConfig("foo", mock)
    val invoker = new MethodBasedServiceInvoker(
      metaData = metadata,
      nodeId = nodeId,
      outputVariableNameOpt = None,
      componentDefinition = definition,
      parametersProvider = _ => Params(Map(ParameterName("foo") -> "aa", ParameterName("bar") -> "terefere"))
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
