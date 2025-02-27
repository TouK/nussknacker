package pl.touk.nussknacker.engine.lite.util.test

import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector

import scala.concurrent.{ExecutionContext, Future}

object TestService extends EagerService {

  val ServiceId = "testService"

  val MockedValued = "sample-mocked"

  @MethodToInvoke
  def prepare(@ParamName("param") value: LazyParameter[String]): ServiceInvoker = new ServiceInvoker {

    override def invoke(context: Context)(
        implicit ec: ExecutionContext,
        collector: ServiceInvocationCollector,
        componentUseCase: ComponentUseCase
    ): Future[String] = {
      collector.collect(s"test-service-$value", Option(MockedValued)) {
        Future.successful(value.evaluate(context))
      }
    }

  }

}
