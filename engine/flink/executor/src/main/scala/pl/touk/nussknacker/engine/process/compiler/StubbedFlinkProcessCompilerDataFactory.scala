package pl.touk.nussknacker.engine.process.compiler

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.ModelData.ExtractDefinitionFun
import pl.touk.nussknacker.engine.api.component.{
  ComponentAdditionalConfig,
  ComponentType,
  DesignerWideComponentId,
  NodesDeploymentData
}
import pl.touk.nussknacker.engine.api.context.ContextTransformation
import pl.touk.nussknacker.engine.api.process.{ComponentUseCase, ProcessConfigCreator}
import pl.touk.nussknacker.engine.api.typed.ReturningType
import pl.touk.nussknacker.engine.api.typed.typing.{TypingResult, Unknown}
import pl.touk.nussknacker.engine.api.{NodeId, Params}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.definition.clazz.ClassDefinitionSet
import pl.touk.nussknacker.engine.definition.component.dynamic.DynamicComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.definition.component.methodbased.MethodBasedComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.definition.component.{
  ComponentDefinitionWithImplementation,
  ComponentImplementationInvoker
}
import pl.touk.nussknacker.engine.definition.fragment.FragmentParametersDefinitionExtractor
import pl.touk.nussknacker.engine.definition.model.ModelDefinition
import pl.touk.nussknacker.engine.graph.node.{FragmentInputDefinition, Source}
import pl.touk.nussknacker.engine.node.ComponentIdExtractor
import pl.touk.nussknacker.engine.process.compiler.StubbedComponentImplementationInvoker.returnType
import shapeless.syntax.typeable.typeableOps

abstract class StubbedFlinkProcessCompilerDataFactory(
    process: CanonicalProcess,
    creator: ProcessConfigCreator,
    extractModelDefinition: ExtractDefinitionFun,
    modelConfig: Config,
    componentUseCase: ComponentUseCase,
    configsFromProviderWithDictionaryEditor: Map[DesignerWideComponentId, ComponentAdditionalConfig]
) extends FlinkProcessCompilerDataFactory(
      creator,
      extractModelDefinition,
      modelConfig,
      componentUseCase,
      configsFromProviderWithDictionaryEditor,
      NodesDeploymentData.empty
    ) {

  override protected def adjustDefinitions(
      originalModelDefinition: ModelDefinition,
      definitionContext: ComponentDefinitionContext,
      classDefinitions: ClassDefinitionSet,
  ): ModelDefinition = {
    val allStartNodesData = process.allStartNodes.toList
      .flatMap(_.headOption)
      .map(_.data)

    val usedSourceIds = allStartNodesData
      .collect { case source: Source =>
        ComponentIdExtractor.fromScenarioNode(source)
      }
      .flatten
      .toSet

    val processedComponents = originalModelDefinition.components.components.map {
      case source if usedSourceIds.contains(source.id) =>
        prepareSourceFactory(source, definitionContext)
      case service if service.componentType == ComponentType.Service =>
        prepareService(service, definitionContext)
      case other => other
    }

    val fragmentParametersDefinitionExtractor = new FragmentParametersDefinitionExtractor(
      definitionContext.userCodeClassLoader,
      classDefinitions,
    )
    val fragmentSourceDefinitionPreparer = new StubbedFragmentSourceDefinitionPreparer(
      fragmentParametersDefinitionExtractor
    )

    val stubbedSourceForFragments =
      allStartNodesData.collect { case frag: FragmentInputDefinition =>
        // We create source definition only to reuse prepareSourceFactory method.
        // Source will have fragment component type to avoid collisions with normal sources
        val fragmentSourceDef = fragmentSourceDefinitionPreparer.createSourceDefinition(frag.id, frag)
        prepareSourceFactory(fragmentSourceDef, definitionContext)
      }

    originalModelDefinition
      .copy(components = originalModelDefinition.components.copy(components = processedComponents))
      .withComponents(stubbedSourceForFragments)
  }

  protected def prepareService(
      service: ComponentDefinitionWithImplementation,
      context: ComponentDefinitionContext
  ): ComponentDefinitionWithImplementation

  protected def prepareSourceFactory(
      sourceFactory: ComponentDefinitionWithImplementation,
      context: ComponentDefinitionContext
  ): ComponentDefinitionWithImplementation

}

abstract class StubbedComponentImplementationInvoker(
    original: ComponentImplementationInvoker,
    originalDefinitionReturnType: Option[TypingResult]
) extends ComponentImplementationInvoker {

  def this(componentDefinition: ComponentDefinitionWithImplementation) = {
    this(
      componentDefinition.implementationInvoker,
      returnType(componentDefinition)
    )
  }

  override def invokeMethod(params: Params, outputVariableNameOpt: Option[String], additional: Seq[AnyRef]): Any = {
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

    val originalValue = original.invokeMethod(params, outputVariableNameOpt, additional)
    originalValue match {
      case contextTransformation: ContextTransformation =>
        contextTransformation.copy(implementation = transform(contextTransformation.implementation))
      case componentExecutor => transform(componentExecutor)
    }
  }

  def handleInvoke(impl: Any, typingResult: TypingResult, nodeId: NodeId): Any
}

object StubbedComponentImplementationInvoker {

  private def returnType(componentDefinition: ComponentDefinitionWithImplementation): Option[TypingResult] = {
    componentDefinition match {
      case methodBasedDefinition: MethodBasedComponentDefinitionWithImplementation => methodBasedDefinition.returnType
      case _: DynamicComponentDefinitionWithImplementation                         => None
    }
  }

}
