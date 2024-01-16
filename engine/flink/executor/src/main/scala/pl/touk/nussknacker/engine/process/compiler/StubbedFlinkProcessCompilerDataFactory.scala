package pl.touk.nussknacker.engine.process.compiler

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.ModelData.ExtractDefinitionFun
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.component.{ComponentInfo, ComponentType}
import pl.touk.nussknacker.engine.api.context.ContextTransformation
import pl.touk.nussknacker.engine.api.namespaces.ObjectNaming
import pl.touk.nussknacker.engine.api.process.{ComponentUseCase, ProcessConfigCreator}
import pl.touk.nussknacker.engine.api.typed.ReturningType
import pl.touk.nussknacker.engine.api.typed.typing.{TypingResult, Unknown}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.definition.component.dynamic.DynamicComponentDefinitionWithLogic
import pl.touk.nussknacker.engine.definition.component.methodbased.MethodBasedComponentDefinitionWithLogic
import pl.touk.nussknacker.engine.definition.component.{ComponentDefinitionWithLogic, ComponentLogic}
import pl.touk.nussknacker.engine.definition.fragment.FragmentWithoutValidatorsDefinitionExtractor
import pl.touk.nussknacker.engine.definition.model.ModelDefinition
import pl.touk.nussknacker.engine.graph.node.{FragmentInputDefinition, Source}
import pl.touk.nussknacker.engine.process.compiler.StubbedComponentLogic.returnType
import shapeless.syntax.typeable.typeableOps

abstract class StubbedFlinkProcessCompilerDataFactory(
    process: CanonicalProcess,
    creator: ProcessConfigCreator,
    extractModelDefinition: ExtractDefinitionFun,
    modelConfig: Config,
    objectNaming: ObjectNaming,
    componentUseCase: ComponentUseCase
) extends FlinkProcessCompilerDataFactory(
      creator,
      extractModelDefinition,
      modelConfig,
      objectNaming,
      componentUseCase,
    ) {

  import pl.touk.nussknacker.engine.util.Implicits._

  override protected def adjustDefinitions(
      originalModelDefinition: ModelDefinition[ComponentDefinitionWithLogic],
      definitionContext: ComponentDefinitionContext
  ): ModelDefinition[ComponentDefinitionWithLogic] = {
    val collectedSources = process.allStartNodes.map(_.head.data).collect { case source: Source =>
      source
    }
    val usedSourceTypes = collectedSources.map(_.ref.typ)
    val stubbedSources =
      usedSourceTypes.map { sourceName =>
        val sourceDefinition = originalModelDefinition
          .getComponent(ComponentType.Source, sourceName)
          .getOrElse(
            throw new IllegalArgumentException(s"Source $sourceName cannot be stubbed - missing definition")
          )
        val stubbedDefinition = prepareSourceFactory(sourceDefinition, definitionContext)
        ComponentInfo(ComponentType.Source, sourceName) -> stubbedDefinition
      }

    val fragmentDefinitionExtractor = new FragmentWithoutValidatorsDefinitionExtractor(
      definitionContext.userCodeClassLoader
    )
    val fragmentSourceDefinitionPreparer = new StubbedFragmentSourceDefinitionPreparer(fragmentDefinitionExtractor)

    val stubbedSourceForFragments =
      process.allStartNodes.map(_.head.data).collect { case frag: FragmentInputDefinition =>
        val fragmentSourceDefWithImpl = fragmentSourceDefinitionPreparer.createSourceDefinition(frag)
        ComponentInfo(ComponentType.Fragment, frag.id) -> prepareSourceFactory(
          fragmentSourceDefWithImpl,
          definitionContext
        )
      }

    val stubbedServices = originalModelDefinition.components
      .filter(_._1.`type` == ComponentType.Service)
      .mapValuesNow(prepareService(_, definitionContext))

    originalModelDefinition
      .copy(
        components =
          originalModelDefinition.components ++ stubbedSources ++ stubbedSourceForFragments ++ stubbedServices
      )
  }

  protected def prepareService(
      service: ComponentDefinitionWithLogic,
      context: ComponentDefinitionContext
  ): ComponentDefinitionWithLogic

  protected def prepareSourceFactory(
      sourceFactory: ComponentDefinitionWithLogic,
      context: ComponentDefinitionContext
  ): ComponentDefinitionWithLogic

}

abstract class StubbedComponentLogic(original: ComponentLogic, originalDefinitionReturnType: Option[TypingResult])
    extends ComponentLogic {

  def this(componentDefinitionWithImpl: ComponentDefinitionWithLogic) = {
    this(
      componentDefinitionWithImpl.componentLogic,
      returnType(componentDefinitionWithImpl)
    )
  }

  override final def run(
      params: Map[String, Any],
      outputVariableNameOpt: Option[String],
      additional: Seq[AnyRef]
  ): Any = {
    def transform(impl: Any): Any = {
      // Correct TypingResult is important for method based components, because even for testing and verification
      // purpose, ImplementationInvoker is used also to determine output types. Dynamic components don't use it during
      // scenario validation so we pass Unknown for them
      val typingResult =
        impl
          .cast[ReturningType]
          .map(rt => rt.returnType)
          .orElse(originalDefinitionReturnType)
          .getOrElse(Unknown)
      val nodeId = additional
        .collectFirst { case nodeId: NodeId =>
          nodeId
        }
        .getOrElse(throw new IllegalArgumentException("Node id is missing in additional parameters"))

      handleRun(impl, typingResult, nodeId)
    }

    val originalValue = original.run(params, outputVariableNameOpt, additional)
    originalValue match {
      case contextTransformation: ContextTransformation =>
        contextTransformation.copy(logic = transform(contextTransformation.logic))
      case componentExecutor => transform(componentExecutor)
    }
  }

  def handleRun(impl: Any, typingResult: TypingResult, nodeId: NodeId): Any
}

object StubbedComponentLogic {

  private def returnType(componentDefinitionWithImpl: ComponentDefinitionWithLogic): Option[TypingResult] = {
    componentDefinitionWithImpl match {
      case methodBasedDefinition: MethodBasedComponentDefinitionWithLogic => methodBasedDefinition.returnType
      case _: DynamicComponentDefinitionWithLogic                         => None
    }
  }

}
