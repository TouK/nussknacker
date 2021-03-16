package pl.touk.nussknacker.engine.definition

import java.util.concurrent.Executor
import java.util.function.Supplier
import org.scalatest.{FlatSpec, Matchers, OptionValues}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.deployment.DeploymentData
import pl.touk.nussknacker.engine.api.test.InvocationCollectors
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.{ServiceInvocationCollector, ToCollect}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectWithMethodDef
import pl.touk.nussknacker.test.PatientScalaFutures

import scala.concurrent.{ExecutionContext, Future}

class ServiceInvokerTest extends FlatSpec with PatientScalaFutures with OptionValues with Matchers {

  private implicit val metadata: MetaData = MetaData("proc1", StreamMetaData())
  private val nodeId = NodeId("id")
  private val jobData: JobData = JobData(metadata, ProcessVersion.empty, DeploymentData.empty)
  private implicit val ctxId: ContextId = ContextId("")
  private implicit val collector: ServiceInvocationCollector = new ServiceInvocationCollector {
    override def collectWithResponse[A](request: => ToCollect, mockValue: Option[A])
                                       (action: => Future[InvocationCollectors.CollectableAction[A]], names: InvocationCollectors.TransmissionNames)
                                       (implicit ec: ExecutionContext): Future[A] = action.map(_.result)
  }

  import scala.concurrent.ExecutionContext.Implicits.global

  it should "invoke service method with declared parameters as scala params" in {
    val mock = new MockService(jobData)
    val definition = ObjectWithMethodDef.withEmptyConfig(mock, DefaultServiceInvoker.Extractor)
    val invoker = DefaultServiceInvoker(metadata, nodeId, None, definition)

    whenReady(invoker.invokeService(Map("foo" -> "aa", "bar" -> 1))) { _ =>
      mock.invoked.value.shouldEqual(("aa", 1, metadata))
    }
  }

  it should "throw excpetion with nice message when parameters do not match" in {
    val mock = new MockService(jobData)
    val definition = ObjectWithMethodDef.withEmptyConfig(mock, DefaultServiceInvoker.Extractor)
    val invoker = DefaultServiceInvoker(metadata, nodeId, None, definition)

    intercept[IllegalArgumentException](
      invoker.invokeService(Map("foo" -> "aa", "bar" -> "terefere")))
        .getMessage shouldBe """Failed to invoke "invoke" on MockService with parameter types: List(String, String, ExecutionContextImpl): argument type mismatch"""
  }

  it should "invoke service method with CompletionStage return type" in {
    val mock = new MockCompletionStageService(jobData)
    val definition = ObjectWithMethodDef.withEmptyConfig(mock, DefaultServiceInvoker.Extractor)
    val invoker = DefaultServiceInvoker(metadata, nodeId, None, definition)

    whenReady(invoker.invokeService(Map("foo" -> "aa", "bar" -> 1))) { _ =>
      mock.invoked.value.shouldEqual(("aa", 1, metadata))
    }
  }

}

class MockService(override val jobData: JobData) extends Service with WithJobData {

  @volatile var invoked: Option[(String, Int, MetaData)] = None

  @MethodToInvoke
  def invoke(@ParamName("foo") foo: String, @ParamName("bar") bar: Int)
            (implicit ec: ExecutionContext): Future[Any] = {
    invoked = Some((foo, bar, jobData.metaData))
    Future.successful(Unit)
  }

}

class MockCompletionStageService(override val jobData: JobData) extends Service with WithJobData {

  @volatile var invoked: Option[(String, Int, MetaData)] = None

  @MethodToInvoke
  def invoke(@ParamName("foo") foo: String, @ParamName("bar") bar: Int)
            (implicit executor: Executor): java.util.concurrent.CompletionStage[Any] = {
    java.util.concurrent.CompletableFuture.supplyAsync(new Supplier[Any] {
      override def get() = {
        invoked = Some((foo, bar, jobData.metaData))
      }
    }, executor)
  }

}
