package pl.touk.esp.engine.compile

import cats.data.Validated._
import cats.instances.list._
import cats.instances.option._
import cats.syntax.cartesian._
import cats.syntax.traverse._
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.{Semigroup, SemigroupK}
import pl.touk.esp.engine._
import ProcessCompilationError._
import pl.touk.esp.engine.api.process._
import pl.touk.esp.engine.api.{FoldingFunction, MetaData}
import pl.touk.esp.engine.compile.dumb._
import pl.touk.esp.engine.compiledgraph.CompiledProcessParts
import pl.touk.esp.engine.definition.DefinitionExtractor._
import pl.touk.esp.engine.definition.ProcessDefinitionExtractor.ProcessDefinition
import pl.touk.esp.engine.definition._
import pl.touk.esp.engine.graph.EspProcess
import pl.touk.esp.engine.graph.sink.SinkRef
import pl.touk.esp.engine.graph.source.SourceRef
import pl.touk.esp.engine.split._
import pl.touk.esp.engine.splittedgraph._
import pl.touk.esp.engine.splittedgraph.part._


class ProcessCompiler(protected val sub: PartSubGraphCompilerBase,
                      protected val sourceFactories: Map[String, ObjectWithMethodDef],
                      protected val sinkFactories: Map[String, ObjectWithMethodDef],
                      protected val foldingFunctions: Map[String, FoldingFunction[Any]]) extends ProcessCompilerBase {

  override type ParameterProviderT = ObjectWithMethodDef

  override def compile(process: EspProcess): ValidatedNel[ProcessCompilationError, CompiledProcessParts] = {
    super.compile(process)
  }

  override protected def createSourceCreator(obj: ObjectWithMethodDef) =
    SourceCreator[Any](obj)

  override protected def createSinkCreator(obj: ObjectWithMethodDef) =
    SinkCreator(obj)
}

class ProcessValidator(protected val sub: PartSubGraphCompilerBase,
                       protected val sourceFactories: Map[String, ObjectDefinition],
                       protected val sinkFactories: Map[String, ObjectDefinition],
                       protected val foldingFunctions: Map[String, FoldingFunction[Any]]) extends ProcessCompilerBase {

  override type ParameterProviderT = ObjectDefinition

  override protected def createSourceCreator(obj: ObjectDefinition) =
    DumbSourceCreator

  override protected def createSinkCreator(obj: ObjectDefinition) =
    DumbSinkCreator

}

protected trait ProcessCompilerBase {

  type ParameterProviderT <: ParametersProvider

  protected def sourceFactories: Map[String, ParameterProviderT]
  protected def sinkFactories: Map[String, ParameterProviderT]
  protected def foldingFunctions: Map[String, FoldingFunction[Any]]

  protected def sub: PartSubGraphCompilerBase

  private implicit val nelSemigroup: Semigroup[NonEmptyList[ProcessCompilationError]] =
    SemigroupK[NonEmptyList].algebra[ProcessCompilationError]

  def validate(process: EspProcess): ValidatedNel[ProcessCompilationError, Unit] = {
    compile(process).map(_ => Unit)
  }

  protected def compile(process: EspProcess): ValidatedNel[ProcessCompilationError, CompiledProcessParts] = {
    compile(ProcessSplitter.split(process))
  }

  private def compile(splittedProcess: SplittedProcess): ValidatedNel[ProcessCompilationError, CompiledProcessParts] = {
    implicit val metaData = splittedProcess.metaData
    (findDuplicates(splittedProcess.source).toValidatedNel |@| compile(splittedProcess.source)).map { (_, source) =>
      CompiledProcessParts(splittedProcess.metaData, source)
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
      case AggregatePart(id, durationInMillis, slideInMillis, aggregatedVar, foldingFunRef, aggregate, nextParts) =>
        (validate(aggregate) |@| foldingFunRef.map(compileFoldingFunction).sequenceU |@| compile(nextParts)).map { (_, foldingFunRefV, nextPartsV) =>
          compiledgraph.part.AggregatePart(id, durationInMillis, slideInMillis, aggregatedVar, foldingFunRefV, aggregate, nextPartsV)
        }
      case SinkPart(id, ref, sink) =>
        (validate(sink) |@| compile(ref)).map { (_, obj) =>
          compiledgraph.part.SinkPart(id, obj, sink)
        }
    }
  }

