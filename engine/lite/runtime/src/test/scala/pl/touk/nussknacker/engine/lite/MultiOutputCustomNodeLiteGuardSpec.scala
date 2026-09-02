package pl.touk.nussknacker.engine.lite

import cats.data.NonEmptyList
import com.typesafe.config.ConfigFactory
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.RuntimeMode
import pl.touk.nussknacker.engine.api.{
  CustomStreamTransformer,
  JobData,
  LazyParameter,
  MethodToInvoke,
  OutputVariableName,
  ParamName,
  ProcessVersion
}
import pl.touk.nussknacker.engine.api.component.{
  ComponentDefinition,
  ComponentOutput,
  NodesDeploymentData,
  SupportsMultipleOutputs
}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, UnsupportedPart}
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.lite.sample.{SampleInput, SimpleSinkFactory, SimpleSourceFactory, StateType}
import pl.touk.nussknacker.engine.resultcollector.ProductionServiceInvocationCollector
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.SynchronousExecutionContextAndIORuntime.syncEc

class MultiOutputCustomNodeLiteGuardSpec extends AnyFunSuite with Matchers with OptionValues {

  import sample.{capabilityTransformer, shape}

  import MultiOutputCustomNodeLiteGuardSpec._

  test("a custom node with a connected additional output fails lite compilation with CustomNodeError") {
    val errors = compileWithMultiOutputComponent(MultiOutputSumTransformerFactory)

    errors should contain(
      CustomNodeError(
        "split",
        "Additional outputs of this component are not supported on this engine - disconnect them.",
        None
      )
    )
  }

  test("an implementation carrying the SupportsMultipleOutputs marker is stopped by the interpreter guard") {
    val errors = compileWithMultiOutputComponent(MarkedMultiOutputSumTransformerFactory)

    errors should contain(UnsupportedPart("split"))
  }

  private def compileWithMultiOutputComponent(factory: CustomStreamTransformer): List[ProcessCompilationError] = {
    val scenario = ScenarioBuilder
      .streamingLite("multi-output-lite")
      .source("start", "start")
      .customNodeWithOutputs(
        "split",
        Some("sum"),
        "multiOutputSum",
        List(
          "main"     -> GraphBuilder.emptySink("end", "end", "value" -> "#input".spel).value,
          "rejected" -> GraphBuilder.emptySink("rejected-end", "end", "value" -> "#input".spel).value
        ),
        "name"  -> "'test'".spel,
        "value" -> "#input".spel
      )

    val components = List(
      ComponentDefinition("start", SimpleSourceFactory),
      ComponentDefinition("end", SimpleSinkFactory),
      ComponentDefinition("multiOutputSum", factory)
    )
    val modelData = LocalModelData(ConfigFactory.empty(), components)
    val jobData   = JobData(scenario.metaData, ProcessVersion.empty.copy(processName = scenario.metaData.name))

    val result = ScenarioInterpreterFactory.createInterpreter[StateType, SampleInput, AnyRef](
      scenario,
      jobData,
      NodesDeploymentData.empty,
      modelData,
      additionalListeners = Nil,
      resultCollector = ProductionServiceInvocationCollector,
      runtimeMode = RuntimeMode.Live,
    )
    result.swap.toOption.value.toList
  }

}

object MultiOutputCustomNodeLiteGuardSpec {

  /**
    * Declares an additional output, so it gets past the unknown-output validation, but the implementation it
    * returns does not carry `SupportsMultipleOutputs` - compilation stops at the compiler's marker check,
    * before the interpreter guard. The transformation logic itself never runs.
    */
  object MultiOutputSumTransformerFactory extends CustomStreamTransformer {

    override def outputs: NonEmptyList[ComponentOutput] =
      NonEmptyList(ComponentOutput.MainOutput, List(ComponentOutput.RejectedOutput))

    @MethodToInvoke(returnType = classOf[Double])
    def invoke(
        @ParamName("name") name: String,
        @ParamName("value") value: LazyParameter[java.lang.Double],
        @OutputVariableName outputVar: String
    ) = new sample.SumTransformer(name, outputVar, value)

  }

  /**
    * Same declaration, but the returned implementation wrongly carries the public `SupportsMultipleOutputs`
    * marker while being a plain lite component. The compiler's marker check trusts it and passes, so only the
    * `validateTransformer` guard in `ScenarioInterpreterFactory` stops the scenario, with `UnsupportedPart` -
    * without it, the job would start with every additional output's branch silently starved.
    */
  object MarkedMultiOutputSumTransformerFactory extends CustomStreamTransformer {

    override def outputs: NonEmptyList[ComponentOutput] =
      NonEmptyList(ComponentOutput.MainOutput, List(ComponentOutput.RejectedOutput))

    @MethodToInvoke(returnType = classOf[Double])
    def invoke(
        @ParamName("name") name: String,
        @ParamName("value") value: LazyParameter[java.lang.Double],
        @OutputVariableName outputVar: String
    ) = new sample.SumTransformer(name, outputVar, value) with SupportsMultipleOutputs

  }

}
