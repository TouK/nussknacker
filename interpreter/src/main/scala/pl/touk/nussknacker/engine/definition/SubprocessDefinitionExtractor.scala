package pl.touk.nussknacker.engine.definition

import cats.data.{NonEmptyList, ValidatedNel}
import cats.data.Validated.{invalid, valid}
import cats.implicits.toFoldableOps
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.FragmentSpecificData
import pl.touk.nussknacker.engine.api.component.{ParameterConfig, SingleComponentConfig}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.MultipleOutputsForName
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.typed.typing.{SingleTypingResult, Typed, Unknown}
import pl.touk.nussknacker.engine.canonicalgraph.{CanonicalProcess, canonicalnode}
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.{CanonicalNode, FlatNode}
import pl.touk.nussknacker.engine.compile.Output
import pl.touk.nussknacker.engine.component.ComponentsUiConfigExtractor
import pl.touk.nussknacker.engine.definition.parameter.ParameterData
import pl.touk.nussknacker.engine.definition.parameter.defaults.{DefaultValueDeterminerChain, DefaultValueDeterminerParameters}
import pl.touk.nussknacker.engine.definition.parameter.editor.EditorExtractor
import pl.touk.nussknacker.engine.definition.parameter.validator.{ValidatorExtractorParameters, ValidatorsExtractor}
import pl.touk.nussknacker.engine.graph.node.SubprocessInputDefinition.{SubprocessClazzRef, SubprocessParameter}
import pl.touk.nussknacker.engine.graph.node.{Join, SubprocessInputDefinition, SubprocessOutputDefinition}

class SubprocessDefinitionExtractor(componentConfig: String => Option[SingleComponentConfig], classLoader: ClassLoader) {

  def extractSubprocessDefinition(subprocess: CanonicalProcess): SubprocessDefinition = {
    subprocess.allStartNodes.collectFirst {
      case FlatNode(SubprocessInputDefinition(_, subprocessParameters, _)) :: nodes =>
        val additionalBranches = subprocess.allStartNodes.collect {
          case a@FlatNode(_: Join) :: _ => a
        }
        val docsUrl = subprocess.metaData.typeSpecificData.asInstanceOf[FragmentSpecificData].docsUrl
        val config = componentConfig(subprocess.id).getOrElse(SingleComponentConfig.zero).copy(docsUrl = docsUrl)

        val parameters = subprocessParameters.map(toParameter(classLoader, config))

        val outputs = subprocess.collectAllNodes.collect {
          case SubprocessOutputDefinition(_, name, fields, _) => Output(name, fields.nonEmpty)
        }

        new SubprocessDefinition(subprocess.id, parameters, nodes, additionalBranches, outputs, config)
    }.getOrElse(throw new IllegalStateException(s"Illegal fragment structure: $subprocess"))
  }

  private def toParameter(classLoader: ClassLoader, componentConfig: SingleComponentConfig)(p: SubprocessParameter): Parameter = {
    val runtimeClass = p.typ.toRuntimeClass(classLoader)
    //FIXME: currently if we cannot parse parameter class we assume it's unknown
    val typ = runtimeClass.map(Typed(_)).getOrElse(Unknown)
    val config = componentConfig.params.flatMap(_.get(p.name)).getOrElse(ParameterConfig.empty)
    val parameterData = ParameterData(typ, Nil)
    val extractedEditor = EditorExtractor.extract(parameterData, config)
    Parameter(
      name = p.name,
      typ = typ,
      editor = extractedEditor,
      validators = ValidatorsExtractor.extract(ValidatorExtractorParameters(parameterData, isOptional = true, config, extractedEditor)),
      // TODO: ability to pick default value from gui
      defaultValue = DefaultValueDeterminerChain.determineParameterDefaultValue(DefaultValueDeterminerParameters(parameterData, isOptional = true, config, extractedEditor)),
      additionalVariables = Map.empty,
      variablesToHide = Set.empty,
      branchParam = false,
      isLazyParameter = false,
      scalaOptionParameter = false,
      javaOptionalParameter = false)
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

  def toSubprocessParameter(p: Parameter): SubprocessParameter = {
    SubprocessParameter(p.name, SubprocessClazzRef(p.typ.asInstanceOf[SingleTypingResult].objType.klass.getName))
  }

}

class SubprocessDefinition(id: String,
                           val parameters: List[Parameter],
                           val nodes: List[CanonicalNode],
                           val additionalBranches: List[List[CanonicalNode]],
                           val allOutputs: List[Output],
                           val config: SingleComponentConfig) {

  def validOutputs: ValidatedNel[ProcessCompilationError, Set[Output]] = {
    NonEmptyList.fromList(allOutputs.groupBy(_.name).filter(_._2.size > 1).toList) match {
      case Some(groups) => invalid(groups.map(gr => MultipleOutputsForName(gr._1, id)))
      case None => valid(allOutputs.toSet)
    }
  }

}
