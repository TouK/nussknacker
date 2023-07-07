package pl.touk.nussknacker.engine.process.compiler

import com.typesafe.config.Config
import org.apache.flink.api.common.restartstrategy.RestartStrategies
import pl.touk.nussknacker.engine.api.dict.EngineDictRegistry
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.api.namespaces.ObjectNaming
import pl.touk.nussknacker.engine.api.process.{ComponentUseCase, ProcessConfigCreator, ProcessObjectDependencies}
import pl.touk.nussknacker.engine.api.{JobData, MetaData, ProcessListener, ProcessVersion}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compile._
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ModelDefinitionWithTypes
import pl.touk.nussknacker.engine.definition.{ProcessDefinitionExtractor, SubprocessComponentDefinitionExtractor}
import pl.touk.nussknacker.engine.dict.DictServicesFactoryLoader
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.graph.node.{CustomNode, NodeData}
import pl.touk.nussknacker.engine.process.async.DefaultAsyncExecutionConfigPreparer
import pl.touk.nussknacker.engine.process.exception.FlinkExceptionHandler
import pl.touk.nussknacker.engine.resultcollector.ResultCollector
import pl.touk.nussknacker.engine.util.LoggingListener
import pl.touk.nussknacker.engine.util.metrics.common.{EndCountingListener, NodeCountingListener}
import pl.touk.nussknacker.engine.{CustomProcessValidatorLoader, ModelData}

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
                           val componentUseCase: ComponentUseCase) extends Serializable {

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._

  def this(modelData: ModelData) = this(modelData.configCreator, modelData.processConfig, diskStateBackendSupport = true, modelData.objectNaming, componentUseCase = ComponentUseCase.EngineRuntime)

  def compileProcess(process: CanonicalProcess,
                     processVersion: ProcessVersion,
                     resultCollector: ResultCollector,
                     userCodeClassLoader: ClassLoader): FlinkProcessCompilerData =
    compileProcess(process, processVersion, resultCollector)(UsedNodes.empty, userCodeClassLoader)

  def compileProcess(process: CanonicalProcess,
                     processVersion: ProcessVersion,
                     resultCollector: ResultCollector)
                    (usedNodes: UsedNodes, userCodeClassLoader: ClassLoader): FlinkProcessCompilerData = {
    val processObjectDependencies = ProcessObjectDependencies(processConfig, objectNaming)

    //TODO: this should be somewhere else?
    val timeout = processConfig.as[FiniteDuration]("timeout")

    //TODO: should this be the default?
    val asyncExecutionContextPreparer = creator.asyncExecutionContextPreparer(processObjectDependencies).getOrElse(
      processConfig.as[DefaultAsyncExecutionConfigPreparer]("asyncExecutionConfig")
    )
    val defaultListeners = prepareDefaultListeners(usedNodes) ++ creator.listeners(processObjectDependencies)
    val listenersToUse = adjustListeners(defaultListeners, processObjectDependencies)

    val (definitionWithTypes, dictRegistry) = definitions(processObjectDependencies, userCodeClassLoader)
    val subprocessDefinitionExtractor = SubprocessComponentDefinitionExtractor(processConfig, userCodeClassLoader)
    val customProcessValidator = CustomProcessValidatorLoader.loadProcessValidators(userCodeClassLoader, processConfig)
    val compiledProcess =
      ProcessCompilerData.prepare(process, definitionWithTypes, dictRegistry, subprocessDefinitionExtractor, listenersToUse,
        userCodeClassLoader, resultCollector, componentUseCase, customProcessValidator)

    new FlinkProcessCompilerData(
      compiledProcess = compiledProcess,
      jobData = JobData(process.metaData, processVersion),
      exceptionHandler = exceptionHandler(process.metaData, processObjectDependencies, listenersToUse, userCodeClassLoader),
      asyncExecutionContextPreparer = asyncExecutionContextPreparer,
      processTimeout = timeout,
      componentUseCase = componentUseCase
    )
  }

  //TODO: should this be configurable somehow?
  private def prepareDefaultListeners(usedNodes: UsedNodes) = {
    //see comment in Interpreter.interpretNext
    //TODO: how should we handle branch?
    val nodesHandledInNextParts = (nodeData: NodeData) => nodeData.isInstanceOf[CustomNode] || nodeData.isInstanceOf[node.Sink]
    List(LoggingListener,
      new NodeCountingListener(usedNodes.nodes.filterNot(nodesHandledInNextParts).map(_.id) ++ usedNodes.nextParts),
      new EndCountingListener(usedNodes.nodes))
  }


  protected def definitions(processObjectDependencies: ProcessObjectDependencies, userCodeClassLoader: ClassLoader): (ModelDefinitionWithTypes, EngineDictRegistry) = {
    val dictRegistryFactory = loadDictRegistry(userCodeClassLoader)
    val modelDefinitionWithTypes = ModelDefinitionWithTypes(ProcessDefinitionExtractor.extractObjectWithMethods(creator, userCodeClassLoader, processObjectDependencies))
    val dictRegistry = dictRegistryFactory.createEngineDictRegistry(modelDefinitionWithTypes.modelDefinition.expressionConfig.dictionaries)
    (modelDefinitionWithTypes, dictRegistry)
  }

  private def loadDictRegistry(userCodeClassLoader: ClassLoader) = {
    // we are loading DictServicesFactory on TaskManager side. It may be tricky because of class loaders...
    DictServicesFactoryLoader.justOne(userCodeClassLoader)
  }



  protected def adjustListeners(defaults: List[ProcessListener], processObjectDependencies: ProcessObjectDependencies): List[ProcessListener] = defaults

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

//The logic of listeners invocation is a bit tricky for CustomNodes/Sinks (see Interpreter.interpretNode)
//ProcessListener.nodeEntered method is invoked in Interpreter, when PartRef is encountered. This is because CustomNode/Sink
//in Interpreter is invoked *after* node execution in Flink/Lite. To initialize Metric listeners correctly we need
//to pass list of nextParts, so when Interpreter invokes nodeEntered on PartRef, the counter is properly initialized
private[process] case class UsedNodes(nodes: Iterable[NodeData], nextParts: Iterable[String])

object UsedNodes {
  val empty: UsedNodes = UsedNodes(Nil, Nil)
}
