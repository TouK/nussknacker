package pl.touk.nussknacker.engine.lite.util.test

import cats.data.Validated.{Invalid, Valid}
import cats.data.ValidatedNel
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.lite.api.commonTypes.ErrorType
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.requestresponse.RequestResponseInterpreter
import pl.touk.nussknacker.engine.testmode.TestComponentsHolder
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner.RunnerResult
import pl.touk.nussknacker.engine.util.test.{ClassBasedTestScenarioRunner, ModelWithTestComponents, RunResult}
import pl.touk.nussknacker.test.PatientScalaFutures

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.reflect.ClassTag

class RequestResponseTestScenarioRunner(val components: List[ComponentDefinition], val config: Config) extends ClassBasedTestScenarioRunner with PatientScalaFutures {

  override def runWithData[I:ClassTag, R](scenario: EspProcess, data: List[I]): RunnerResult[R] = {
    val (modelData, runId) = ModelWithTestComponents.prepareModelWithTestComponents(config, components)
    import pl.touk.nussknacker.engine.requestresponse.FutureBasedRequestResponseScenarioInterpreter._

    try {
      RequestResponseInterpreter[Future](
        scenario, ProcessVersion.empty,
        LiteEngineRuntimeContextPreparer.noOp, modelData,
        Nil, null, null
      ).map{ interpreter =>
        val result: List[ValidatedNel[ErrorType, List[Any]]] = Future.sequence(data.map(interpreter.invokeToOutput)).futureValue

        val (errors, successes) = result.foldRight((List.empty[ErrorType], List.empty[R])) {
          case (Valid(result), (errors, successes)) => (errors, result.map(_.asInstanceOf[R]) ::: successes)
          case (Invalid(e), (errors, successes)) => (e.toList ::: errors, successes)
        }

        RunResult(errors, successes)
      }
    } finally {
      TestComponentsHolder.clean(runId)
    }
  }

}
