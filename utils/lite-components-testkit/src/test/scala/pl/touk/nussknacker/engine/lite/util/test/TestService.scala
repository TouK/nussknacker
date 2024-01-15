package pl.touk.nussknacker.engine.lite.util.test

import pl.touk.nussknacker.engine.api.ServiceLogic.{ParamsEvaluator, RunContext}
import pl.touk.nussknacker.engine.api._

import scala.concurrent.{ExecutionContext, Future}

object TestService extends EagerService {

  val ServiceId = "testService"

  val MockedValued = "sample-mocked"

  @MethodToInvoke
  def invoke(@ParamName("param") value: LazyParameter[String]): ServiceLogic = new ServiceLogic {

    override def run(
        paramsEvaluator: ParamsEvaluator
    )(implicit runContext: RunContext, executionContext: ExecutionContext): Future[Any] = {
      val params = paramsEvaluator.evaluate()
      runContext.collector
        .collect(s"test-service-$value", Option(MockedValued)) {
          Future.successful(params.getUnsafe[String]("param"))
        }
    }

  }

}
