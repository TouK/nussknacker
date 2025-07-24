package pl.touk.nussknacker.engine.process.scenariotesting

import org.apache.flink.api.common.restartstrategy.RestartStrategies
import pl.touk.nussknacker.engine.{ModelConfig, ModelData, RuntimeMode}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.NodesDeploymentData
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.test.ScenarioTestData
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compile.nodecompilation.EvaluableLazyParameterCreator
import pl.touk.nussknacker.engine.definition.component.{
  ComponentDefinitionWithImplementation,
  NodeCompilationDependencies
}
import pl.touk.nussknacker.engine.definition.component.ComponentImplementationInvoker.ComponentImplementationSpecificInvocationContext
import pl.touk.nussknacker.engine.flink.api.exception.FlinkEspExceptionConsumer
import pl.touk.nussknacker.engine.flink.api.process.FlinkSourceTestSupport
import pl.touk.nussknacker.engine.flink.util.source.EmptySource
import pl.touk.nussknacker.engine.process.compiler.ComponentDefinitionContext
import pl.touk.nussknacker.engine.process.exception.FlinkExceptionHandler
import pl.touk.nussknacker.engine.testmode.{ResultsCollectingListener, TestDataPreparer}

import scala.annotation.nowarn

class TestFlinkProcessCompilerDataFactory(
    process: CanonicalProcess,
    scenarioTestData: ScenarioTestData,
    modelData: ModelData,
    jobData: JobData,
    collectingListener: ResultsCollectingListener[_]
) extends StubbedFlinkProcessCompilerDataFactory(
      process,
      modelData.configCreator,
      modelData.extractModelDefinitionFun,
      modelData.modelConfig,
      RuntimeMode.Test,
      modelData.additionalConfigsFromProvider,
      NodesDeploymentData.empty,
      List.empty,
    ) {

  override protected def adjustListeners(
      defaults: List[ProcessListener],
      modelConfig: ModelConfig
  ): List[ProcessListener] = {
    collectingListener :: defaults
  }

  override protected def prepareSourceFactory(
      sourceFactory: ComponentDefinitionWithImplementation,
      context: ComponentDefinitionContext
  ): ComponentDefinitionWithImplementation = {
    val sourcePreparer = new StubbedSourcePreparer(
      new TestDataPreparer(
        context.userCodeClassLoader,
        context.expressionConfig,
        context.dictRegistry,
        context.classDefinitions,
        jobData
      ),
      scenarioTestData
    )
    sourceFactory.withImplementationInvoker(new TestSourceComponentImplementationInvoker(sourcePreparer, sourceFactory))
  }

  override protected def prepareService(
      service: ComponentDefinitionWithImplementation,
      context: ComponentDefinitionContext
  ): ComponentDefinitionWithImplementation = service

  override protected def exceptionHandler(
      metaData: MetaData,
      modelConfig: ModelConfig,
      listeners: Seq[ProcessListener],
      classLoader: ClassLoader
  ): FlinkExceptionHandler = {
    new TestFlinkExceptionHandler(metaData, modelConfig, listeners, classLoader)
  }

}

class TestSourceComponentImplementationInvoker(
    sourcePreparer: StubbedSourcePreparer,
    sourceFactory: ComponentDefinitionWithImplementation
) extends StubbedComponentImplementationInvoker(sourceFactory) {

  override def invokeOriginalInvoker(
      params: Params,
      compilationDependencies: NodeCompilationDependencies,
      invocationContext: Option[ComponentImplementationSpecificInvocationContext]
  ): Any = {
    // Transform EvaluableLazyParameterCreator's into EvaluableLazyParameter's
    // in order to have them available for resolving when executing tests
    val resolvedParams = Params.fromRawValuesMap(params.nameToRawValueMap.map { case (name, value) =>
      name -> resolveParam(value)
    })
    original.invokeMethod(resolvedParams, compilationDependencies, invocationContext)
  }

  override def transformOriginalInvocationResult(
      originalSource: Any,
      typingResult: TypingResult,
      compilationDependencies: NodeCompilationDependencies
  ): Any = {
    originalSource match {
      case sourceWithTestSupport: Source with FlinkSourceTestSupport[Object @unchecked] =>
        sourcePreparer.prepareStubbedSource(sourceWithTestSupport, typingResult, compilationDependencies.nodeId)
      case _ =>
        // We allow to
        EmptySource(typingResult)
    }
  }

  private def resolveParam(param: Any): Any = param match {
    case lazyParameterCreator: EvaluableLazyParameterCreator[_] =>
      sourcePreparer.resolveParam(lazyParameterCreator)
    case other =>
      other
  }

}

class TestFlinkExceptionHandler(
    metaData: MetaData,
    modelConfig: ModelConfig,
    listeners: Seq[ProcessListener],
    classLoader: ClassLoader
) extends FlinkExceptionHandler(metaData, modelConfig, listeners, classLoader) {

  @nowarn("cat=deprecation")
  override def restartStrategy: RestartStrategies.RestartStrategyConfiguration = RestartStrategies.noRestart()

  override val consumer: FlinkEspExceptionConsumer = _ => {}

}
