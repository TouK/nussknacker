package pl.touk.nussknacker.engine.compile

import cats.data.Validated._
import cats.data.{NonEmptyList, Validated}
import cats.instances.list._
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine._
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.context._
import pl.touk.nussknacker.engine.api.dict.DictRegistry
import pl.touk.nussknacker.engine.api.expression.ExpressionParser
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.util.ThreadUtils
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.compile.nodecompilation.NodeCompiler
import pl.touk.nussknacker.engine.compiledgraph.part.PotentiallyStartPart
import pl.touk.nussknacker.engine.compiledgraph.{CompiledProcessParts, part}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor._
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.{ExpressionDefinition, ProcessDefinition}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.node.{Sink, Source => _, _}
import pl.touk.nussknacker.engine.split._
import pl.touk.nussknacker.engine.splittedgraph._
import pl.touk.nussknacker.engine.splittedgraph.end.NormalEnd
import pl.touk.nussknacker.engine.splittedgraph.part._
import pl.touk.nussknacker.engine.splittedgraph.splittednode.EndingNode
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer

import scala.util.control.NonFatal

class ProcessCompiler(protected val classLoader: ClassLoader,
                      protected val sub: PartSubGraphCompiler,
                      definitions: ProcessDefinition[ObjectWithMethodDef],
                      // compiler for object (source, sink, custom transformer) parameters
                      objectParametersExpressionCompiler: ExpressionCompiler
                     ) extends ProcessCompilerBase with ProcessValidator {


  override protected lazy val globalVariablesPreparer: GlobalVariablesPreparer = GlobalVariablesPreparer(definitions.expressionConfig)

  override protected val nodeCompiler: NodeCompiler = new NodeCompiler(definitions, objectParametersExpressionCompiler, classLoader)

  override def withExpressionParsers(modify: PartialFunction[ExpressionParser, ExpressionParser]): ProcessCompiler =
    new ProcessCompiler(classLoader, sub.withExpressionParsers(modify), definitions, objectParametersExpressionCompiler.withExpressionParsers(modify))

  override def compile(process: EspProcess): CompilationResult[CompiledProcessParts] = {
    super.compile(process)
  }
}

trait ProcessValidator extends LazyLogging {

  def validate(canonical: CanonicalProcess): CompilationResult[Unit] = {
    ProcessCanonizer.uncanonizeArtificial(canonical).map(validate).extract
  }

  def validate(process: EspProcess): CompilationResult[Unit] = {
    try {
      compile(process).map(_ => Unit)
    } catch {
      case NonFatal(e) =>
        logger.warn(s"Unexpected error during compilation of ${process.id}", e)
        CompilationResult(Invalid(NonEmptyList.of(FatalUnknownError(e.getMessage))))
    }
  }

  def withExpressionParsers(modify: PartialFunction[ExpressionParser, ExpressionParser]): ProcessValidator

  protected def compile(process: EspProcess): CompilationResult[_]

}

protected trait ProcessCompilerBase {
  
  protected def sub: PartSubGraphCompiler

  protected def classLoader: ClassLoader

  protected def nodeCompiler: NodeCompiler

  protected def globalVariablesPreparer: GlobalVariablesPreparer

  protected def compile(process: EspProcess): CompilationResult[CompiledProcessParts] = {
    ThreadUtils.withThisAsContextClassLoader(classLoader) {
      compile(ProcessSplitter.split(process))
    }
  }

  private def contextWithOnlyGlobalVariables(implicit metaData: MetaData): ValidationContext
  = globalVariablesPreparer.emptyValidationContext(metaData)

  private def compile(splittedProcess: SplittedProcess): CompilationResult[CompiledProcessParts] = {
    implicit val metaData: MetaData = splittedProcess.metaData
    val (typingInfo, compiledExceptionHandler) = nodeCompiler.compileExceptionHandler(splittedProcess.exceptionHandlerRef)
    val nodeTypingInfo = Map(NodeTypingInfo.ExceptionHandlerNodeId -> NodeTypingInfo(contextWithOnlyGlobalVariables, typingInfo, None))
    CompilationResult.map3(
      CompilationResult(findDuplicates(splittedProcess.sources).toValidatedNel),
      CompilationResult(nodeTypingInfo, compiledExceptionHandler),
      compileSources(splittedProcess.sources)
    ) { (_, exceptionHandler, sources) =>
      CompiledProcessParts(splittedProcess.metaData, exceptionHandler, sources)
    }
  }

