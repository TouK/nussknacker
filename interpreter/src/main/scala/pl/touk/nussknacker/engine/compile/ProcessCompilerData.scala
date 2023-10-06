package pl.touk.nussknacker.engine.compile

import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.dict.EngineDictRegistry
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.api.{Lifecycle, MetaData, ProcessListener}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compile.nodecompilation.NodeCompiler
import pl.touk.nussknacker.engine.compiledgraph.CompiledProcessParts
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ModelDefinitionWithTypes
import pl.touk.nussknacker.engine.definition.{FragmentComponentDefinitionExtractor, LazyInterpreterDependencies}
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.graph.node.{NodeData, WithComponent}
import pl.touk.nussknacker.engine.resultcollector.ResultCollector
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer
import pl.touk.nussknacker.engine.{CustomProcessValidator, Interpreter}

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

/*
  This is helper class, which collects pieces needed for various stages of compilation process

 */
object ProcessCompilerData {

  def prepare(
      process: CanonicalProcess,
      definitionWithTypes: ModelDefinitionWithTypes,
      dictRegistry: EngineDictRegistry,
      fragmentDefinitionExtractor: FragmentComponentDefinitionExtractor,
      listeners: Seq[ProcessListener],
      userCodeClassLoader: ClassLoader,
      resultsCollector: ResultCollector,
      componentUseCase: ComponentUseCase,
      customProcessValidator: CustomProcessValidator
  ): ProcessCompilerData = {
    import definitionWithTypes.modelDefinition
    val servicesDefs = modelDefinition.services

    val expressionCompiler = ExpressionCompiler.withOptimization(
      userCodeClassLoader,
      dictRegistry,
      modelDefinition.expressionConfig,
      definitionWithTypes.typeDefinitions
    )
    // for testing environment it's important to take classloader from user jar
    val nodeCompiler = new NodeCompiler(
      modelDefinition,
      fragmentDefinitionExtractor,
      expressionCompiler,
      userCodeClassLoader,
      resultsCollector,
      componentUseCase
    )
    val subCompiler = new PartSubGraphCompiler(expressionCompiler, nodeCompiler)
    val processCompiler = new ProcessCompiler(
      userCodeClassLoader,
      subCompiler,
      GlobalVariablesPreparer(modelDefinition.expressionConfig),
      nodeCompiler,
      customProcessValidator
    )

    val globalVariablesPreparer = GlobalVariablesPreparer(modelDefinition.expressionConfig)

    val expressionEvaluator =
      ExpressionEvaluator.optimizedEvaluator(globalVariablesPreparer, listeners, process.metaData)

    val interpreter = Interpreter(listeners, expressionEvaluator, componentUseCase)

    new ProcessCompilerData(
      processCompiler,
      subCompiler,
      LazyInterpreterDependencies(expressionEvaluator, expressionCompiler, FiniteDuration(10, TimeUnit.SECONDS)),
      interpreter,
      process,
      listeners,
      servicesDefs.mapValuesNow(_.obj.asInstanceOf[Lifecycle])
    )

  }

}

class ProcessCompilerData(
    compiler: ProcessCompiler,
    val subPartCompiler: PartSubGraphCompiler,
    val lazyInterpreterDeps: LazyInterpreterDependencies,
    val interpreter: Interpreter,
    process: CanonicalProcess,
    val listeners: Seq[ProcessListener],
    services: Map[String, Lifecycle]
) {

  def lifecycle(nodesToUse: List[_ <: NodeData]): Seq[Lifecycle] = {
    val componentIds = nodesToUse.collect { case e: WithComponent =>
      e.componentId
    }
    // TODO: For eager services we should open service implementation (ServiceInvoker) which is hold inside
    //       SyncInterpretationFunction.compiledNode inside ServiceRef instead of definition (GenericNodeTransformation)
    //       Definition shouldn't be used after component is compiled. Thanks to that it will be possible to
    //       e.g. to pass ExecutionContext inside EngineRuntimeContext and to separate implementation from definition
    val servicesToUse = services.filterKeysNow(componentIds.contains).values
    listeners ++ servicesToUse
  }

  def metaData: MetaData = process.metaData

  def compile(): ValidatedNel[ProcessCompilationError, CompiledProcessParts] =
    compiler.compile(process).result
}
