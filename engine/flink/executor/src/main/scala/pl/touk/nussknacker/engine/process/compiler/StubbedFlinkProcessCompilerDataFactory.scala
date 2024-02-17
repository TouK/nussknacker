package pl.touk.nussknacker.engine.process.compiler

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.ModelData.ExtractDefinitionFun
import pl.touk.nussknacker.engine.api.{NodeId, Params}
import pl.touk.nussknacker.engine.api.component.ComponentType
import pl.touk.nussknacker.engine.api.context.ContextTransformation
import pl.touk.nussknacker.engine.api.namespaces.NamingStrategy
import pl.touk.nussknacker.engine.api.process.{ComponentUseCase, ProcessConfigCreator}
import pl.touk.nussknacker.engine.api.typed.ReturningType
import pl.touk.nussknacker.engine.api.typed.typing.{TypingResult, Unknown}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.definition.component.dynamic.DynamicComponentDefinitionWithLogic
import pl.touk.nussknacker.engine.definition.component.methodbased.MethodBasedComponentDefinitionWithLogic
import pl.touk.nussknacker.engine.definition.component.{ComponentDefinitionWithLogic, ComponentLogic}
import pl.touk.nussknacker.engine.definition.fragment.FragmentParametersDefinitionExtractor
import pl.touk.nussknacker.engine.definition.model.ModelDefinition
import pl.touk.nussknacker.engine.graph.node.{FragmentInputDefinition, Source}
import pl.touk.nussknacker.engine.node.ComponentIdExtractor
import pl.touk.nussknacker.engine.process.compiler.StubbedComponentLogic.returnType
import shapeless.syntax.typeable.typeableOps

abstract class StubbedFlinkProcessCompilerDataFactory(
    process: CanonicalProcess,
    creator: ProcessConfigCreator,
    extractModelDefinition: ExtractDefinitionFun,
    modelConfig: Config,
    namingStrategy: NamingStrategy,
    componentUseCase: ComponentUseCase
) extends FlinkProcessCompilerDataFactory(
      creator,
      extractModelDefinition,
      modelConfig,
      namingStrategy,
      componentUseCase,
    ) {

  override protected def adjustDefinitions(
      originalModelDefinition: ModelDefinition,
      definitionContext: ComponentDefinitionContext
  ): ModelDefinition = {
    val usedSourceIds = process.allStartNodes
      .map(_.head.data)
      .collect { case source: Source =>
        ComponentIdExtractor.fromScenarioNode(source)
      }
      .flatten
      .toSet

    val processedComponents = originalModelDefinition.components.map {
      case source if usedSourceIds.contains(source.id) =>
        prepareSourceFactory(source, definitionContext)
      case service if service.componentType == ComponentType.Service =>
        prepareService(service, definitionContext)
      case other => other
    }

    val fragmentParametersDefinitionExtractor = new FragmentParametersDefinitionExtractor(
      definitionContext.userCodeClassLoader
    )
    val fragmentSourceDefinitionPreparer = new StubbedFragmentSourceDefinitionPreparer(
      fragmentParametersDefinitionExtractor
    )

    val stubbedSourceForFragments =
      process.allStartNodes.map(_.head.data).collect { case frag: FragmentInputDefinition =>
        // We create source definition only to reuse prepareSourceFactory method.
        // Source will have fragment component type to avoid collisions with normal sources
        val fragmentSourceDef = fragmentSourceDefinitionPreparer.createSourceDefinition(frag.id, frag)
        prepareSourceFactory(fragmentSourceDef, definitionContext)
      }

    originalModelDefinition
      .copy(components = processedComponents)
      .withComponents(stubbedSourceForFragments)
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

abstract class StubbedComponentLogic(
    original: ComponentLogic,
    originalDefinitionReturnType: Option[TypingResult]
) extends ComponentLogic {

  def this(componentDefinition: ComponentDefinitionWithLogic) = {
    this(
      componentDefinition.componentLogic,
      returnType(componentDefinition)
    )
  }

  override def run(params: Params, outputVariableNameOpt: Option[String], additional: Seq[AnyRef]): Any = {
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

      handleInvoke(impl, typingResult, nodeId)
    }

    val originalValue = original.run(params, outputVariableNameOpt, additional)
    originalValue match {
      case contextTransformation: ContextTransformation =>
        contextTransformation.copy(componentLogic = transform(contextTransformation.componentLogic))
      case componentExecutor => transform(componentExecutor)
    }
  }

  def handleInvoke(impl: Any, typingResult: TypingResult, nodeId: NodeId): Any
}

object StubbedComponentLogic {

  private def returnType(componentDefinition: ComponentDefinitionWithLogic): Option[TypingResult] = {
    componentDefinition match {
      case methodBasedDefinition: MethodBasedComponentDefinitionWithLogic => methodBasedDefinition.returnType
      case _: DynamicComponentDefinitionWithLogic                         => None
    }
  }

}
