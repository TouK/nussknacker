package pl.touk.nussknacker.engine.process.helpers

import com.typesafe.config.Config
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import org.scalatest.Suite
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.dict.DictInstance
import pl.touk.nussknacker.engine.api.dict.embedded.EmbeddedDictDefinition
import pl.touk.nussknacker.engine.api.exception.NonTransientException
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.flink.api.process.FlinkCustomNodeContext
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.flink.test.ScalatestMiniClusterJobStatusCheckingOps.miniClusterWithServicesToOps
import pl.touk.nussknacker.engine.flink.util.sink.EmptySink
import pl.touk.nussknacker.engine.process.SimpleJavaEnum
import pl.touk.nussknacker.engine.process.helpers.SampleNodes._
import pl.touk.nussknacker.engine.process.runner.FlinkScenarioUnitTestJob
import pl.touk.nussknacker.engine.testing.LocalModelData

import scala.concurrent.ExecutionContext.Implicits.global

trait ProcessTestHelpers extends FlinkSpec { self: Suite =>

  object processInvoker {

    def invokeWithSampleData(
        process: CanonicalProcess,
        data: List[SimpleRecord],
        config: Config = config
    ): Unit = {
      val defaultComponents = ProcessTestHelpers.prepareComponents(data)
      ProcessTestHelpers.logServiceResultsHolder.clear()
      ProcessTestHelpers.sinkForStringsResultsHolder.clear()
      ProcessTestHelpers.sinkForIntsResultsHolder.clear()
      ProcessTestHelpers.eagerOptionalParameterSinkResultsHolder.clear()
      ProcessTestHelpers.genericParameterSinkResultsHolder.clear()
      ProcessTestHelpers.optionalEndingCustomResultsHolder.clear()
      flinkMiniCluster.withDetachedStreamExecutionEnvironment { env =>
        val modelData = LocalModelData(
          config,
          defaultComponents,
          configCreator = ProcessTestHelpersConfigCreator
        )
        val executionResult = new FlinkScenarioUnitTestJob(modelData).run(process, env)
        flinkMiniCluster.waitForJobIsFinished(executionResult.getJobID)
      }
    }

  }

}

// TODO: Having TestResultsHolder in a one shared between all tests object is a bad pattern. It can cause interfere with other tests.
//       We'd rather should create a separate TestResultsHolder for each tests and pass components using them to the invoker above.
object ProcessTestHelpers extends Serializable {

  val logServiceResultsHolder                 = new TestResultsHolder[Any]
  val sinkForStringsResultsHolder             = new TestResultsHolder[String]
  val sinkForIntsResultsHolder                = new TestResultsHolder[java.lang.Integer]
  val eagerOptionalParameterSinkResultsHolder = new TestResultsHolder[String]
  val genericParameterSinkResultsHolder       = new TestResultsHolder[String]
  val optionalEndingCustomResultsHolder       = new TestResultsHolder[AnyRef]

  def prepareComponents(data: List[SimpleRecord]): List[ComponentDefinition] = List(
    ComponentDefinition("logService", new MockService(logServiceResultsHolder)),
    ComponentDefinition("lifecycleService", LifecycleService),
    ComponentDefinition("eagerLifecycleService", EagerLifecycleService),
    ComponentDefinition("enricherWithOpenService", new EnricherWithOpenService),
    ComponentDefinition("serviceAcceptingOptionalValue", ServiceAcceptingScalaOption),
    ComponentDefinition("returningComponentUseContextService", ReturningComponentUseContextService),
    ComponentDefinition(
      "throwingNonTransientErrors",
      new ThrowingService(NonTransientException("test input", "test msg"))
    ),
    ComponentDefinition("input", SampleNodes.simpleRecordSource(data)),
    ComponentDefinition("intInputWithParam", new IntParamSourceFactory),
    ComponentDefinition("genericParametersSource", GenericParametersSource),
    ComponentDefinition("genericSourceWithCustomVariables", GenericSourceWithCustomVariables),
    ComponentDefinition("monitor", SinkFactory.noParam(MonitorEmptySink)),
    ComponentDefinition("sinkAccessingNodeContext", SinkFactory.noParam(SinkAccessingNodeContext)),
    ComponentDefinition("sinkForInts", SinkForInts(sinkForIntsResultsHolder)),
    ComponentDefinition("sinkForStrings", SinkForStrings(sinkForStringsResultsHolder)),
    ComponentDefinition(
      "eagerOptionalParameterSink",
      new EagerOptionalParameterSinkFactory(eagerOptionalParameterSinkResultsHolder)
    ),
    ComponentDefinition("genericParametersSink", new GenericParametersSink(genericParameterSinkResultsHolder)),
    ComponentDefinition("stateCustom", StateCustomNode),
    ComponentDefinition("customFilter", CustomFilter),
    ComponentDefinition("customFilterContextTransformation", CustomFilterContextTransformation),
    ComponentDefinition("customContextClear", CustomContextClear),
    ComponentDefinition("sampleJoin", CustomJoin),
    ComponentDefinition("joinBranchExpression", CustomJoinUsingBranchExpressions),
    ComponentDefinition("transformWithNullable", TransformerWithNullableParam),
    ComponentDefinition("optionalEndingCustom", new OptionalEndingCustom(optionalEndingCustomResultsHolder)),
    ComponentDefinition("genericParametersNode", GenericParametersNode),
    ComponentDefinition("nodePassingStateToImplementation", NodePassingStateToImplementation),
  )

}

object ProcessTestHelpersConfigCreator extends EmptyProcessConfigCreator {
  override def listeners(modelDependencies: ProcessObjectDependencies): Seq[ProcessListener] =
    List(CountingNodesListener, new LifecycleCheckingListener)

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

case object SinkAccessingNodeContext extends EmptySink with Serializable {

  @transient private var _nodeId: String = _

  def nodeId: String = _nodeId

  override def toFlinkFunction(flinkNodeContext: FlinkCustomNodeContext): SinkFunction[AnyRef] = {
    _nodeId = flinkNodeContext.nodeId
    super.toFlinkFunction(flinkNodeContext)
  }

}
