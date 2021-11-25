package pl.touk.nussknacker.engine.process.compiler

import com.typesafe.config.Config
import org.apache.flink.api.common.restartstrategy.RestartStrategies
import pl.touk.nussknacker.engine.api.async.{DefaultAsyncInterpretationValue, DefaultAsyncInterpretationValueDeterminer}
import pl.touk.nussknacker.engine.api.namespaces.ObjectNaming
import pl.touk.nussknacker.engine.api.process.{ProcessConfigCreator, ProcessObjectDependencies, RunMode}
import pl.touk.nussknacker.engine.api.{JobData, MetaData, ProcessListener, ProcessVersion}
import pl.touk.nussknacker.engine.compile._
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectWithMethodDef
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ProcessDefinition
import pl.touk.nussknacker.engine.flink.api.exception.ConfigurableExceptionHandler
import pl.touk.nussknacker.engine.flink.api.process.{FlinkProcessSignalSenderProvider, SignalSenderKey}
import pl.touk.nussknacker.engine.flink.api.signal.FlinkProcessSignalSender
import pl.touk.nussknacker.engine.flink.util.async.DefaultAsyncExecutionConfigPreparer
import pl.touk.nussknacker.engine.flink.util.listener.NodeCountMetricListener
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.util.LoggingListener
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.deployment.DeploymentData
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.resultcollector.ResultCollector

import scala.concurrent.duration.FiniteDuration

/*
  This class prepares (in compile method) various objects needed to run process part on one Flink operator.

  Instances of this class is serialized in Flink Job graph, on jobmanager etc. That's why we struggle to keep parameters as small as possible
  and we have InputConfigDuringExecution with ModelConfigLoader and not whole config.
 */
class FlinkProcessCompiler(creator: ProcessConfigCreator,
                           val processConfig: Config,
                           val diskStateBackendSupport: Boolean,
                           objectNaming: ObjectNaming,
                           val runMode: RunMode) extends Serializable {

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import pl.touk.nussknacker.engine.util.Implicits._

  def this(modelData: ModelData) = this(modelData.configCreator, modelData.processConfig, diskStateBackendSupport = true, modelData.objectNaming, runMode = RunMode.Normal)

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

    val compiledProcess =
      ProcessCompilerData.prepare(process, definitions(processObjectDependencies), listenersToUse, userCodeClassLoader, resultCollector, runMode)

    new FlinkProcessCompilerData(
      compiledProcess = compiledProcess,
      jobData = JobData(process.metaData, processVersion, deploymentData),
      exceptionHandler = exceptionHandler(process.metaData, processObjectDependencies, listenersToUse, userCodeClassLoader),
      signalSenders = new FlinkProcessSignalSenderProvider(signalSenders(processObjectDependencies)),
      asyncExecutionContextPreparer = asyncExecutionContextPreparer,
      processTimeout = timeout,
      runMode = runMode
    )
  }

  protected def definitions(processObjectDependencies: ProcessObjectDependencies): ProcessDefinition[ObjectWithMethodDef] = {
    ProcessDefinitionExtractor.extractObjectWithMethods(creator, processObjectDependencies)
  }

  protected def listeners(processObjectDependencies: ProcessObjectDependencies): Seq[ProcessListener] = {
    //TODO: should this be configurable somehow?
    //if it's configurable, it also has to affect NodeCountMetricFunction!
    List(LoggingListener, new NodeCountMetricListener) ++ creator.listeners(processObjectDependencies)
  }

  protected def signalSenders(processObjectDependencies: ProcessObjectDependencies): Map[SignalSenderKey, FlinkProcessSignalSender]
    = definitions(processObjectDependencies).signalsWithTransformers.mapValuesNow(_._1.obj.asInstanceOf[FlinkProcessSignalSender])
      .map { case (k, v) => SignalSenderKey(k, v.getClass) -> v }

  protected def exceptionHandler(metaData: MetaData,
                                 processObjectDependencies: ProcessObjectDependencies,
                                 listeners: Seq[ProcessListener],
                                 classLoader: ClassLoader): ConfigurableExceptionHandler = {
    runMode match {
      case RunMode.Normal =>
        new ConfigurableExceptionHandler(metaData, processObjectDependencies, listeners, classLoader)
      case RunMode.Test =>
        new ConfigurableExceptionHandler(metaData, processObjectDependencies, listeners, classLoader) {
          override def restartStrategy: RestartStrategies.RestartStrategyConfiguration = RestartStrategies.noRestart()
          override def handle(exceptionInfo: NuExceptionInfo[_ <: Throwable]): Unit = ()
        }
    }
  }
}
