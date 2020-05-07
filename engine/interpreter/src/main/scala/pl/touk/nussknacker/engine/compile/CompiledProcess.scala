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
import pl.touk.nussknacker.engine.graph.node.{Enricher, Processor}
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.split.{NodesCollector, ProcessSplitter}
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer

import scala.collection.immutable
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

      //when using lazyVals we're not sure what services will be used, if we don't use them
      //we can open/close only ones that will be used
      val servicesToUse = if (process.metaData.typeSpecificData.allowLazyVars) servicesDefs else servicesDefs.filterKeys(findUsedServices(process).contains)

      val expressionEvaluator = ExpressionEvaluator.optimizedEvaluator(globalVariablesPreparer, listeners, process.metaData, servicesToUse)
      val interpreter = Interpreter(listeners, expressionEvaluator)

      CompiledProcess(
        compiledProcess,
        subCompiler,
        LazyInterpreterDependencies(expressionEvaluator, expressionCompiler, FiniteDuration(10, TimeUnit.SECONDS)),
        interpreter,
        listeners ++ servicesToUse.values.map(_.obj.asInstanceOf[Lifecycle]) :+ compiledProcess.exceptionHandler
      )

    }
  }

  private def findUsedServices(process: EspProcess): immutable.Seq[String] = {
    val nodes = NodesCollector.collectNodesInAllParts(ProcessSplitter.split(process).sources)
    val serviceIds = nodes.map(_.data).collect {
      case Enricher(_, ServiceRef(id, _), _, _) => id
      case Processor(_, ServiceRef(id, _), _, _) => id
    }
    serviceIds
  }

  private def loadDictRegistry(userCodeClassLoader: ClassLoader) = {
    // we are loading DictServicesFactory on TaskManager side. It may be tricky because of class loaders...
    DictServicesFactoryLoader.justOne(userCodeClassLoader)
  }

}

case class CompiledProcess(parts: CompiledProcessParts,
                           subPartCompiler: PartSubGraphCompiler,
                           lazyInterpreterDeps: LazyInterpreterDependencies,
                           interpreter: Interpreter, lifecycle: Seq[Lifecycle]) {

  def close(): Unit = {
    lifecycle.foreach(_.close())
  }

}
