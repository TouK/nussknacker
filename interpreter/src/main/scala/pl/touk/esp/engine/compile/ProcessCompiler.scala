package pl.touk.esp.engine.compile

import cats.data.Validated._
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.instances.list._
import pl.touk.esp.engine._
import pl.touk.esp.engine.api.MetaData
import pl.touk.esp.engine.api.exception.{EspExceptionHandler, EspExceptionInfo}
import pl.touk.esp.engine.api.process._
import pl.touk.esp.engine.canonicalgraph.CanonicalProcess
import pl.touk.esp.engine.canonize.ProcessCanonizer
import pl.touk.esp.engine.compile.PartSubGraphCompilerBase.ContextsForParts
import pl.touk.esp.engine.compile.ProcessCompilationError._
import pl.touk.esp.engine.compile.dumb._
import pl.touk.esp.engine.compiledgraph.CompiledProcessParts
import pl.touk.esp.engine.splittedgraph.splittednode.PartRef
import pl.touk.esp.engine.compiledgraph.part.NextWithParts
import pl.touk.esp.engine.definition.DefinitionExtractor._
import pl.touk.esp.engine.definition.ProcessDefinitionExtractor.ProcessDefinition
import pl.touk.esp.engine.definition._
import pl.touk.esp.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.esp.engine.graph.node.{CustomNode, StartingNodeData, SubprocessInputDefinition}
import pl.touk.esp.engine.graph.sink.SinkRef
import pl.touk.esp.engine.graph.{EspProcess, param}
import pl.touk.esp.engine.split._
import pl.touk.esp.engine.splittedgraph._
import pl.touk.esp.engine.splittedgraph.part._
import pl.touk.esp.engine.splittedgraph.splittednode.{Next, NextNode, SplittedNode}

class ProcessCompiler(protected val sub: PartSubGraphCompilerBase,
                      protected val definitions: ProcessDefinition[ObjectWithMethodDef]) extends ProcessCompilerBase {

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
                       protected val definitions: ProcessDefinition[ObjectDefinition]) extends ProcessCompilerBase {

  override type ParameterProviderT = ObjectDefinition

  override protected def createFactory[T](obj: ObjectDefinition) =
    new DumbProcessObjectFactory[T]

  override protected def createCustomNodeInvoker(obj: ObjectDefinition, metaData: MetaData, node: SplittedNode[graph.node.CustomNode]) =
    new DumbCustomNodeInvoker[Any]
}

protected trait ProcessCompilerBase {

  type ParameterProviderT <: ObjectMetadata

  protected def definitions: ProcessDefinition[ParameterProviderT]
  protected def sourceFactories = definitions.sourceFactories
  protected def sinkFactories = definitions.sinkFactories
  protected def exceptionHandlerFactory = definitions.exceptionHandlerFactory
  protected val customStreamTransformers = definitions.customStreamTransformers
  protected val typesInformation = definitions.typesInformation

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

  private def contextAfterCustomNode(node: CustomNode, nodeDefinition: ParameterProviderT, validationContext: ValidationContext)
                                    (implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, ValidationContext]=
    (node.outputVar, nodeDefinition.hasNoReturn) match {
      case (Some(varName), false) => validationContext.withVariable(varName, nodeDefinition.returnType)
        //ble... NonEmptyList is invariant...
        .asInstanceOf[ValidatedNel[ProcessCompilationError,ValidationContext]]
      case (None, true) => Valid(validationContext)
      case (Some(_), true) => Invalid(NonEmptyList.of(RedundantParameters(Set("OutputVariable"))))
      case (None, false) => Invalid(NonEmptyList.of(MissingParameters(Set("OutputVariable"))))
    }

  private def compile(part: SubsequentPart, ctx: ValidationContext)
                     (implicit metaData: MetaData): ValidatedNel[ProcessCompilationError, compiledgraph.part.SubsequentPart] = {
    implicit val nodeId = NodeId(part.id)
    part match {
      case SinkPart(node) =>
        validate(node, ctx).andThen { newCtx =>
          compile(node.data.ref).map { obj =>
            compiledgraph.part.SinkPart(obj, node)
          }
        }
      case CustomNodePart(node, nextParts, ends) =>
        getCustomNodeDefinition(node).andThen { nodeDefinition =>
          contextAfterCustomNode(node.data, nodeDefinition, ctx).andThen { ctxWithVar =>
            validate(node, ctxWithVar).andThen { newCtx =>
              compileCustomNodeInvoker(node, nodeDefinition).andThen { nodeInvoker =>
                compile(nextParts, newCtx).map { nextParts =>
                  compiledgraph.part.CustomNodePart(nodeInvoker, node, nextParts, ends)
                }
              }
            }
          }
        }

      case SplitPart(node@splittednode.SplitNode(_, nexts)) =>
        nexts.map { next =>
          validate(next.next, ctx).andThen { newCtx =>
            compile(next.nextParts, newCtx).map(cp => NextWithParts(next.next, cp, next.ends))
          }
        }.sequence.map { nextsWithParts =>
          compiledgraph.part.SplitPart(node, nextsWithParts)
        }

    }
  }