  private def compile(source: SourcePart)
                     (implicit metaData: MetaData): ValidatedNel[ProcessCompilationError, compiledgraph.part.SourcePart] = {
    implicit val nodeId = NodeId(source.id)
    (validate(source.source) |@| compile(source.ref) |@| compile(source.nextParts)).map { (_, obj, nextParts) =>
      compiledgraph.part.SourcePart(source.id, obj, source.source, nextParts)
    }
  }

  private def compile(ref: SourceRef)
                     (implicit nodeId: NodeId,
                      metaData: MetaData): ValidatedNel[ProcessCompilationError, api.process.Source[Any]] = {
    val validSourceFactory = sourceFactories.get(ref.typ).map(valid).getOrElse(invalid(MissingSourceFactory(ref.typ))).toValidatedNel
    validSourceFactory.andThen { sourceFactory =>
      validateParameters(sourceFactory, ref.parameters.map(_.name)).map { _ =>
        val creator = createSourceCreator(sourceFactory)
        creator.create(metaData, ref.parameters)
      }
    }
  }

  private def compile(ref: SinkRef)
                     (implicit nodeId: NodeId,
                      metaData: MetaData): ValidatedNel[ProcessCompilationError, api.process.Sink] = {
    val validSinkFactory = sinkFactories.get(ref.typ).map(valid).getOrElse(invalid(MissingSinkFactory(ref.typ))).toValidatedNel
    validSinkFactory.andThen { sinkFactory =>
      validateParameters(sinkFactory, ref.parameters.map(_.name)).map { _ =>
        val creator = createSinkCreator(sinkFactory)
        creator.create(metaData, ref.parameters)
      }
    }
  }

  private def compileFoldingFunction(ref: String)
                                    (implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, FoldingFunction[Any]] = {
    foldingFunctions.get(ref).map(valid).getOrElse(invalid(MissingFoldingFunction(ref))).toValidatedNel
  }

  protected def createSourceCreator(obj: ParameterProviderT): SourceCreator[Any]

  protected def createSinkCreator(obj: ParameterProviderT): SinkCreator

  private def compile(parts: List[SubsequentPart])
                     (implicit metaData: MetaData): ValidatedNel[ProcessCompilationError, List[compiledgraph.part.SubsequentPart]] = {
    parts.map(compile).sequenceU
  }

  private def validateParameters(parameterProvider: ParameterProviderT, usedParamNames: List[String])
                                (implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, Unit] = {
    val definedParamNames = parameterProvider.parameters.map(_.name).toSet
    val usedParamNamesSet = usedParamNames.toSet
    val missingParams = definedParamNames.diff(usedParamNamesSet)
    val redundantParams = usedParamNamesSet.diff(definedParamNames)
    val notMissing = if (missingParams.nonEmpty) invalid(MissingParameters(missingParams)) else valid(Unit)
    val notRedundant = if (redundantParams.nonEmpty) invalid(RedundantParameters(redundantParams)) else valid(Unit)
    (notMissing.toValidatedNel |@| notRedundant.toValidatedNel)
      .map { (_, _) => () }
      .leftMap(_.map(identity[ProcessCompilationError]))
  }

  private def validate(n: splittednode.SplittedNode): ValidatedNel[ProcessCompilationError, Unit] = {
    sub.validate(n).leftMap(_.map(identity[ProcessCompilationError]))
  }

}

object ProcessCompiler {

  def apply(sub: PartSubGraphCompiler,
            sourceFactories: Map[String, SourceFactory[_]],
            sinkFactories: Map[String, SinkFactory],
            foldingFunctions: Map[String, FoldingFunction[_]]): ProcessCompiler = {
    val sourceFactoriesDefs = sourceFactories.mapValues { factory =>
      ObjectWithMethodDef(factory, SourceFactoryDefinitionExtractor.extractMethodDefinition(factory))
    }
    val sinkFactoriesDefs = sinkFactories.mapValues { factory =>
      ObjectWithMethodDef(factory, SinkFactoryDefinitionExtractor.extractMethodDefinition(factory))
    }
    val ffun = foldingFunctions.asInstanceOf[Map[String, FoldingFunction[Any]]]
    new ProcessCompiler(sub, sourceFactoriesDefs, sinkFactoriesDefs, ffun)
  }

}

object ProcessValidator {

  def default(definition: ProcessDefinition): ProcessValidator = {
    val sub = PartSubGraphValidator.default(definition.services)
    val foldingFunctions = definition.foldingFunctions.map(name => name -> DumbFoldingFunction).toMap
    new ProcessValidator(sub, definition.sourceFactories, definition.sinkFactories, foldingFunctions)
  }

}