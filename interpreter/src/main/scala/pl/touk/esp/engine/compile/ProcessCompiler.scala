package pl.touk.esp.engine.compile

import cats.data.Validated._
import cats.data.{Validated, ValidatedNel}
import cats.instances.list._
import cats.instances.option._
import pl.touk.esp.engine._
import pl.touk.esp.engine.api.exception.{EspExceptionHandler, ExceptionHandlerFactory}
import pl.touk.esp.engine.api.process._
import pl.touk.esp.engine.api.{CustomStreamTransformer, MetaData, Service}
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
        validateWithoutContextValidation(node, ctx).andThen { newCtx =>
          compileCustomNodeInvoker(node).andThen { nodeInvoker =>
            compile(nextParts, newCtx).map { nextParts =>
              compiledgraph.part.CustomNodePart(nodeInvoker, node, nextParts, ends)
            }
          }
        }
    }
  }

  private def compile(source: SourcePart)
                     (implicit metaData: MetaData): ValidatedNel[ProcessCompilationError, compiledgraph.part.SourcePart] = {
    implicit val nodeId = NodeId(source.id)
    val variables = sourceFactories.get(source.node.data.ref.typ)
      .map(sf => Map(Interpreter.InputParamName -> sf.definedClass)).getOrElse(Map.empty)
    validate(source.node, ValidationContext(variables, typesInformation)).andThen { ctx =>
      compile(source.node.data.ref).andThen { obj =>
        compile(source.nextParts, ctx).map { nextParts =>
          compiledgraph.part.SourcePart(obj, source.node, nextParts, source.ends)
        }
      }
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

  private def compile(parts: List[SubsequentPart], ctx: ValidationContext)
                     (implicit metaData: MetaData): ValidatedNel[ProcessCompilationError, List[compiledgraph.part.SubsequentPart]] = {
    parts.map(p => compile(p, ctx)).sequence
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

  private def validate(n: splittednode.SplittedNode[_], ctx: ValidationContext): ValidatedNel[ProcessCompilationError, ValidationContext] = {
    sub.validate(n, ctx).leftMap(_.map(identity[ProcessCompilationError]))
  }

  private def validateWithoutContextValidation(n: splittednode.SplittedNode[_], ctx: ValidationContext): ValidatedNel[ProcessCompilationError, ValidationContext] = {
    sub.validateWithoutContextValidation(n).leftMap(_.map(identity[ProcessCompilationError]))
  }


}

object ProcessValidator {

  def default(definition: ProcessDefinition[ObjectDefinition]): ProcessValidator = {
    val sub = PartSubGraphValidator.default(definition.services, definition.globalVariables.mapValues(_.value))
    new ProcessValidator(sub, definition)
  }

}