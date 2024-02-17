package pl.touk.nussknacker.engine.process.compiler

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.ModelData.ExtractDefinitionFun
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.component.ComponentType
import pl.touk.nussknacker.engine.api.context.ContextTransformation
import pl.touk.nussknacker.engine.api.namespaces.NamingStrategy
import pl.touk.nussknacker.engine.api.process.{ComponentUseCase, ProcessConfigCreator}
import pl.touk.nussknacker.engine.api.typed.ReturningType
import pl.touk.nussknacker.engine.api.typed.typing.{TypingResult, Unknown}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.definition.component.dynamic.DynamicComponentWithRuntimeLogicFactory
import pl.touk.nussknacker.engine.definition.component.methodbased.MethodBasedComponentWithRuntimeLogicFactory
import pl.touk.nussknacker.engine.definition.component.{ComponentRuntimeLogicFactory, ComponentWithRuntimeLogicFactory}
import pl.touk.nussknacker.engine.definition.fragment.FragmentParametersDefinitionExtractor
import pl.touk.nussknacker.engine.definition.model.ModelDefinition
import pl.touk.nussknacker.engine.graph.node.{FragmentInputDefinition, Source}
import pl.touk.nussknacker.engine.node.ComponentIdExtractor
import pl.touk.nussknacker.engine.process.compiler.StubbedComponentRuntimeLogicFactory.returnType
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
        val fragmentSourceDefWithImpl = fragmentSourceDefinitionPreparer.createSourceDefinition(frag.id, frag)
        prepareSourceFactory(fragmentSourceDefWithImpl, definitionContext)
      }

    originalModelDefinition
      .copy(components = processedComponents)
      .withComponents(stubbedSourceForFragments)
  }

  protected def prepareService(
      service: ComponentWithRuntimeLogicFactory,
      context: ComponentDefinitionContext
  ): ComponentWithRuntimeLogicFactory

  protected def prepareSourceFactory(
      sourceFactory: ComponentWithRuntimeLogicFactory,
      context: ComponentDefinitionContext
  ): ComponentWithRuntimeLogicFactory

}

abstract class StubbedComponentRuntimeLogicFactory(
    original: ComponentRuntimeLogicFactory,
    originalDefinitionReturnType: Option[TypingResult]
) extends ComponentRuntimeLogicFactory {

  def this(componentDefinitionWithImpl: ComponentWithRuntimeLogicFactory) = {
    this(
      componentDefinitionWithImpl.runtimeLogicFactory,
      returnType(componentDefinitionWithImpl)
    )
  }

  override def createRuntimeLogic(
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

      handleInvoke(impl, typingResult, nodeId)
    }

    val originalValue = original.createRuntimeLogic(params, outputVariableNameOpt, additional)
    originalValue match {
      case contextTransformation: ContextTransformation =>
        contextTransformation.copy(runtimeLogic = transform(contextTransformation.runtimeLogic))
      case componentExecutor => transform(componentExecutor)
    }
  }

  def handleInvoke(impl: Any, typingResult: TypingResult, nodeId: NodeId): Any
}

object StubbedComponentRuntimeLogicFactory {

  private def returnType(componentDefinitionWithImpl: ComponentWithRuntimeLogicFactory): Option[TypingResult] = {
    componentDefinitionWithImpl match {
      case methodBasedDefinition: MethodBasedComponentWithRuntimeLogicFactory => methodBasedDefinition.returnType
      case _: DynamicComponentWithRuntimeLogicFactory                         => None
    }
  }

}
