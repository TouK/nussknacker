package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory.fromAnyRef
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.scalatest.Inside
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.process.{EmptyProcessConfigCreator, ProcessObjectDependencies, SourceFactory, WithCategories}
import pl.touk.nussknacker.engine.api.{CustomStreamTransformer, ProcessListener, ProcessVersion}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.flink.util.source.EmitWatermarkAfterEachElementCollectionSource
import pl.touk.nussknacker.engine.process.ExecutionConfigPreparer
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.testmode.{ResultsCollectingListener, ResultsCollectingListenerHolder}

import java.time.Duration

class AggregateTest extends AnyFunSuite with FlinkSpec with Matchers with Inside {

  private val processId = "aggregateFilterProcess"

  private val process: CanonicalProcess =
    ScenarioBuilder
      .streaming(processId)
      .parallelism(1)
      .source("start", "start")
      .customNode("aggregate",
        "aggregate",
        "aggregate-sliding",
        "groupBy" -> "#input.id",
        "aggregateBy" -> "{account: #input.id}",
        "aggregator" -> "#AGG.map({account: #AGG.last})",
        "windowLength" -> "T(java.time.Duration).parse('PT5H')",
        "emitWhenEventLeft" -> "false"
      )
      .customNodeNoOutput("rewriter", "rewriter",
        "key" -> "#aggregate.account"
      )
      .emptySink("spoolResult", "dead-end")

  test("aggregate should produce aggregateId variable") {

    val model = modelData(List(
      TestRecord(id = "2", eId = 1, timeHours = 0, str = "a"),
      TestRecord(id = "2", eId = 2, timeHours = 1, str = "b"),
      TestRecord(id = "2", eId = 3, timeHours = 2, str = "c"),
      TestRecord(id = "3", eId = 1, timeHours = 3, str = "d"),
      TestRecord(id = "3", eId = 5, timeHours = 4, str = "e"),
      TestRecord(id = "2", eId = 2, timeHours = 6, str = "g"),
    ))

    val collectingListener = ResultsCollectingListenerHolder.registerRun(identity)
    runProcess(model, process, collectingListener)

    val result = collectingListener.results[Any].nodeResults("spoolResult")
      .map {
        _.variableTyped("aggregate")
      }

    println(collectingListener.results[Any].nodeResults("spoolResult"))
    println(result)
  }

  //copy-paste from another test
  def modelData(list: List[TestRecord] = List()): LocalModelData = LocalModelData(ConfigFactory
    .empty().withValue("useTypingResultTypeInformation", fromAnyRef(true)), new AggregateCreator(list))

  //copy-paste from another test
  protected def runProcess(model: LocalModelData, testProcess: CanonicalProcess, collectingListener: ResultsCollectingListener): Unit = {
    val stoppableEnv = flinkMiniCluster.createExecutionEnvironment()
    val registrar = FlinkProcessRegistrar(new FlinkProcessCompiler(model) {
      override protected def adjustListeners(defaults: List[ProcessListener], processObjectDependencies: ProcessObjectDependencies): List[ProcessListener] = {
        collectingListener :: defaults
      }
    }, ExecutionConfigPreparer.unOptimizedChain(model))
    registrar.register(stoppableEnv, testProcess, ProcessVersion.empty, DeploymentData.empty)
    stoppableEnv.executeAndWaitForFinished(testProcess.id)()
  }
}

//copy-paste from another test, only `rewriter` was added
class AggregateCreator(input: List[TestRecord]) extends EmptyProcessConfigCreator {

  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory]] = {
    Map("start" -> WithCategories(SourceFactory.noParam[TestRecord](EmitWatermarkAfterEachElementCollectionSource
      .create[TestRecord](input, _.timestamp, Duration.ofHours(1))(TypeInformation.of(classOf[TestRecord])))))
  }

  override def customStreamTransformers(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[CustomStreamTransformer]] = {
    super.customStreamTransformers(processObjectDependencies) ++ Map("rewriter" -> WithCategories(new RewriterCustomTransformer()))
  }
}

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.datastream._
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkCustomStreamTransformation, FlinkLazyParameterFunctionHelper, LazyParameterInterpreterFunction}

class RewriterCustomTransformer() extends CustomStreamTransformer {
  @MethodToInvoke(returnType = classOf[Void])
  def invoke(@ParamName("key") key: LazyParameter[String]): FlinkCustomStreamTransformation
  = FlinkCustomStreamTransformation((stream: DataStream[Context], ctx: FlinkCustomNodeContext) => {
    stream
      .flatMap(ctx.lazyParameterHelper.lazyMapFunction(key))
      .keyBy((value: ValueWithContext[String]) => value.value)
      .process(new RewriteHandler(ctx.lazyParameterHelper))
  })

}

class RewriteHandler(val lazyParameterHelper: FlinkLazyParameterFunctionHelper) extends
  KeyedProcessFunction[String, ValueWithContext[String], ValueWithContext[AnyRef]] with LazyParameterInterpreterFunction with LazyLogging {

  override def open(config: Configuration): Unit = {
    super.open(config)
  }

  override def processElement(value: ValueWithContext[String], ctx: KeyedProcessFunction[String, ValueWithContext[String], ValueWithContext[AnyRef]]#Context,
                              out: Collector[ValueWithContext[AnyRef]]): Unit = {
    out.collect(ValueWithContext[AnyRef](value.value, value.context))
  }
}
