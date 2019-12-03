package pl.touk.nussknacker.engine.definition

import java.util.concurrent.Executor
import java.util.function.Supplier

import org.scalatest.{FlatSpec, Matchers, OptionValues}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.NodeContext
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectWithMethodDef
import pl.touk.nussknacker.test.PatientScalaFutures

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class ServiceInvokerTest extends FlatSpec with PatientScalaFutures with OptionValues with Matchers {

  implicit val metadata = MetaData("proc1", StreamMetaData())
  val jobData = JobData(metadata, ProcessVersion.empty)

  it should "invoke service method with declared parameters as scala params" in {
    val mock = new MockService(jobData)
    val definition = ObjectWithMethodDef.withEmptyConfig(mock, ServiceInvoker.Extractor)
    val invoker = ServiceInvoker(definition)

    whenReady(invoker.invoke(Map("foo" -> "aa", "bar" -> 1), NodeContext("", "", "", None))) { _ =>
      mock.invoked.value.shouldEqual(("aa", 1, metadata))
    }
  }

  it should "throw excpetion with nice message when parameters do not match" in {
    val mock = new MockService(jobData)
    val definition = ObjectWithMethodDef.withEmptyConfig(mock, ServiceInvoker.Extractor)
    val invoker = ServiceInvoker(definition)

    intercept[IllegalArgumentException](
      invoker.invoke(Map("foo" -> "aa", "bar" -> "terefere"), NodeContext("", "", "", None))).getMessage shouldBe "Parameter bar has invalid class: java.lang.String, should be: int"
  }

  it should "invoke service method with CompletionStage return type" in {
    val mock = new MockCompletionStageService(jobData)
    val definition = ObjectWithMethodDef.withEmptyConfig(mock, ServiceInvoker.Extractor)
    val invoker = ServiceInvoker(definition)

    whenReady(invoker.invoke(Map("foo" -> "aa", "bar" -> 1), NodeContext("", "", "", None))) { _ =>
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