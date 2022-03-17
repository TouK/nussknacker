package pl.touk.nussknacker.engine.process.compiler

import com.typesafe.config.Config
import org.apache.flink.api.common.restartstrategy.RestartStrategies
import pl.touk.nussknacker.engine.api.async.{DefaultAsyncInterpretationValue, DefaultAsyncInterpretationValueDeterminer}
import pl.touk.nussknacker.engine.api.namespaces.ObjectNaming
import pl.touk.nussknacker.engine.api.process.{ComponentUseCase, ProcessConfigCreator, ProcessObjectDependencies}
import pl.touk.nussknacker.engine.api.{JobData, MetaData, ProcessListener, ProcessVersion}
import pl.touk.nussknacker.engine.compile._
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectWithMethodDef
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ProcessDefinition
import pl.touk.nussknacker.engine.flink.api.process.{FlinkProcessSignalSenderProvider, SignalSenderKey}
import pl.touk.nussknacker.engine.flink.api.signal.FlinkProcessSignalSender
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.util.LoggingListener
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.component.ComponentProvider.ComponentProviders
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.process.async.DefaultAsyncExecutionConfigPreparer
import pl.touk.nussknacker.engine.process.exception.FlinkExceptionHandler
import pl.touk.nussknacker.engine.resultcollector.ResultCollector
import pl.touk.nussknacker.engine.util.metrics.common.{EndCountingListener, NodeCountingListener}

import scala.concurrent.duration.FiniteDuration

/*
  This class prepares (in compile method) various objects needed to run process part on one Flink operator.

  Instances of this class is serialized in Flink Job graph, on jobmanager etc. Every field of this class need to be serializable
  That's why we struggle to keep parameters as small as possible and we have InputConfigDuringExecution with ModelConfigLoader and not whole config.
  In case of serialization problems add -Dsun.io.serialization.extendedDebugInfo=true to VM parameters.
 */
class FlinkProcessCompiler(creator: ProcessConfigCreator,
                           val processConfig: Config,
                           val diskStateBackendSupport: Boolean,
                           objectNaming: ObjectNaming,
                           val componentUseCase: ComponentUseCase,
                           providers: ComponentProviders) extends Serializable {

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import pl.touk.nussknacker.engine.util.Implicits._

  def this(modelData: ModelData) = this(
    modelData.configCreator,
    modelData.processConfig,
    diskStateBackendSupport = true,
    modelData.objectNaming,
    componentUseCase = ComponentUseCase.EngineRuntime,
    modelData.componentProviders)

  def compileProcess(process: EspProcess,
                     processVersion: ProcessVersion,
                     deploymentData: DeploymentData,
                     resultCollector: ResultCollector)
                    (userCodeClassLoader: ClassLoader): FlinkProcessCompilerData = {
    val processObjectDependencies = ProcessObjectDependencies(processConfig, objectNaming)

    //TODO: this should be somewhere else?
    val timeout = processConfig.as[FiniteDuration]("timeout")

    //TODO: should this be the default?
    val asyncExecutionContextPreparer = creator.asyncExecutionContextPreparer(processObjectDependencies).getOrElse(
      processConfig.as[DefaultAsyncExecutionConfigPreparer]("asyncExecutionConfig")
    )
    implicit val defaultAsync: DefaultAsyncInterpretationValue = DefaultAsyncInterpretationValueDeterminer.determine(asyncExecutionContextPreparer)

    val listenersToUse = listeners(processObjectDependencies)

    val processDefinition = definitions(processObjectDependencies)
    val compiledProcess =
      ProcessCompilerData.prepare(process, processDefinition, listenersToUse, userCodeClassLoader, resultCollector, componentUseCase)

    new FlinkProcessCompilerData(
      compiledProcess = compiledProcess,
      jobData = JobData(process.metaData, processVersion),
      exceptionHandler = exceptionHandler(process.metaData, processObjectDependencies, listenersToUse, userCodeClassLoader),
      signalSenders = new FlinkProcessSignalSenderProvider(signalSenders(processDefinition)),
      asyncExecutionContextPreparer = asyncExecutionContextPreparer,
      processTimeout = timeout,
      componentUseCase = componentUseCase
    )
  }

  protected def definitions(processObjectDependencies: ProcessObjectDependencies): ProcessDefinition[ObjectWithMethodDef] = {
    ProcessDefinitionExtractor.extractObjectWithMethods(creator, processObjectDependencies, providers)
  }

  protected def listeners(processObjectDependencies: ProcessObjectDependencies): Seq[ProcessListener] = {
    //TODO: should this be configurable somehow?
    List(LoggingListener, new NodeCountingListener, new EndCountingListener) ++ creator.listeners(processObjectDependencies)
  }

  protected def signalSenders(processDefinition: ProcessDefinition[ObjectWithMethodDef]): Map[SignalSenderKey, FlinkProcessSignalSender]
  = processDefinition.signalsWithTransformers.mapValuesNow(_._1.obj.asInstanceOf[FlinkProcessSignalSender])
    .map { case (k, v) => SignalSenderKey(k, v.getClass) -> v }

  protected def exceptionHandler(metaData: MetaData,
                                 processObjectDependencies: ProcessObjectDependencies,
                                 listeners: Seq[ProcessListener],
                                 classLoader: ClassLoader): FlinkExceptionHandler = {
    componentUseCase match {
      case ComponentUseCase.TestRuntime =>
        new FlinkExceptionHandler(metaData, processObjectDependencies, listeners, classLoader) {
          override def restartStrategy: RestartStrategies.RestartStrategyConfiguration = RestartStrategies.noRestart()

          override def handle(exceptionInfo: NuExceptionInfo[_ <: Throwable]): Unit = ()
        }
      case _ => new FlinkExceptionHandler(metaData, processObjectDependencies, listeners, classLoader)
    }
  }
}
