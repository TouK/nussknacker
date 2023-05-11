package pl.touk.nussknacker.engine.process.compiler

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ContextTransformation
import pl.touk.nussknacker.engine.api.namespaces.ObjectNaming
import pl.touk.nussknacker.engine.api.process.{ComponentUseCase, ProcessConfigCreator, ProcessObjectDependencies}
import pl.touk.nussknacker.engine.api.typed.ReturningType
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectWithMethodDef
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ModelDefinitionWithTypes
import pl.touk.nussknacker.engine.graph.node.Source
import shapeless.syntax.typeable._

abstract class StubbedFlinkProcessCompiler(process: CanonicalProcess,
                                           creator: ProcessConfigCreator,
                                           processConfig: Config,
                                           diskStateBackendSupport: Boolean,
                                           objectNaming: ObjectNaming,
                                           componentUseCase: ComponentUseCase)
  extends FlinkProcessCompiler(creator, processConfig, diskStateBackendSupport, objectNaming, componentUseCase) {

  import pl.touk.nussknacker.engine.util.Implicits._

  override protected def definitions(processObjectDependencies: ProcessObjectDependencies): ModelDefinitionWithTypes = {
    val createdDefinitions = super.definitions(processObjectDependencies).modelDefinition

    val collectedSources = process.allStartNodes.map(_.head.data).collect {
      case source: Source => source
    }

    val usedSourceTypes = collectedSources.map(_.ref.typ)
    val stubbedSources =
      usedSourceTypes.map { sourceType =>
        val sourceDefinition = createdDefinitions.sourceFactories.getOrElse(sourceType, throw new IllegalArgumentException(s"Source $sourceType cannot be stubbed - missing definition"))
        val stubbedDefinition = prepareSourceFactory(sourceDefinition)
        sourceType -> stubbedDefinition
      }

    val stubbedServices = createdDefinitions.services.mapValuesNow(prepareService)

    ModelDefinitionWithTypes(
      createdDefinitions
        .copy(
          sourceFactories = createdDefinitions.sourceFactories ++ stubbedSources,
          services = stubbedServices))
  }

  protected def prepareService(service: ObjectWithMethodDef): ObjectWithMethodDef

  protected def prepareSourceFactory(sourceFactory: ObjectWithMethodDef): ObjectWithMethodDef

  protected def overrideObjectWithMethod(original: ObjectWithMethodDef, overrideFromOriginalAndType: (Any, Option[TypingResult], NodeId) => Any): ObjectWithMethodDef = {
    original.withImplementationInvoker((params: Map[String, Any], outputVariableNameOpt: Option[String], additional: Seq[AnyRef]) => {
      //this is needed to be able to handle dynamic types in tests
      def transform(impl: Any): Any = {
        val typingResult = impl.cast[ReturningType].map(rt => Some(rt.returnType)).getOrElse(original.returnType)
        val nodeId = additional.collectFirst {
          case nodeId: NodeId => nodeId
        }.getOrElse(throw new IllegalArgumentException("Node id is missing in additional parameters"))
        overrideFromOriginalAndType(impl, typingResult, nodeId)
      }

      val originalValue = original.implementationInvoker.invokeMethod(params, outputVariableNameOpt, additional)
      originalValue match {
        case e: ContextTransformation =>
          e.copy(implementation = transform(e.implementation))
        case e => transform(e)
      }
    })
  }

}
