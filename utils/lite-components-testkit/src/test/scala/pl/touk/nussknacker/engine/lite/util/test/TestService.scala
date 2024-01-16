package pl.touk.nussknacker.engine.lite.util.test

import pl.touk.nussknacker.engine.api.{
  Context,
  ContextId,
  EagerService,
  LazyParameter,
  MethodToInvoke,
  ParamName,
  ServiceInvoker
}
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector

import scala.concurrent.{ExecutionContext, Future}

object TestService extends EagerService {

  val ServiceId = "testService"

  val MockedValued = "sample-mocked"

  @MethodToInvoke
  def invoke(@ParamName("param") value: LazyParameter[String]): ServiceInvoker = new ServiceInvoker {

    override def invokeService(context: Context, params: Map[String, Any])(
        implicit ec: ExecutionContext,
        collector: ServiceInvocationCollector,
        componentUseCase: ComponentUseCase
    ): Future[String] = {
      collector.collect(s"test-service-$value", Option(MockedValued)) {
        Future.successful(params("param").asInstanceOf[String])
      }
    }

  }

}
