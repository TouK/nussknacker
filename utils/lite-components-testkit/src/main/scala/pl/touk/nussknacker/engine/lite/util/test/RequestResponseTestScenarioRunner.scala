package pl.touk.nussknacker.engine.lite.util.test

import com.typesafe.config.Config
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.requestresponse.RequestResponseInterpreter
import pl.touk.nussknacker.engine.util.test.{ModelWithTestComponents, TestScenarioRunner}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.reflect.ClassTag

class RequestResponseTestScenarioRunner(val components: List[ComponentDefinition], val config: Config) extends TestScenarioRunner {

  override def runWithData[T: ClassTag, Result](scenario: EspProcess, data: List[T]): List[Result] = {
    val modelData = ModelWithTestComponents.prepareModelWithTestComponents(config, components)
    import pl.touk.nussknacker.engine.requestresponse.FutureBasedRequestResponseScenarioInterpreter._
    val interpreter = RequestResponseInterpreter[Future](scenario,
      ProcessVersion.empty, LiteEngineRuntimeContextPreparer.noOp, modelData, Nil, null, null).getOrElse(throw new IllegalArgumentException(""))
    Future.sequence(data.map(interpreter.invokeToOutput)).futureValue.map(_.asInstanceOf[Result])
  }
}
