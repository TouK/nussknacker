package pl.touk.nussknacker.engine.process.helpers

import com.typesafe.config.Config
import org.scalatest.Suite
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.dict.DictInstance
import pl.touk.nussknacker.engine.api.dict.embedded.EmbeddedDictDefinition
import pl.touk.nussknacker.engine.api.exception.NonTransientException
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.process.SimpleJavaEnum
import pl.touk.nussknacker.engine.process.helpers.SampleNodes._
import pl.touk.nussknacker.engine.process.runner.UnitTestsFlinkRunner
import pl.touk.nussknacker.engine.testing.LocalModelData

trait ProcessTestHelpers extends FlinkSpec { self: Suite =>

  object processInvoker {

    def invokeWithSampleData(process: CanonicalProcess, data: List[SimpleRecord]): Unit =
      invokeWithSampleData(process, data, config)

    def invokeWithSampleData(
        process: CanonicalProcess,
        data: List[SimpleRecord],
        config: Config
    ): Unit = {
      val components = ProcessTestHelpers.prepareComponents(data)
      val env        = flinkMiniCluster.createExecutionEnvironment()
      val modelData  = LocalModelData(config, components, configCreator = ProcessTestHelpersConfigCreator)
      UnitTestsFlinkRunner.registerInEnvironmentWithModel(env, modelData)(process)

      MockService.clear()
      SinkForStrings.clear()
      SinkForInts.clear()
      env.executeAndWaitForFinished(process.id)()
    }

  }

}

object ProcessTestHelpers {

  def prepareComponents(data: List[SimpleRecord]): List[ComponentDefinition] = List(
    ComponentDefinition("logService", new MockService),
    ComponentDefinition("lifecycleService", LifecycleService),
    ComponentDefinition("eagerLifecycleService", EagerLifecycleService),
    ComponentDefinition("enricherWithOpenService", new EnricherWithOpenService),
    ComponentDefinition("serviceAcceptingOptionalValue", ServiceAcceptingScalaOption),
    ComponentDefinition("returningComponentUseCaseService", ReturningComponentUseCaseService),
    ComponentDefinition(
      "throwingNonTransientErrors",
      new ThrowingService(NonTransientException("test input", "test msg"))
    ),
    ComponentDefinition("input", SampleNodes.simpleRecordSource(data)),
    ComponentDefinition("intInputWithParam", new IntParamSourceFactory),
    ComponentDefinition("genericParametersSource", GenericParametersSource),
    ComponentDefinition("genericSourceWithCustomVariables", GenericSourceWithCustomVariables),
    ComponentDefinition("monitor", SinkFactory.noParam(MonitorEmptySink)),
    ComponentDefinition("sinkForInts", SinkForInts.toSinkFactory),
    ComponentDefinition("sinkForStrings", SinkForStrings.toSinkFactory),
    ComponentDefinition("eagerOptionalParameterSink", EagerOptionalParameterSinkFactory),
    ComponentDefinition("genericParametersSink", GenericParametersSink),
    ComponentDefinition("stateCustom", StateCustomNode),
    ComponentDefinition("customFilter", CustomFilter),
    ComponentDefinition("customFilterContextTransformation", CustomFilterContextTransformation),
    ComponentDefinition("customContextClear", CustomContextClear),
    ComponentDefinition("sampleJoin", CustomJoin),
    ComponentDefinition("joinBranchExpression", CustomJoinUsingBranchExpressions),
    ComponentDefinition("transformWithNullable", TransformerWithNullableParam),
    ComponentDefinition("optionalEndingCustom", OptionalEndingCustom),
    ComponentDefinition("genericParametersNode", GenericParametersNode),
    ComponentDefinition("nodePassingStateToImplementation", NodePassingStateToImplementation),
  )

}

object ProcessTestHelpersConfigCreator extends EmptyProcessConfigCreator {
  override def listeners(modelDependencies: ProcessObjectDependencies): Seq[ProcessListener] =
    List(CountingNodesListener)

  override def expressionConfig(modelDependencies: ProcessObjectDependencies): ExpressionConfig = {
    val dictId  = EmbeddedDictDefinition.enumDictId(classOf[SimpleJavaEnum])
    val dictDef = EmbeddedDictDefinition.forJavaEnum(classOf[SimpleJavaEnum])
    val globalProcessVariables = Map(
      "processHelper" -> WithCategories.anyCategory(ProcessHelper),
      "enum"          -> WithCategories.anyCategory(DictInstance(dictId, dictDef)),
      "typedMap"      -> WithCategories.anyCategory(TypedMap(Map("aField" -> "123")))
    )
    ExpressionConfig(
      globalProcessVariables,
      List.empty,
      List.empty,
      dictionaries = Map(dictId -> dictDef)
    )
  }

}
