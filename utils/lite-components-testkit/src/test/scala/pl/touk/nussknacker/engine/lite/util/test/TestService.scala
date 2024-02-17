package pl.touk.nussknacker.engine.lite.util.test

import pl.touk.nussknacker.engine.api.{
  ContextId,
  EagerService,
  LazyParameter,
  MethodToInvoke,
  ParamName,
  ServiceRuntimeLogic
}
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector

import scala.concurrent.{ExecutionContext, Future}

object TestService extends EagerService {

  val ServiceId = "testService"

  val MockedValued = "sample-mocked"

  @MethodToInvoke
  def invoke(@ParamName("param") value: LazyParameter[String]): ServiceRuntimeLogic = new ServiceRuntimeLogic {

    override def apply(params: Map[String, Any])(
        implicit ec: ExecutionContext,
        collector: ServiceInvocationCollector,
        contextId: ContextId,
        componentUseCase: ComponentUseCase
    ): Future[String] = {
      collector.collect(s"test-service-$value", Option(MockedValued)) {
        Future.successful(params("param").asInstanceOf[String])
      }
    }

  }

}
