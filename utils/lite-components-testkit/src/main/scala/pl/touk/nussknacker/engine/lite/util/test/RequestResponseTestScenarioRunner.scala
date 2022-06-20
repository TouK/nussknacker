package pl.touk.nussknacker.engine.lite.util.test

import cats.data.{Validated, ValidatedNel}
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.lite.api.commonTypes.ErrorType
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.requestresponse.RequestResponseInterpreter
import pl.touk.nussknacker.engine.testmode.TestComponentsHolder
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner.RunnerResult
import pl.touk.nussknacker.engine.util.test.{ClassBaseTestScenarioRunner, ModelWithTestComponents, RunResult}
import pl.touk.nussknacker.test.PatientScalaFutures

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.reflect.ClassTag

class RequestResponseTestScenarioRunner(val components: List[ComponentDefinition], val config: Config) extends ClassBaseTestScenarioRunner with PatientScalaFutures {

  override def runWithData[I:ClassTag, R](scenario: EspProcess, data: List[I]): RunnerResult[R] = {
    val (modelData, runId) = ModelWithTestComponents.prepareModelWithTestComponents(config, components)
    import pl.touk.nussknacker.engine.requestresponse.FutureBasedRequestResponseScenarioInterpreter._

    val result = RequestResponseInterpreter[Future](
      scenario, ProcessVersion.empty,
      LiteEngineRuntimeContextPreparer.noOp, modelData,
      Nil, null, null
    ).map(interpreter => {
      val result: List[ValidatedNel[ErrorType, List[Any]]] = Future.sequence(data.map(interpreter.invokeToOutput)).futureValue

      val errors = result.collect {
        case Validated.Invalid(e) => e.toList.map(_.throwable.getMessage)
      }.flatten

      val successes = result.collect {
        case Validated.Valid(r) => r.map(_.asInstanceOf[R])
      }.flatten

      RunResult(errors, successes)
    })

    TestComponentsHolder.clean(runId)
    result
  }

}
