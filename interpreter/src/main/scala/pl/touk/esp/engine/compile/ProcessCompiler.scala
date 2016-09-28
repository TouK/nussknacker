package pl.touk.esp.engine.compile

import cats.data.Validated._
import cats.data.{Validated, ValidatedNel}
import cats.instances.list._
import cats.instances.option._
import pl.touk.esp.engine._
import pl.touk.esp.engine.api.exception.{EspExceptionHandler, ExceptionHandlerFactory}
import pl.touk.esp.engine.api.process._
import pl.touk.esp.engine.api.{CustomStreamTransformer, FoldingFunction, MetaData}
import pl.touk.esp.engine.canonicalgraph.CanonicalProcess
import pl.touk.esp.engine.canonize.ProcessCanonizer
import pl.touk.esp.engine.compile.ProcessCompilationError._
import pl.touk.esp.engine.compile.dumb._
import pl.touk.esp.engine.compiledgraph.CompiledProcessParts
import pl.touk.esp.engine.definition.DefinitionExtractor._
import pl.touk.esp.engine.definition.ProcessDefinitionExtractor.ProcessDefinition
import pl.touk.esp.engine.definition.{ProcessObjectDefinitionExtractor, _}
import pl.touk.esp.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.esp.engine.graph.sink.SinkRef
import pl.touk.esp.engine.graph.source.SourceRef
import pl.touk.esp.engine.graph.{EspProcess, param}
import pl.touk.esp.engine.split._
import pl.touk.esp.engine.splittedgraph._
import pl.touk.esp.engine.splittedgraph.part._
import pl.touk.esp.engine.splittedgraph.splittednode.SplittedNode

class ProcessCompiler(protected val sub: PartSubGraphCompilerBase,
                      protected val sourceFactories: Map[String, ObjectWithMethodDef],
                      protected val sinkFactories: Map[String, ObjectWithMethodDef],
                      protected val foldingFunctions: Map[String, FoldingFunction[Any]],
                      protected val customStreamTransformers: Map[String, ObjectWithMethodDef],
                      protected val exceptionHandlerFactory: ObjectWithMethodDef) extends ProcessCompilerBase {

  override type ParameterProviderT = ObjectWithMethodDef

  override def compile(process: EspProcess): ValidatedNel[ProcessCompilationError, CompiledProcessParts] = {
    super.compile(process)
  }

  override protected def createCustomNodeInvoker(obj: ObjectWithMethodDef, metaData: MetaData, node: SplittedNode[graph.node.CustomNode]) =
    CustomNodeInvoker[Any](obj, metaData, node)

  override protected def createFactory[T](obj: ObjectWithMethodDef) =
    ProcessObjectFactory[T](obj)

}

class ProcessValidator(protected val sub: PartSubGraphCompilerBase,
                       protected val sourceFactories: Map[String, ObjectDefinition],
                       protected val sinkFactories: Map[String, ObjectDefinition],
                       protected val foldingFunctions: Map[String, FoldingFunction[Any]],
                       protected val customStreamTransformers: Map[String, ObjectDefinition],
                       protected val exceptionHandlerFactory: ObjectDefinition) extends ProcessCompilerBase {

  override type ParameterProviderT = ObjectDefinition

  override protected def createFactory[T](obj: ObjectDefinition) =
    new DumbProcessObjectFactory[T]

  override protected def createCustomNodeInvoker(obj: ObjectDefinition, metaData: MetaData, node: SplittedNode[graph.node.CustomNode]) =
    new DumbCustomNodeInvoker[Any]
}

protected trait ProcessCompilerBase {

  type ParameterProviderT <: ParametersProvider

  protected def sourceFactories: Map[String, ParameterProviderT]
  protected def sinkFactories: Map[String, ParameterProviderT]
  protected def foldingFunctions: Map[String, FoldingFunction[Any]]
  protected def exceptionHandlerFactory: ParameterProviderT
  protected val customStreamTransformers: Map[String, ParameterProviderT]

  protected def sub: PartSubGraphCompilerBase

  private val syntax = ValidatedSyntax[ProcessCompilationError]
  import syntax._

  def validate(canonical: CanonicalProcess): ValidatedNel[ProcessCompilationError, Unit] = {
    ProcessCanonizer.uncanonize(canonical).leftMap(_.map(identity[ProcessCompilationError])) andThen { process =>
      validate(process)
    }
  }

  def validate(process: EspProcess): ValidatedNel[ProcessCompilationError, Unit] = {
    compile(process).map(_ => Unit)
  }

