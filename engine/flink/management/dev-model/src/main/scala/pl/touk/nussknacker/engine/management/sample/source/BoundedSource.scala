package pl.touk.nussknacker.engine.management.sample.source

import org.apache.flink.streaming.api.datastream.DataStreamSource
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.api.{MethodToInvoke, ParamName}
import pl.touk.nussknacker.engine.api.component.{ParameterConfig, UnboundedStreamComponent}
import pl.touk.nussknacker.engine.api.definition.RawParameterEditor
import pl.touk.nussknacker.engine.api.deployment.{ScenarioActionName, WithActionParametersSupport}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.SourceFactory
import pl.touk.nussknacker.engine.api.typed.typing.Unknown
import pl.touk.nussknacker.engine.flink.api.process.FlinkCustomNodeContext
import pl.touk.nussknacker.engine.flink.util.source.CollectionSource

import scala.jdk.CollectionConverters._
import scala.util.Try

object BoundedSource extends SourceFactory with UnboundedStreamComponent {

  @MethodToInvoke
  def source(@ParamName("elements") elements: java.util.List[Any]) =
    new CollectionSource[Any](elements.asScala.toList, None, Unknown)

}

object BoundedSourceWithOffset extends SourceFactory with UnboundedStreamComponent {

  private val OFFSET_PARAMETER_NAME: ParameterName = ParameterName("offset")

  @MethodToInvoke
  def source(@ParamName("elements") elements: java.util.List[Any]) =
    new CollectionSource[Any](elements.asScala.toList, None, Unknown) with WithActionParametersSupport {

      override def actionParametersDefinition: Map[ScenarioActionName, Map[ParameterName, ParameterConfig]] = {
        Map(
          ScenarioActionName.Deploy -> deployParameters
        )
      }

      override protected def createSourceStream[T](
          list: List[T],
          env: StreamExecutionEnvironment,
          flinkNodeContext: FlinkCustomNodeContext
      ): DataStreamSource[T] = {
        val offsetOpt =
          flinkNodeContext.componentUseContext
            .deploymentData()
            .flatMap(_.get(OFFSET_PARAMETER_NAME.value))
            .flatMap(s => Try(s.toInt).toOption)
        val elementsWithOffset = offsetOpt match {
          case Some(offset) => list.drop(offset)
          case None         => list
        }
        super.createSourceStream(elementsWithOffset, env, flinkNodeContext)
      }

    }

  private def deployParameters: Map[ParameterName, ParameterConfig] = {
    Map(
      OFFSET_PARAMETER_NAME -> ParameterConfig(
        defaultValue = None,
        editor = Some(RawParameterEditor),
        validators = None,
        label = Some("Offset"),
        hintText = Some(
          "Set offset to setup source to emit elements from specified start point in input collection. Empty field resets collection to the beginning."
        )
      ),
    )
  }

}
