package pl.touk.nussknacker.engine.process.helpers

import com.typesafe.config.Config
import org.scalatest.Suite
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.dict.DictInstance
import pl.touk.nussknacker.engine.api.dict.embedded.EmbeddedDictDefinition
import pl.touk.nussknacker.engine.api.exception.NonTransientException
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.process.helpers.SampleNodes._
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.process.{ExecutionConfigPreparer, SimpleJavaEnum, registrar}
import pl.touk.nussknacker.engine.testing.LocalModelData

trait ProcessTestHelpers extends FlinkSpec { self: Suite =>

  object processInvoker {

    def invokeWithSampleData(process: CanonicalProcess, data: List[SimpleRecord]): Unit =
      invokeWithSampleData(process, data, config)

    def invokeWithSampleData(
        process: CanonicalProcess,
        data: List[SimpleRecord],
        config: Config,
        processVersion: ProcessVersion = ProcessVersion.empty
    ): Unit = {
      val creator: ProcessConfigCreator = ProcessTestHelpers.prepareCreator(data, config)

      val env       = flinkMiniCluster.createExecutionEnvironment()
      val modelData = LocalModelData(config, creator, List.empty)
      FlinkProcessRegistrar(new FlinkProcessCompiler(modelData), ExecutionConfigPreparer.unOptimizedChain(modelData))
        .register(env, process, processVersion, DeploymentData.empty)

      MockService.clear()
      SinkForStrings.clear()
      SinkForInts.clear()
      env.executeAndWaitForFinished(process.id)()
    }

    def invoke(
        process: CanonicalProcess,
        creator: ProcessConfigCreator,
        config: Config,
        processVersion: ProcessVersion,
        actionToInvokeWithJobRunning: => Unit
    ): Unit = {
      val env       = flinkMiniCluster.createExecutionEnvironment()
      val modelData = LocalModelData(config, creator, List.empty)
      registrar
        .FlinkProcessRegistrar(new FlinkProcessCompiler(modelData), ExecutionConfigPreparer.unOptimizedChain(modelData))
        .register(env, process, processVersion, DeploymentData.empty)

      MockService.clear()
      env.withJobRunning(process.id)(actionToInvokeWithJobRunning)
    }

  }

}

object ProcessTestHelpers {
  def prepareCreator(data: List[SimpleRecord], config: Config): ProcessConfigCreator = new ProcessBaseTestHelpers(data)
}

class ProcessBaseTestHelpers(data: List[SimpleRecord]) extends ProcessConfigCreator {

  override def services(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]] =
    Map(
      "logService"                       -> WithCategories.anyCategory(new MockService),
      "lifecycleService"                 -> WithCategories.anyCategory(LifecycleService),
      "eagerLifecycleService"            -> WithCategories.anyCategory(EagerLifecycleService),
      "enricherWithOpenService"          -> WithCategories.anyCategory(new EnricherWithOpenService),
      "serviceAcceptingOptionalValue"    -> WithCategories.anyCategory(ServiceAcceptingScalaOption),
      "returningComponentUseCaseService" -> WithCategories.anyCategory(ReturningComponentUseCaseService),
      "throwingNonTransientErrors" -> WithCategories.anyCategory(
        new ThrowingService(NonTransientException("test input", "test msg"))
      ),
    )

  override def sourceFactories(
      processObjectDependencies: ProcessObjectDependencies
  ): Map[String, WithCategories[SourceFactory]] = Map(
    "input"                            -> WithCategories.anyCategory(SampleNodes.simpleRecordSource(data)),
    "intInputWithParam"                -> WithCategories.anyCategory(new IntParamSourceFactory),
    "genericParametersSource"          -> WithCategories.anyCategory(GenericParametersSource),
    "genericSourceWithCustomVariables" -> WithCategories.anyCategory(GenericSourceWithCustomVariables)
  )

  override def sinkFactories(
      processObjectDependencies: ProcessObjectDependencies
  ): Map[String, WithCategories[SinkFactory]] = Map(
    "monitor"                    -> WithCategories.anyCategory(SinkFactory.noParam(MonitorEmptySink)),
    "sinkForInts"                -> WithCategories.anyCategory(SinkForInts.toSinkFactory),
    "sinkForStrings"             -> WithCategories.anyCategory(SinkForStrings.toSinkFactory),
    "eagerOptionalParameterSink" -> WithCategories.anyCategory(EagerOptionalParameterSinkFactory),
    "genericParametersSink"      -> WithCategories.anyCategory(GenericParametersSink)
  )

  override def customStreamTransformers(
      processObjectDependencies: ProcessObjectDependencies
  ): Map[String, WithCategories[CustomStreamTransformer]] = Map(
    "stateCustom"                       -> WithCategories.anyCategory(StateCustomNode),
    "customFilter"                      -> WithCategories.anyCategory(CustomFilter),
    "customFilterContextTransformation" -> WithCategories.anyCategory(CustomFilterContextTransformation),
    "customContextClear"                -> WithCategories.anyCategory(CustomContextClear),
    "sampleJoin"                        -> WithCategories.anyCategory(CustomJoin),
    "joinBranchExpression"              -> WithCategories.anyCategory(CustomJoinUsingBranchExpressions),
    "transformWithNullable"             -> WithCategories.anyCategory(TransformerWithNullableParam),
    "optionalEndingCustom"              -> WithCategories.anyCategory(OptionalEndingCustom),
    "genericParametersNode"             -> WithCategories.anyCategory(GenericParametersNode),
    "nodePassingStateToImplementation"  -> WithCategories.anyCategory(NodePassingStateToImplementation)
  )

  override def listeners(processObjectDependencies: ProcessObjectDependencies) = List(CountingNodesListener)

  override def expressionConfig(processObjectDependencies: ProcessObjectDependencies): ExpressionConfig = {
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

  override def buildInfo(): Map[String, String] = Map.empty
}
