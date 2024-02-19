package pl.touk.nussknacker.engine.compile

import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.api.component.ComponentType
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.dict.EngineDictRegistry
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.api.{Lifecycle, ProcessListener}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compile.nodecompilation.{LazyParameterCreationStrategy, NodeCompiler}
import pl.touk.nussknacker.engine.compiledgraph.CompiledProcessParts
import pl.touk.nussknacker.engine.definition.fragment.FragmentParametersDefinitionExtractor
import pl.touk.nussknacker.engine.definition.model.ModelDefinitionWithClasses
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.graph.node.{NodeData, WithComponent}
import pl.touk.nussknacker.engine.resultcollector.ResultCollector
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer
import pl.touk.nussknacker.engine.{CustomProcessValidator, Interpreter}

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
      customProcessValidator: CustomProcessValidator,
      nonServicesLazyParamStrategy: LazyParameterCreationStrategy = LazyParameterCreationStrategy.default
  ): ProcessCompilerData = {
    val servicesDefs = definitionWithTypes.modelDefinition.components
      .filter(_.componentType == ComponentType.Service)

    val expressionCompiler = ExpressionCompiler.withOptimization(
      userCodeClassLoader,
      dictRegistry,
      definitionWithTypes.modelDefinition.expressionConfig,
      definitionWithTypes.classDefinitions
    )

    // for testing environment it's important to take classloader from user jar
    val nodeCompiler = new NodeCompiler(
      definitionWithTypes.modelDefinition,
      new FragmentParametersDefinitionExtractor(userCodeClassLoader),
      expressionCompiler,
      userCodeClassLoader,
      listeners,
      resultsCollector,
      componentUseCase,
      nonServicesLazyParamStrategy
    )
    val subCompiler             = new PartSubGraphCompiler(nodeCompiler)
    val globalVariablesPreparer = GlobalVariablesPreparer(definitionWithTypes.modelDefinition.expressionConfig)
    val processCompiler = new ProcessCompiler(
      userCodeClassLoader,
      subCompiler,
      globalVariablesPreparer,
      nodeCompiler,
      customProcessValidator
    )
    val expressionEvaluator =
      ExpressionEvaluator.optimizedEvaluator(globalVariablesPreparer, listeners)

    val interpreter = Interpreter(listeners, expressionEvaluator, componentUseCase)

    new ProcessCompilerData(
      processCompiler,
      subCompiler,
      expressionCompiler,
      expressionEvaluator,
      interpreter,
      listeners,
      servicesDefs.map(service => service.name -> service.implementation.asInstanceOf[Lifecycle]).toMap
    )

  }

}

final class ProcessCompilerData(
    compiler: ProcessCompiler,
    val subPartCompiler: PartSubGraphCompiler,
    val expressionCompiler: ExpressionCompiler,
    val expressionEvaluator: ExpressionEvaluator,
    val interpreter: Interpreter,
    val listeners: Seq[ProcessListener],
    services: Map[String, Lifecycle]
) {

  def lifecycle(nodesToUse: List[_ <: NodeData]): Seq[Lifecycle] = {
    val componentIds = nodesToUse.collect { case e: WithComponent =>
      e.componentId
    }
    // TODO: For eager services we should open service implementation (ServiceInvoker) which is hold inside
    //       SyncInterpretationFunction.compiledNode inside ServiceRef instead of definition (DynamicComponent)
    //       Definition shouldn't be used after component is compiled. Thanks to that it will be possible to
    //       e.g. to pass ExecutionContext inside EngineRuntimeContext and to separate implementation from definition
    val servicesToUse = services.filterKeysNow(componentIds.contains).values
    listeners ++ servicesToUse
  }

  def compile(process: CanonicalProcess): ValidatedNel[ProcessCompilationError, CompiledProcessParts] =
    compiler.compile(process).result
}
