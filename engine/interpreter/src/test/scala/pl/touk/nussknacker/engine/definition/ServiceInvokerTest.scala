package pl.touk.nussknacker.engine.definition

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FlatSpec, Matchers, OptionValues}
import pl.touk.nussknacker.engine.api.process.WithCategories
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.NodeContext
import pl.touk.nussknacker.engine.api.{ParamName, Service}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectWithMethodDef

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class ServiceInvokerTest extends FlatSpec with ScalaFutures with OptionValues with Matchers {

  it should "invoke service method with declared parameters as scala params" in {
    val mock = new MockService
    val definition = ObjectWithMethodDef[Service](WithCategories(mock), ServiceDefinitionExtractor)
    val invoker = ServiceInvoker(definition)

    whenReady(invoker.invoke(Map("foo" -> "aa", "bar" -> 1), NodeContext("", "", ""))) { _ =>
      mock.invoked.value.shouldEqual(("aa", 1))
    }

  }

  it should "throw excpetion with nice message when parameters do not match" in {
    val mock = new MockService
    val definition = ObjectWithMethodDef[Service](WithCategories(mock), ServiceDefinitionExtractor)
    val invoker = ServiceInvoker(definition)

    intercept[IllegalArgumentException](
      invoker.invoke(Map("foo" -> "aa", "bar" -> "terefere"), NodeContext("", "", ""))).getMessage shouldBe "Parameter bar has invalid class: java.lang.String, should be: int"
  }

}

class MockService extends Service {

  @volatile var invoked: Option[(String, Int)] = None

  def invoke(@ParamName("foo") foo: String, @ParamName("bar") bar: Int)
            (implicit ec: ExecutionContext): Future[Any] = {
    invoked = Some((foo, bar))
    Future.successful(Unit)
  }

}