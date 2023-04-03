package pl.touk.nussknacker.engine.definition

import cats.Id
import cats.data.Validated.{invalid, valid}
import cats.data.{NonEmptyList, ValidatedNel, WriterT}
import cats.implicits.toTraverseOps
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.component.{ParameterConfig, SingleComponentConfig}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{MultipleOutputsForName, SubprocessParamClassLoadError}
import pl.touk.nussknacker.engine.api.context.{PartSubGraphCompilationError, ProcessCompilationError}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, NodeId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.{CanonicalNode, FlatNode}
import pl.touk.nussknacker.engine.compile.Output
import pl.touk.nussknacker.engine.component.ComponentsUiConfigExtractor
import pl.touk.nussknacker.engine.definition.parameter.ParameterData
import pl.touk.nussknacker.engine.definition.parameter.defaults.{DefaultValueDeterminerChain, DefaultValueDeterminerParameters}
import pl.touk.nussknacker.engine.definition.parameter.editor.EditorExtractor
import pl.touk.nussknacker.engine.definition.parameter.validator.{ValidatorExtractorParameters, ValidatorsExtractor}
import pl.touk.nussknacker.engine.graph.node.SubprocessInputDefinition.SubprocessParameter
import pl.touk.nussknacker.engine.graph.node.{Join, SubprocessInput, SubprocessInputDefinition, SubprocessOutputDefinition}

// We have two implementations of SubprocessDefinitionExtractor. The only difference is that SubprocessGraphDefinitionExtractor
// extract parts of definition that is used for graph resolution wheres SubprocessComponentDefinitionExtractor is used
// for component definition extraction (parameters, validators, etc.) for purpose of further parameters validation
// We split it to avoid passing around ProcessingTypeData
abstract class SubprocessDefinitionExtractor {

  protected def withExistingSubprocessInput[T](subprocess: CanonicalProcess)(extract: (SubprocessInputDefinition, List[CanonicalNode], List[Output]) => T): T = {
    subprocess.allStartNodes.collectFirst {
      case FlatNode(input: SubprocessInputDefinition) :: nodes =>
        val outputs = collectOutputs(subprocess)
        extract(input, nodes, outputs)
    }.getOrElse(throw new IllegalStateException(s"Illegal fragment structure: $subprocess"))
  }

  private def collectOutputs(subprocess: CanonicalProcess): List[Output] = {
    subprocess.collectAllNodes.collect {
      case SubprocessOutputDefinition(_, name, fields, _) => Output(name, fields.nonEmpty)
    }
  }

}

class SubprocessComponentDefinitionExtractor(componentConfig: String => Option[SingleComponentConfig], classLoader: ClassLoader) extends SubprocessDefinitionExtractor {

  def extractSubprocessComponentDefinition(subprocess: CanonicalProcess): SubprocessComponentDefinition = {
    withExistingSubprocessInput(subprocess) { (input, _, outputs) =>
      val docsUrl = subprocess.metaData.typeSpecificData.asInstanceOf[FragmentSpecificData].docsUrl
      val config = componentConfig(subprocess.id).getOrElse(SingleComponentConfig.zero).copy(docsUrl = docsUrl)
      val parameters = input.parameters.map(toParameter(config)(_)).sequence.value
      new SubprocessComponentDefinition(parameters, config, outputs)
    }
  }

  def extractParametersDefinition(subprocessInput: SubprocessInput)(implicit nodeId: NodeId): WriterT[Id, List[PartSubGraphCompilationError], List[Parameter]] = {
    val config = componentConfig(subprocessInput.ref.id).getOrElse(SingleComponentConfig.zero)
    subprocessInput.subprocessParams.get.map(toParameter(config)).sequence
      .mapWritten(_.map(data => SubprocessParamClassLoadError(data.fieldName, data.refClazzName, nodeId.id)))
  }

  private def toParameter(componentConfig: SingleComponentConfig)(p: SubprocessParameter): WriterT[Id, List[SubprocessParamClassLoadErrorData], Parameter] = {
    val paramName = p.name
    p.typ.toRuntimeClass(classLoader).map(Typed(_))
      .map(WriterT.value[Id, List[SubprocessParamClassLoadErrorData], TypingResult])
      .getOrElse(WriterT
        .value[Id, List[SubprocessParamClassLoadErrorData], TypingResult](Unknown)
        .tell(List(SubprocessParamClassLoadErrorData(paramName, p.typ.refClazzName)))
      ).map(toParameter(componentConfig, paramName, _))
  }

  private def toParameter(componentConfig: SingleComponentConfig, paramName: String, typ: typing.TypingResult) = {
    val config = componentConfig.params.flatMap(_.get(paramName)).getOrElse(ParameterConfig.empty)
    val parameterData = ParameterData(typ, Nil)
    val extractedEditor = EditorExtractor.extract(parameterData, config)
    Parameter.optional(paramName, typ).copy(
      editor = extractedEditor,
      validators = ValidatorsExtractor.extract(ValidatorExtractorParameters(parameterData, isOptional = true, config, extractedEditor)),
      // TODO: ability to pick a default value from gui
      defaultValue = DefaultValueDeterminerChain.determineParameterDefaultValue(DefaultValueDeterminerParameters(parameterData, isOptional = true, config, extractedEditor)))
  }

}

object SubprocessComponentDefinitionExtractor {

  def apply(modelData: ModelData): SubprocessComponentDefinitionExtractor = {
    SubprocessComponentDefinitionExtractor(modelData.processConfig, modelData.modelClassLoader.classLoader)
  }

  def apply(modelConfig: Config, classLoader: ClassLoader): SubprocessComponentDefinitionExtractor = {
    val componentsConfig = ComponentsUiConfigExtractor.extract(modelConfig)
    new SubprocessComponentDefinitionExtractor(componentsConfig.get, classLoader)
  }

}

object SubprocessGraphDefinitionExtractor extends SubprocessDefinitionExtractor {

  def extractSubprocessGraphDefinition(subprocess: CanonicalProcess): SubprocessGraphDefinition = {
    withExistingSubprocessInput(subprocess) { (input, nodes, outputs) =>
      val additionalBranches = subprocess.allStartNodes.collect {
        case a@FlatNode(_: Join) :: _ => a
      }
      new SubprocessGraphDefinition(input.parameters, nodes, additionalBranches, outputs)
    }
  }

}

class SubprocessComponentDefinition(val parameters: List[Parameter],
                                    val config: SingleComponentConfig,
                                    allOutputs: List[Output]) {
  def outputNames: List[String] = allOutputs.map(_.name).sorted
}

class SubprocessGraphDefinition(val subprocessParameters: List[SubprocessParameter],
                                val nodes: List[CanonicalNode],
                                val additionalBranches: List[List[CanonicalNode]],
                                allOutputs: List[Output]) {

  def validOutputs(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, Set[Output]] = {
    NonEmptyList.fromList(allOutputs.groupBy(_.name).filter(_._2.size > 1).toList) match {
      case Some(groups) => invalid(groups.map(gr => MultipleOutputsForName(gr._1, nodeId.id)))
      case None => valid(allOutputs.toSet)
    }
  }

}

case class SubprocessParamClassLoadErrorData(fieldName: String, refClazzName: String)