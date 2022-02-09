package pl.touk.nussknacker.engine.compile

import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.api.async.DefaultAsyncInterpretationValue
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.api.{Lifecycle, MetaData, ProcessListener}
import pl.touk.nussknacker.engine.compile.nodecompilation.NodeCompiler
import pl.touk.nussknacker.engine.compiledgraph.CompiledProcessParts
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectWithMethodDef
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ProcessDefinition
import pl.touk.nussknacker.engine.definition.{LazyInterpreterDependencies, ProcessDefinitionExtractor}
import pl.touk.nussknacker.engine.dict.DictServicesFactoryLoader
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.node.{NodeData, WithComponent}
import pl.touk.nussknacker.engine.resultcollector.ResultCollector
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer
import pl.touk.nussknacker.engine.{Interpreter, TypeDefinitionSet}

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

/*
  This is helper class, which collects pieces needed for various stages of compilation process

 */
object ProcessCompilerData {

  def prepare(process: EspProcess,
              definitions: ProcessDefinition[ObjectWithMethodDef],
              listeners: Seq[ProcessListener],
              userCodeClassLoader: ClassLoader,
              resultsCollector: ResultCollector,
              componentUseCase: ComponentUseCase
             )(implicit defaultAsyncValue: DefaultAsyncInterpretationValue): ProcessCompilerData = {
    val servicesDefs = definitions.services

    val dictRegistryFactory = loadDictRegistry(userCodeClassLoader)
    val dictRegistry = dictRegistryFactory.createEngineDictRegistry(definitions.expressionConfig.dictionaries)

    val typeDefinitionSet = TypeDefinitionSet(ProcessDefinitionExtractor.extractTypes(definitions))
    val expressionCompiler = ExpressionCompiler.withOptimization(userCodeClassLoader, dictRegistry, definitions.expressionConfig, definitions.settings, typeDefinitionSet)
    //for testing environment it's important to take classloader from user jar
    val nodeCompiler = new NodeCompiler(definitions, expressionCompiler, userCodeClassLoader, resultsCollector, componentUseCase)
    val subCompiler = new PartSubGraphCompiler(expressionCompiler, nodeCompiler)
    val processCompiler = new ProcessCompiler(userCodeClassLoader, subCompiler, GlobalVariablesPreparer(definitions.expressionConfig), nodeCompiler)

    val globalVariablesPreparer = GlobalVariablesPreparer(definitions.expressionConfig)

    val expressionEvaluator = ExpressionEvaluator.optimizedEvaluator(globalVariablesPreparer, listeners, process.metaData)

    val interpreter = Interpreter(listeners, expressionEvaluator, componentUseCase)

    new ProcessCompilerData(
      processCompiler,
      subCompiler,
      nodeCompiler,
      LazyInterpreterDependencies(expressionEvaluator, expressionCompiler, FiniteDuration(10, TimeUnit.SECONDS)),
      interpreter,
      process,
      listeners,
      servicesDefs.mapValues(_.obj.asInstanceOf[Lifecycle])
    )

  }

  private def loadDictRegistry(userCodeClassLoader: ClassLoader) = {
    // we are loading DictServicesFactory on TaskManager side. It may be tricky because of class loaders...
    DictServicesFactoryLoader.justOne(userCodeClassLoader)
  }

}

class ProcessCompilerData(compiler: ProcessCompiler,
                          val subPartCompiler: PartSubGraphCompiler,
                          nodeCompiler: NodeCompiler,
                          val lazyInterpreterDeps: LazyInterpreterDependencies,
                          val interpreter: Interpreter,
                          process: EspProcess,
                          val listeners: Seq[ProcessListener],
                          services: Map[String, Lifecycle]) {

  def lifecycle(nodesToUse: List[_ <: NodeData]): Seq[Lifecycle] = {
    val componentIds = nodesToUse.collect {
      case e:WithComponent => e.componentId
    }
    listeners ++ services.filterKeys(componentIds.contains).values
  }

  def metaData: MetaData = process.metaData

  def compile(): ValidatedNel[ProcessCompilationError, CompiledProcessParts] =
    compiler.compile(process).result
}