  protected def compile(process: EspProcess): ValidatedNel[ProcessCompilationError, CompiledProcessParts] = {
    compile(ProcessSplitter.split(process))
  }

  private def compile(splittedProcess: SplittedProcess): ValidatedNel[ProcessCompilationError, CompiledProcessParts] = {
    implicit val metaData = splittedProcess.metaData
    A.map3(
      findDuplicates(splittedProcess.source).toValidatedNel,
      compile(splittedProcess.exceptionHandlerRef),
      compile(splittedProcess.source)
    ) { (_, exceptionHandler, source) =>
      CompiledProcessParts(splittedProcess.metaData, exceptionHandler, source)
    }
  }

  private def findDuplicates(part: SourcePart): Validated[ProcessCompilationError, Unit] = {
    val allNodes = NodesCollector.collectNodesInAllParts(part)
    val duplicatedIds =
      allNodes.map(_.id).groupBy(identity).collect {
        case (id, grouped) if grouped.size > 1 =>
          id
      }
    if (duplicatedIds.isEmpty)
      valid(Unit)
    else
      invalid(DuplicatedNodeIds(duplicatedIds.toSet))
  }

  private def compile(part: SubsequentPart)
                     (implicit metaData: MetaData): ValidatedNel[ProcessCompilationError, compiledgraph.part.SubsequentPart] = {
    implicit val nodeId = NodeId(part.id)
    part match {
      case AggregatePart(node, nextParts, ends) =>
        A.map3(
          validate(node),
          node.data.foldingFunRef.map(compileFoldingFunction).sequence,
          compile(nextParts)
        ) { (_, foldingFunRefV, nextPartsV) =>
          compiledgraph.part.AggregatePart(foldingFunRefV, node, nextPartsV, ends)
        }
      case SinkPart(node) =>
        A.map2(
          validate(node),
          compile(node.data.ref)
        ) { (_, obj) =>
          compiledgraph.part.SinkPart(obj, node)
        }
      case CustomNodePart(node, nextParts, ends) =>
        A.map2(compileCustomNodeInvoker(node), compile(nextParts))(
          compiledgraph.part.CustomNodePart(_, node, _, ends)
        )
    }
  }

  private def compile(source: SourcePart)
                     (implicit metaData: MetaData): ValidatedNel[ProcessCompilationError, compiledgraph.part.SourcePart] = {
    implicit val nodeId = NodeId(source.id)
    A.map3(
      validate(source.node),
      compile(source.node.data.ref),
      compile(source.nextParts)
    ) { (_, obj, nextParts) =>
      compiledgraph.part.SourcePart(obj, source.node, nextParts, source.ends)
    }
  }

  private def compile(ref: ExceptionHandlerRef)
                      (implicit metaData: MetaData): ValidatedNel[ProcessCompilationError, EspExceptionHandler] = {
    implicit val nodeId = NodeId(ProcessCompilationError.ProcessNodeId)
    compileProcessObject[EspExceptionHandler](exceptionHandlerFactory, ref.parameters)
  }

  private def compile(ref: SourceRef)
                     (implicit nodeId: NodeId,
                      metaData: MetaData): ValidatedNel[ProcessCompilationError, api.process.Source[Any]] = {
    val validSourceFactory = sourceFactories.get(ref.typ).map(valid).getOrElse(invalid(MissingSourceFactory(ref.typ))).toValidatedNel
    validSourceFactory.andThen(sourceFactory => compileProcessObject[Source[Any]](sourceFactory, ref.parameters))
  }

  private def compile(ref: SinkRef)
                     (implicit nodeId: NodeId,
                      metaData: MetaData): ValidatedNel[ProcessCompilationError, api.process.Sink] = {
    val validSinkFactory = sinkFactories.get(ref.typ).map(valid).getOrElse(invalid(MissingSinkFactory(ref.typ))).toValidatedNel
    validSinkFactory.andThen(sinkFactory => compileProcessObject[Sink](sinkFactory, ref.parameters))
  }

  private def compileProcessObject[T](parameterProviderT: ParameterProviderT,
                                      parameters: List[param.Parameter])
                                     (implicit nodeId: NodeId,
                                      metaData: MetaData): ValidatedNel[ProcessCompilationError, T] = {
    validateParameters(parameterProviderT, parameters.map(_.name)).map { _ =>
      val factory = createFactory[T](parameterProviderT)
      factory.create(metaData, parameters)
    }
  }

