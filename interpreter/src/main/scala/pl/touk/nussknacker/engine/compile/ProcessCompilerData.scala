package pl.touk.nussknacker.engine.compile

import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.api.component.ComponentType
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.dict.EngineDictRegistry
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.api.{Lifecycle, ProcessListener}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compile.nodecompilation.{LazyInterpreterDependencies, NodeCompiler}
import pl.touk.nussknacker.engine.compiledgraph.CompiledProcessParts
import pl.touk.nussknacker.engine.definition.fragment.FragmentParametersCompleteDefinitionExtractor
import pl.touk.nussknacker.engine.definition.model.ModelDefinitionWithClasses
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
      definitionWithTypes: ModelDefinitionWithClasses,
      dictRegistry: EngineDictRegistry,
      listeners: Seq[ProcessListener],
      userCodeClassLoader: ClassLoader,
      resultsCollector: ResultCollector,
      componentUseCase: ComponentUseCase,
      customProcessValidator: CustomProcessValidator
  ): ProcessCompilerData = {
    val servicesDefs = definitionWithTypes.modelDefinition.components
      .filter(_._1.`type` == ComponentType.Service)

    val expressionCompiler = ExpressionCompiler.withOptimization(
      userCodeClassLoader,
      dictRegistry,
      definitionWithTypes.modelDefinition.expressionConfig,
      definitionWithTypes.classDefinitions
    )
    val fragmentParametersDefinitionExtractor = FragmentParametersCompleteDefinitionExtractor(
      userCodeClassLoader,
      expressionCompiler
    )

    // for testing environment it's important to take classloader from user jar
    val nodeCompiler = new NodeCompiler(
      definitionWithTypes.modelDefinition,
      fragmentParametersDefinitionExtractor,
      expressionCompiler,
      userCodeClassLoader,
      resultsCollector,
      componentUseCase
    )
    val subCompiler = new PartSubGraphCompiler(expressionCompiler, nodeCompiler)
    val processCompiler = new ProcessCompiler(
      userCodeClassLoader,
      subCompiler,
      GlobalVariablesPreparer(definitionWithTypes.modelDefinition.expressionConfig),
      nodeCompiler,
      customProcessValidator
    )

    val globalVariablesPreparer = GlobalVariablesPreparer(definitionWithTypes.modelDefinition.expressionConfig)

    val expressionEvaluator =
      ExpressionEvaluator.optimizedEvaluator(globalVariablesPreparer, listeners)

    val interpreter = Interpreter(listeners, expressionEvaluator, componentUseCase)

    new ProcessCompilerData(
      processCompiler,
      subCompiler,
      LazyInterpreterDependencies(expressionEvaluator, expressionCompiler, FiniteDuration(10, TimeUnit.SECONDS)),
      interpreter,
      listeners,
      servicesDefs.map { case (info, servicesDef) => info.name -> servicesDef.implementation.asInstanceOf[Lifecycle] }
    )

  }

}

final class ProcessCompilerData(
    compiler: ProcessCompiler,
    val subPartCompiler: PartSubGraphCompiler,
    val lazyInterpreterDeps: LazyInterpreterDependencies,
    val interpreter: Interpreter,
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

  def compile(process: CanonicalProcess): ValidatedNel[ProcessCompilationError, CompiledProcessParts] =
    compiler.compile(process).result
}