  /*
    We need to sort SourceParts to know types of variables in branches for joins. See comment in PartSort
    In the future we'll probably move to direct representation of process as graph and this will no longer be needed
   */
  private def compileSources(sources: NonEmptyList[SourcePart])(implicit meta: MetaData): CompilationResult[NonEmptyList[PotentiallyStartPart]] = {
    val zeroAcc = (CompilationResult(Valid(List[PotentiallyStartPart]())), BranchEndContexts(Map()))
    //we use fold here (and not map/sequence), because we can compile part which starts from Join only when we
    //know compilation results (stored in BranchEndContexts) of all branches that end in this join
    val (result, _) = PartSort.sort(sources.toList).foldLeft(zeroAcc) { case ((resultSoFar, branchContexts), nextSourcePart) =>
      val compiledPart = compile(nextSourcePart, branchContexts)
      //we don't use andThen on CompilationResult, since we don't want to stop if there are errors in part
      val nextResult = CompilationResult.map2(resultSoFar, compiledPart)(_ :+ _)
      (nextResult, branchContexts.addPart(nextSourcePart, compiledPart))
    }
    result.map(NonEmptyList.fromListUnsafe)
  }

  private def findDuplicates(parts: NonEmptyList[SourcePart]): Validated[ProcessCompilationError, Unit] = {
    val allNodes = NodesCollector.collectNodesInAllParts(parts)
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

  private def compile(source: SourcePart, branchEndContexts: BranchEndContexts)
                     (implicit metaData: MetaData): CompilationResult[compiledgraph.part.PotentiallyStartPart] = {
    implicit val nodeId: NodeId = NodeId(source.id)

    source match {
      case SourcePart(splittednode.SourceNode(sourceData: SourceNodeData, _), _, _) =>
        compileSourcePart(source, sourceData)
      case SourcePart(srcNode@splittednode.SourceNode(data: Join, _), _, _) =>
        val node = srcNode.asInstanceOf[splittednode.SourceNode[Join]]
        compileCustomNodePart(source, node, data, Right(branchEndContexts))
    }

  }

  private def compileParts(parts: List[SubsequentPart], ctx: Map[String, ValidationContext])
                          (implicit metaData: MetaData): CompilationResult[List[compiledgraph.part.SubsequentPart]] = {
    import CompilationResult._
    parts.map(p =>
      ctx.get(p.id).map(compileSubsequentPart(p, _)).getOrElse(CompilationResult(Invalid(NonEmptyList.of[ProcessCompilationError](MissingPart(p.id)))))
    ).sequence
  }

  private def compileSubsequentPart(part: SubsequentPart, ctx: ValidationContext)
                                   (implicit metaData: MetaData): CompilationResult[compiledgraph.part.SubsequentPart] = {
    implicit val nodeId: NodeId = NodeId(part.id)
    part match {
      case SinkPart(node) =>
        compileSinkPart(node, ctx)
      case CustomNodePart(node@splittednode.EndingNode(data), _, _) =>
        compileEndingCustomNodePart(node, data, ctx)
      case CustomNodePart(node@splittednode.OneOutputSubsequentNode(data, _), _, _) =>
        compileCustomNodePart(part, node, data, Left(ctx))
    }
  }

  def compileSourcePart(part: SourcePart, sourceData: SourceNodeData)
                       (implicit nodeId: NodeId, metaData: MetaData): CompilationResult[compiledgraph.part.SourcePart] = {
    val (typingInfo, parameters, initialCtx, compiledSource) = nodeCompiler.compileSource(sourceData)

    val validatedSource = sub.validate(part.node, initialCtx.valueOr(_ => contextWithOnlyGlobalVariables))
    val typesForParts = validatedSource.typing.mapValues(_.inputValidationContext)
    val nodeTypingInfo = Map(part.id -> NodeTypingInfo(contextWithOnlyGlobalVariables, typingInfo, parameters))

    CompilationResult.map4(
      validatedSource,
      compileParts(part.nextParts, typesForParts),
      CompilationResult(initialCtx),
      CompilationResult(nodeTypingInfo, compiledSource)) { (_, nextParts, ctx, obj) =>
      compiledgraph.part.SourcePart(obj, splittednode.SourceNode(sourceData, part.node.next), ctx, nextParts, part.ends)
    }
  }

  def compileSinkPart(node: EndingNode[Sink], ctx: ValidationContext)(implicit metaData: MetaData, nodeId: NodeId): CompilationResult[part.SinkPart] = {
    val (typingInfo, parameters, compiledSink) = nodeCompiler.compileSink(node.data, ctx)
    val nodeTypingInfo = Map(node.id -> NodeTypingInfo(ctx, typingInfo, parameters))
    CompilationResult.map2(sub.validate(node, ctx), CompilationResult(nodeTypingInfo, compiledSink))((_, obj) =>
      compiledgraph.part.SinkPart(obj, node, ctx)
    )
  }

  def compileEndingCustomNodePart(node: splittednode.EndingNode[CustomNode], data: CustomNodeData,
                                  ctx: ValidationContext)
                                 (implicit metaData: MetaData, nodeId: NodeId): CompilationResult[compiledgraph.part.CustomNodePart] = {
    val (typingInfo, parameters, validatedNextCtx, compiledNode) = nodeCompiler.compileCustomNodeObject(data, Left(ctx), ending = true)
    val nodeTypingInfo = Map(node.id -> NodeTypingInfo(ctx, typingInfo, parameters))

    CompilationResult.map2(
      CompilationResult(nodeTypingInfo, compiledNode),
      CompilationResult(validatedNextCtx)
    ) { (nodeInvoker, nextCtx) =>
      compiledgraph.part.CustomNodePart(nodeInvoker, node, nextCtx, List.empty, List(NormalEnd(node.id)))
    }.distinctErrors
  }

  def compileCustomNodePart(part: ProcessPart, node: splittednode.OneOutputNode[CustomNodeData], data: CustomNodeData,
                            ctx: Either[ValidationContext, BranchEndContexts])
                           (implicit metaData: MetaData, nodeId: NodeId): CompilationResult[compiledgraph.part.CustomNodePart] = {
    val (typingInfo, parameters, validatedNextCtx, compiledNode) = nodeCompiler.compileCustomNodeObject(data, ctx.right.map(_.contexts), ending = false)

    val nextPartsValidation = sub.validate(node, validatedNextCtx.valueOr(_ => ctx.left.getOrElse(contextWithOnlyGlobalVariables)))
    val typesForParts = nextPartsValidation.typing.mapValues(_.inputValidationContext)
    val nodeTypingInfo = Map(node.id -> NodeTypingInfo(ctx.left.getOrElse(contextWithOnlyGlobalVariables), typingInfo, parameters))

    CompilationResult.map4(
      CompilationResult(nodeTypingInfo, compiledNode),
      nextPartsValidation,
      compileParts(part.nextParts, typesForParts),
      CompilationResult(validatedNextCtx)
    ) { (nodeInvoker, _, nextPartsCompiled, nextCtx) =>
      compiledgraph.part.CustomNodePart(nodeInvoker, node, nextCtx, nextPartsCompiled, part.ends)
    }.distinctErrors
  }

  private case class BranchEndContexts(contexts: Map[String, ValidationContext]) {

    def addPart(part: ProcessPart, result: CompilationResult[_]): BranchEndContexts = {
      val branchEnds = NodesCollector.collectNodesInAllParts(part).collect {
        case splittednode.EndingNode(BranchEndData(definition)) => definition.id -> result.typing.apply(definition.artificialNodeId)
      }.toMap
      copy(contexts = contexts ++ branchEnds.mapValues(_.inputValidationContext))
    }
  }

}

object ProcessCompiler {

  def apply(classLoader: ClassLoader,
            definitions: ProcessDefinition[ObjectWithMethodDef],
            dictRegistry: DictRegistry,
            expressionCompilerCreate: (ClassLoader, DictRegistry, ExpressionDefinition[ObjectWithMethodDef], ClassExtractionSettings) => ExpressionCompiler): ProcessCompiler = {
    val expressionCompiler = expressionCompilerCreate(classLoader, dictRegistry, definitions.expressionConfig, definitions.settings)
    val sub = new PartSubGraphCompiler(classLoader, expressionCompiler, definitions.expressionConfig, definitions.services)
    new ProcessCompiler(classLoader, sub, definitions, expressionCompiler)
  }

}

object ProcessValidator {

  def default(definitions: ProcessDefinition[ObjectWithMethodDef], dictRegistry: DictRegistry, loader: ClassLoader = getClass.getClassLoader): ProcessValidator = {
    ProcessCompiler(loader, definitions, dictRegistry, ExpressionCompiler.withoutOptimization)
  }

}