  private def compile(source: SourcePart)
                     (implicit metaData: MetaData): ValidatedNel[ProcessCompilationError, compiledgraph.part.SourcePart] = {
    implicit val nodeId = NodeId(source.id)
    val variables = computeInitialVariables(source.node.data)
    A.map2(validate(source.node, ValidationContext(variables, typesInformation)), compile(source.node.data)) { (ctx, obj) =>
      compile(source.nextParts, ctx).map { nextParts =>
        compiledgraph.part.SourcePart(obj, source.node, nextParts, source.ends)
      }
    }.andThen(identity)
  }

  private def computeInitialVariables(nodeData: StartingNodeData) : Map[String, ClazzRef] = nodeData match {
    case pl.touk.esp.engine.graph.node.Source(_, ref, _) =>  sourceFactories.get(ref.typ)
          .map(sf => Map(Interpreter.InputParamName -> sf.returnType)).getOrElse(Map.empty)
    case SubprocessInputDefinition(_, params, _) => params.map(p => p.name -> p.typ).toMap
  }

  private def compile(ref: ExceptionHandlerRef)
                      (implicit metaData: MetaData): ValidatedNel[ProcessCompilationError, EspExceptionHandler] = {
    implicit val nodeId = NodeId(ProcessCompilationError.ProcessNodeId)
    if (metaData.isSubprocess) {
      //FIXME: co tutaj?
      Valid(new EspExceptionHandler {
        override def handle(exceptionInfo: EspExceptionInfo[_ <: Throwable]): Unit = {}
      })
    } else {
      compileProcessObject[EspExceptionHandler](exceptionHandlerFactory, ref.parameters)
    }
  }

  private def compile(nodeData: StartingNodeData)
                     (implicit nodeId: NodeId,
                      metaData: MetaData): ValidatedNel[ProcessCompilationError, api.process.Source[Any]] = nodeData match {
    case pl.touk.esp.engine.graph.node.Source(_, ref, _) =>
      val validSourceFactory = sourceFactories.get(ref.typ).map(valid).getOrElse(invalid(MissingSourceFactory(ref.typ))).toValidatedNel
        validSourceFactory.andThen(sourceFactory => compileProcessObject[Source[Any]](sourceFactory, ref.parameters))
    case SubprocessInputDefinition(_, _, _) => Valid(new Source[Any]{}) //FIXME: How should this be handled?
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

  private def getCustomNodeDefinition(node: SplittedNode[graph.node.CustomNode])(implicit nodeId: NodeId, metaData: MetaData) = {
    val ref = node.data.nodeType
    fromOption[ProcessCompilationError, ParameterProviderT](customStreamTransformers.get(ref).map(_._1), MissingCustomNodeExecutor(ref))
          .toValidatedNel
  }

  private def compileCustomNodeInvoker(node: SplittedNode[CustomNode], nodeDefinition: ParameterProviderT)
                                      (implicit nodeId: NodeId, metaData: MetaData): ValidatedNel[ProcessCompilationError, CustomNodeInvoker[Any]] = {
    validateParameters(nodeDefinition, node.data.parameters.map(_.name))
      .map(createCustomNodeInvoker(_, metaData, node))
  }

  protected def createCustomNodeInvoker(obj: ParameterProviderT, metaData: MetaData, node: SplittedNode[graph.node.CustomNode]) : CustomNodeInvoker[Any]

  protected def createFactory[T](obj: ParameterProviderT): ProcessObjectFactory[T]

  private def compile(parts: List[SubsequentPart], ctx: ContextsForParts)
                     (implicit metaData: MetaData): ValidatedNel[ProcessCompilationError, List[compiledgraph.part.SubsequentPart]] = {
    parts.map(p =>
      ctx.get(p.id).map(compile(p, _)).getOrElse(Invalid(NonEmptyList.of[ProcessCompilationError](MissingPart(p.id))))).sequence
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

  private def validate(n: splittednode.SplittedNode[_], ctx: ValidationContext): ValidatedNel[ProcessCompilationError, ContextsForParts] = {
    sub.validate(n, ctx).leftMap(_.map(identity[ProcessCompilationError]))
  }

  private def validate(n: Next, ctx: ValidationContext): ValidatedNel[ProcessCompilationError, ContextsForParts] = n match {
    case NextNode(node) => sub.validate(node, ctx).leftMap(_.map(identity[ProcessCompilationError]))
    //TODO: a moze cos tu innego powinno byc??
    case PartRef(id) => Validated.valid(Map(id -> ctx))
  }

  private def validateWithoutContextValidation(n: splittednode.SplittedNode[_], ctx: ValidationContext): ValidatedNel[ProcessCompilationError, ContextsForParts] = {
    sub.validateWithoutContextValidation(n).leftMap(_.map(identity[ProcessCompilationError]))
  }


}

object ProcessValidator {

  import pl.touk.esp.engine.util.Implicits._

  def default(definition: ProcessDefinition[ObjectDefinition], loader: ClassLoader = getClass.getClassLoader): ProcessValidator = {
    val sub = PartSubGraphValidator.default(definition.services, definition.globalVariables.mapValuesNow(_.value), loader)
    new ProcessValidator(sub, definition)
  }

}