  private def compileFoldingFunction(ref: String)
                                    (implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, FoldingFunction[Any]] = {
    foldingFunctions.get(ref).map(valid).getOrElse(invalid(MissingFoldingFunction(ref))).toValidatedNel
  }

  private def compileCustomNodeInvoker(node: SplittedNode[graph.node.CustomNode])
                                      (implicit nodeId: NodeId, metaData: MetaData): ValidatedNel[ProcessCompilationError, CustomNodeInvoker[Any]] = {
    val ref = node.data.nodeType
    fromOption[ProcessCompilationError, ParameterProviderT](customStreamTransformers.get(ref), MissingCustomNodeExecutor(ref))
      .toValidatedNel
      .andThen((k: ParameterProviderT) => validateParameters(k, node.data.parameters.map(_.name)))
      .map(createCustomNodeInvoker(_, metaData, node))
  }

  protected def createCustomNodeInvoker(obj: ParameterProviderT, metaData: MetaData, node: SplittedNode[graph.node.CustomNode]) : CustomNodeInvoker[Any]

  protected def createFactory[T](obj: ParameterProviderT): ProcessObjectFactory[T]

  private def compile(parts: List[SubsequentPart])
                     (implicit metaData: MetaData): ValidatedNel[ProcessCompilationError, List[compiledgraph.part.SubsequentPart]] = {
    parts.map(compile).sequence
  }

  private def validateParameters(parameterProvider: ParameterProviderT, usedParamsNames: List[String])
                                (implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, ParameterProviderT] = {
    val definedParamNames = parameterProvider.parameters.map(_.name).toSet
    val usedParamNamesSet = usedParamsNames.toSet
    val missingParams = definedParamNames.diff(usedParamNamesSet)
    val redundantParams = usedParamNamesSet.diff(definedParamNames)
    val notMissing = if (missingParams.nonEmpty) invalid(MissingParameters(missingParams)) else valid(Unit)
    val notRedundant = if (redundantParams.nonEmpty) invalid(RedundantParameters(redundantParams)) else valid(Unit)
    A.map2(
      notMissing.toValidatedNel,
      notRedundant.toValidatedNel
    ) { (_, _) => parameterProvider }.leftMap(_.map(identity[ProcessCompilationError]))
  }

  private def validate(n: splittednode.SplittedNode[_]): ValidatedNel[ProcessCompilationError, Unit] = {
    sub.validate(n).leftMap(_.map(identity[ProcessCompilationError]))
  }

}

object ProcessCompiler {

  def apply(sub: PartSubGraphCompiler,
            sourceFactories: Map[String, SourceFactory[_]],
            sinkFactories: Map[String, SinkFactory],
            foldingFunctions: Map[String, FoldingFunction[_]],
            customStreamTransformers: Map[String, CustomStreamTransformer],
            exceptionHandlerFactory: ExceptionHandlerFactory): ProcessCompiler = {
    val sourceFactoriesDefs = sourceFactories.mapValues { factory =>
      ObjectWithMethodDef(factory, ProcessObjectDefinitionExtractor.source.extractMethodDefinition(factory))
    }
    val sinkFactoriesDefs = sinkFactories.mapValues { factory =>
      ObjectWithMethodDef(factory, ProcessObjectDefinitionExtractor.sink.extractMethodDefinition(factory))
    }
    val ffun = foldingFunctions.asInstanceOf[Map[String, FoldingFunction[Any]]]
    val exceptionHandlerFactoryDefs = ObjectWithMethodDef(
      exceptionHandlerFactory,
      ProcessObjectDefinitionExtractor.exceptionHandler.extractMethodDefinition(exceptionHandlerFactory))
    val customNodesExecutorsDefs = customStreamTransformers.mapValues { executor =>
      ObjectWithMethodDef(executor, ProcessObjectDefinitionExtractor.customNodeExecutor.extractMethodDefinition(executor))
    }
    new ProcessCompiler(sub, sourceFactoriesDefs, sinkFactoriesDefs, ffun, customNodesExecutorsDefs, exceptionHandlerFactoryDefs)
  }

}

object ProcessValidator {

  def default(definition: ProcessDefinition): ProcessValidator = {
    val sub = PartSubGraphValidator.default(definition.services)
    val foldingFunctions = definition.foldingFunctions.map(name => name -> DumbFoldingFunction).toMap
    new ProcessValidator(sub, definition.sourceFactories, definition.sinkFactories, foldingFunctions, definition.customStreamTransformers,
      definition.exceptionHandlerFactory)
  }

}