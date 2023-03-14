package pl.touk.nussknacker.engine.definition

import pl.touk.nussknacker.engine.api.component.{ParameterConfig, SingleComponentConfig}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, Unknown}
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, MetaData}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.FlatNode
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectDefinition
import pl.touk.nussknacker.engine.definition.SubprocessDefinitionExtractor.extractSubprocessParam
import pl.touk.nussknacker.engine.definition.parameter.ParameterData
import pl.touk.nussknacker.engine.definition.parameter.defaults.{DefaultValueDeterminerChain, DefaultValueDeterminerParameters}
import pl.touk.nussknacker.engine.definition.parameter.editor.EditorExtractor
import pl.touk.nussknacker.engine.definition.parameter.validator.{ValidatorExtractorParameters, ValidatorsExtractor}
import pl.touk.nussknacker.engine.graph.node.SubprocessInputDefinition
import pl.touk.nussknacker.engine.graph.node.SubprocessInputDefinition.SubprocessParameter


case class SubprocessDetails(canonical: CanonicalProcess, category: String)


class SubprocessDefinitionExtractor(subprocessesDetails: Set[SubprocessDetails], componentsConfig: Map[String, SingleComponentConfig], classLoader: ClassLoader) {

  def extractBySubprocessId(subprocessId: String): List[Parameter] = {
    extract.getOrElse(subprocessId, ObjectDefinition.withParams(List.empty)).parameters
  }

  def getFragment(id: String): Option[CanonicalProcess] = subprocessesDetails.collectFirst { case sd if sd.canonical.id.equals(id) => sd.canonical }

  protected def extract: Map[String, ObjectDefinition] = {
    subprocessesDetails.collect {
      case SubprocessDetails(CanonicalProcess(MetaData(id, FragmentSpecificData(docsUrl), _, _), FlatNode(SubprocessInputDefinition(_, parameters, _)) :: _, _), category) =>
        val config = componentsConfig.getOrElse(id, SingleComponentConfig.zero).copy(docsUrl = docsUrl)
        val typedParameters = parameters.map(extractSubprocessParam(classLoader, config))
        val objectDefinition = new ObjectDefinition(typedParameters, Typed[java.util.Map[String, Any]], Some(List(category)), config)
        (id, objectDefinition)
    }.toMap
  }
}

object SubprocessDefinitionExtractor {

  implicit val dummyExtractor = SubprocessDefinitionExtractor(subprocessesDetails = Set.empty, Map.empty, classLoader = this.getClass.getClassLoader)

  def apply(subprocessesDetails: Set[SubprocessDetails], subprocessesConfig: Map[String, SingleComponentConfig], classLoader: ClassLoader): SubprocessDefinitionExtractor = {
    new SubprocessDefinitionExtractor(
      subprocessesDetails = subprocessesDetails,
      componentsConfig  = subprocessesConfig,
      classLoader = classLoader
    )
  }

  def extractSubprocessParam(classLoader: ClassLoader, componentConfig: SingleComponentConfig)(p: SubprocessParameter): Parameter = {
    val runtimeClass = p.typ.toRuntimeClass(classLoader)
    //TODO: currently if we cannot parse parameter class we assume it's unknown
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
      javaOptionalParameter = false
    )
  }
}