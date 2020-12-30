package pl.touk.nussknacker.engine.compile

import java.util.concurrent.TimeUnit

import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.Interpreter
import pl.touk.nussknacker.engine.api.async.DefaultAsyncInterpretationValue
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.{Lifecycle, ProcessListener}
import pl.touk.nussknacker.engine.compile.nodecompilation.NodeCompiler
import pl.touk.nussknacker.engine.compiledgraph.CompiledProcessParts
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectWithMethodDef
import pl.touk.nussknacker.engine.definition.LazyInterpreterDependencies
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ProcessDefinition
import pl.touk.nussknacker.engine.dict.DictServicesFactoryLoader
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer

import scala.concurrent.duration.FiniteDuration

object CompiledProcess {

  def compile(process: EspProcess,
              definitions: ProcessDefinition[ObjectWithMethodDef],
              listeners: Seq[ProcessListener],
              userCodeClassLoader: ClassLoader
             )(implicit defaultAsyncValue: DefaultAsyncInterpretationValue): ValidatedNel[ProcessCompilationError, CompiledProcess] = {
    val servicesDefs = definitions.services

    val dictRegistryFactory = loadDictRegistry(userCodeClassLoader)
    val dictRegistry = dictRegistryFactory.createEngineDictRegistry(definitions.expressionConfig.dictionaries)

    val expressionCompiler = ExpressionCompiler.withOptimization(userCodeClassLoader, dictRegistry, definitions.expressionConfig, definitions.settings)
    //for testing environment it's important to take classloader from user jar
    val nodeCompiler = new NodeCompiler(definitions, expressionCompiler, userCodeClassLoader)
    val subCompiler = new PartSubGraphCompiler(expressionCompiler, nodeCompiler)
    val processCompiler = new ProcessCompiler(userCodeClassLoader, subCompiler, GlobalVariablesPreparer(definitions.expressionConfig), nodeCompiler)

    processCompiler.compile(process).result.map { compiledProcess =>
      val globalVariablesPreparer = GlobalVariablesPreparer(definitions.expressionConfig)

      val expressionEvaluator = ExpressionEvaluator.optimizedEvaluator(globalVariablesPreparer, listeners, process.metaData)

      val interpreter = Interpreter(listeners, expressionEvaluator)

      CompiledProcess(
        compiledProcess,
        subCompiler,
        LazyInterpreterDependencies(expressionEvaluator, expressionCompiler, FiniteDuration(10, TimeUnit.SECONDS)),
        interpreter,
        listeners ++ servicesDefs.values.map(_.obj.asInstanceOf[Lifecycle])
      )

    }
  }

  private def loadDictRegistry(userCodeClassLoader: ClassLoader) = {
    // we are loading DictServicesFactory on TaskManager side. It may be tricky because of class loaders...
    DictServicesFactoryLoader.justOne(userCodeClassLoader)
  }

}

case class CompiledProcess(parts: CompiledProcessParts,
                           subPartCompiler: PartSubGraphCompiler,
                           lazyInterpreterDeps: LazyInterpreterDependencies,
                           interpreter: Interpreter,
                           lifecycle: Seq[Lifecycle])
