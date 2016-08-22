package pl.touk.esp.engine.definition

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FlatSpec, Matchers, OptionValues}
import pl.touk.esp.engine.api.{ParamName, Service}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class ServiceInvokerTest extends FlatSpec with ScalaFutures with OptionValues with Matchers {

  it should "invoke service method with declared parameters as scala params" in {
    val mock = new MockService
    val invoker = ServiceInvoker(mock)

    whenReady(invoker.invoke(Map("foo" -> "aa", "bar" -> 1))) { _ =>
      mock.invoked.value.shouldEqual(("aa", 1))
    }

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