package pl.touk.nussknacker.engine.lite.util.test

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.requestresponse.RequestResponseInterpreter
import pl.touk.nussknacker.engine.testmode.TestComponentsHolder
import pl.touk.nussknacker.engine.util.test.{ModelWithTestComponents, TestScenarioRunner}
import pl.touk.nussknacker.test.PatientScalaFutures

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.reflect.ClassTag

class RequestResponseTestScenarioRunner(val components: List[ComponentDefinition], val config: Config) extends TestScenarioRunner with PatientScalaFutures {

  override type Output = Any
  override type Input = Any

  override def runWithData[T<:Input:ClassTag, R<:Output](scenario: EspProcess, data: List[T]): List[R] = {
    val (modelData, runId) = ModelWithTestComponents.prepareModelWithTestComponents(config, components)
    import pl.touk.nussknacker.engine.requestresponse.FutureBasedRequestResponseScenarioInterpreter._
    try {
      val interpreter = RequestResponseInterpreter[Future](scenario,
        ProcessVersion.empty, LiteEngineRuntimeContextPreparer.noOp, modelData, Nil, null, null).getOrElse(throw new IllegalArgumentException(""))
      Future.sequence(data.map(interpreter.invokeToOutput)).futureValue.map(_.asInstanceOf[R])
    } finally {
      TestComponentsHolder.clean(runId)
    }
  }

}
