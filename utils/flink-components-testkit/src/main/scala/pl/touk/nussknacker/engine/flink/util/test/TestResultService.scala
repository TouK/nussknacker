package pl.touk.nussknacker.engine.flink.util.test

import pl.touk.nussknacker.engine.api.{MethodToInvoke, ParamName, Service}
import pl.touk.nussknacker.engine.util.test.{TestExtensionsHolder, TestScenarioRunner}

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

class TestResultService extends Service {

  private var invocationResult: List[Any] = List()

  @MethodToInvoke
  def invoke(@ParamName("value") value: Any)(implicit ec: ExecutionContext): Future[Unit] = {
    Future.successful {
      invocationResult = value :: invocationResult
    }
  }

  def data[T](): List[T] = invocationResult.reverse.map(_.asInstanceOf[T])

}

object TestResultService {

  def extractFromTestComponentsHolder[R](testExtensionsHolder: TestExtensionsHolder): List[R] = {
    testExtensionsHolder
      .components[Service]
      .find(_.name == TestScenarioRunner.testResultService)
      .map(_.component)
      .getOrElse(throw new IllegalStateException(s"No ${TestScenarioRunner.testResultService} service registered"))
      .asInstanceOf[TestResultService]
      .data[R]()
  }

}
