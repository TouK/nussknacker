package pl.touk.nussknacker.engine.process.compiler

import com.typesafe.config.Config
import org.apache.flink.api.common.serialization.DeserializationSchema
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.datastream.{ConnectedStreams, DataStream}
import pl.touk.nussknacker.engine.api.context.ContextTransformation
import pl.touk.nussknacker.engine.api.namespaces.ObjectNaming
import pl.touk.nussknacker.engine.api.process.{ComponentUseCase, ProcessConfigCreator, ProcessObjectDependencies}
import pl.touk.nussknacker.engine.api.typed.ReturningType
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.{ObjectWithMethodDef, OverriddenObjectWithMethodDef}
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor
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

  override protected def definitions(processObjectDependencies: ProcessObjectDependencies): ProcessDefinitionExtractor.ProcessDefinition[ObjectWithMethodDef] = {
    val createdDefinitions = super.definitions(processObjectDependencies)

    val collectedSources = checkSources(process.allStartNodes.map(_.head.data).collect {
      case source: Source => source
    })

    val usedSourceTypes = collectedSources.map(_.ref.typ)
    val stubbedSources =
      usedSourceTypes.map { sourceType =>
        val sourceDefinition = createdDefinitions.sourceFactories.getOrElse(sourceType, throw new IllegalArgumentException(s"Source $sourceType cannot be stubbed - missing definition"))
        val stubbedDefinition = prepareSourceFactory(sourceDefinition)
        sourceType -> stubbedDefinition
      }

    val stubbedServices = createdDefinitions.services.mapValuesNow(prepareService)

    createdDefinitions
      .copy(
        sourceFactories = createdDefinitions.sourceFactories ++ stubbedSources,
        services = stubbedServices)
  }

  protected def checkSources(sources: List[Source]): List[Source] = sources

  protected def prepareService(service: ObjectWithMethodDef): ObjectWithMethodDef

  protected def prepareSourceFactory(sourceFactory: ObjectWithMethodDef): ObjectWithMethodDef


  protected def overrideObjectWithMethod(original: ObjectWithMethodDef, overrideFromOriginalAndType: (Any, TypingResult) => Any): ObjectWithMethodDef =
    new OverriddenObjectWithMethodDef(original) {
      override def invokeMethod(params: Map[String, Any], outputVariableNameOpt: Option[String], additional: Seq[AnyRef]): Any = {
        //this is needed to be able to handle dynamic types in tests
        def transform(impl: Any): Any = overrideFromOriginalAndType(impl, impl.cast[ReturningType].map(_.returnType).getOrElse(original.returnType))

        val originalValue = original.invokeMethod(params, outputVariableNameOpt, additional)
        originalValue match {
          case e: ContextTransformation =>
            e.copy(implementation = transform(e.implementation))
          case e => transform(e)
        }
      }
    }

}
