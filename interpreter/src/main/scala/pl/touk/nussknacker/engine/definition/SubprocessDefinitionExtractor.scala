package pl.touk.nussknacker.engine.definition

import cats.Id
import cats.data.Validated.{invalid, valid}
import cats.data.{NonEmptyList, ValidatedNel, WriterT}
import cats.implicits.{toFoldableOps, toTraverseOps}
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.component.{ParameterConfig, SingleComponentConfig}
import pl.touk.nussknacker.engine.api.context.{PartSubGraphCompilationError, ProcessCompilationError}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{MultipleOutputsForName, SubprocessParamClassLoadError}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{SingleTypingResult, Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, NodeId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.{CanonicalNode, FlatNode}
import pl.touk.nussknacker.engine.compile.Output
import pl.touk.nussknacker.engine.component.ComponentsUiConfigExtractor
import pl.touk.nussknacker.engine.definition.parameter.ParameterData
import pl.touk.nussknacker.engine.definition.parameter.defaults.{DefaultValueDeterminerChain, DefaultValueDeterminerParameters}
import pl.touk.nussknacker.engine.definition.parameter.editor.EditorExtractor
import pl.touk.nussknacker.engine.definition.parameter.validator.{ValidatorExtractorParameters, ValidatorsExtractor}
import pl.touk.nussknacker.engine.graph.node.SubprocessInputDefinition.{SubprocessClazzRef, SubprocessParameter}
import pl.touk.nussknacker.engine.graph.node.{Join, SubprocessInput, SubprocessInputDefinition, SubprocessOutputDefinition}

class SubprocessDefinitionExtractor(componentConfig: String => Option[SingleComponentConfig], classLoader: ClassLoader) {

  def extractSubprocessDefinition(subprocess: CanonicalProcess): SubprocessDefinition = {
    subprocess.allStartNodes.collectFirst {
      case FlatNode(SubprocessInputDefinition(_, subprocessParameters, _)) :: nodes =>
        val additionalBranches = subprocess.allStartNodes.collect {
          case a@FlatNode(_: Join) :: _ => a
        }
        val docsUrl = subprocess.metaData.typeSpecificData.asInstanceOf[FragmentSpecificData].docsUrl
        val config = componentConfig(subprocess.id).getOrElse(SingleComponentConfig.zero).copy(docsUrl = docsUrl)

        val parameters = subprocessParameters.map(toParameter(config)(_)).sequence.value

        val outputs = subprocess.collectAllNodes.collect {
          case SubprocessOutputDefinition(_, name, fields, _) => Output(name, fields.nonEmpty)
        }

        new SubprocessDefinition(parameters, nodes, additionalBranches, outputs, config)
    }.getOrElse(throw new IllegalStateException(s"Illegal fragment structure: $subprocess"))
  }

  def extractParametersDefinition(subprocessInput: SubprocessInput)(implicit nodeId: NodeId): WriterT[Id, List[PartSubGraphCompilationError], List[Parameter]] = {
    val config = componentConfig(subprocessInput.ref.id).getOrElse(SingleComponentConfig.zero)
    subprocessInput.subprocessParams.get.map(toParameter(config)).sequence
      .mapWritten(_.map(data => SubprocessParamClassLoadError(data.fieldName, data.refClazzName, nodeId.id)))
  }

  private def toParameter(componentConfig: SingleComponentConfig)(p: SubprocessParameter): WriterT[Id, List[SubprocessParamClassLoadErrorData], Parameter] = {
    val runtimeClass = p.typ.toRuntimeClass(classLoader)
    val paramName = p.name

    runtimeClass.map(Typed(_))
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

object SubprocessDefinitionExtractor {

  def apply(modelData: ModelData): SubprocessDefinitionExtractor = {
    SubprocessDefinitionExtractor(modelData.processConfig, modelData.modelClassLoader.classLoader)
  }

  def apply(modelConfig: Config, classLoader: ClassLoader): SubprocessDefinitionExtractor = {
    val componentsConfig = ComponentsUiConfigExtractor.extract(modelConfig)
    new SubprocessDefinitionExtractor(componentsConfig.get, classLoader)
  }


}

class SubprocessDefinition(val parameters: List[Parameter],
                           val nodes: List[CanonicalNode],
                           val additionalBranches: List[List[CanonicalNode]],
                           allOutputs: List[Output],
                           val config: SingleComponentConfig) {

  def validOutputs(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, Set[Output]] = {
    NonEmptyList.fromList(allOutputs.groupBy(_.name).filter(_._2.size > 1).toList) match {
      case Some(groups) => invalid(groups.map(gr => MultipleOutputsForName(gr._1, nodeId.id)))
      case None => valid(allOutputs.toSet)
    }
  }

  def outputNames: List[String] = allOutputs.map(_.name).sorted

  def subprocessParameters: List[SubprocessParameter] =
    parameters.map { p =>
      SubprocessParameter(p.name, SubprocessClazzRef(p.typ.asInstanceOf[SingleTypingResult].objType.klass.getName))
    }

}

case class SubprocessParamClassLoadErrorData(fieldName: String, refClazzName: String)