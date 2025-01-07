package pl.touk.nussknacker.engine.management.sample.source

import org.apache.flink.streaming.api.datastream.DataStreamSource
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.api.component.{ParameterConfig, UnboundedStreamComponent}
import pl.touk.nussknacker.engine.api.definition.{FixedExpressionValue, FixedValuesParameterEditor, RawParameterEditor}
import pl.touk.nussknacker.engine.api.deployment.ScenarioActionName
import pl.touk.nussknacker.engine.api.editor.FixedValuesEditorMode
import pl.touk.nussknacker.engine.api.process.{SourceFactory, WithActivityParameters}
import pl.touk.nussknacker.engine.api.typed.typing.Unknown
import pl.touk.nussknacker.engine.api.{MethodToInvoke, ParamName}
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

  val OFFSET_PARAMETER_NAME = "offset"

  @MethodToInvoke
  def source(@ParamName("elements") elements: java.util.List[Any]) =
    new CollectionSource[Any](elements.asScala.toList, None, Unknown) with WithActivityParameters {

      override def activityParametersDefinition: Map[String, Map[String, ParameterConfig]] = {
        Map(
          ScenarioActionName.Deploy.value -> deployActivityParameters
        )
      }

      override protected def createSourceStream[T](
          list: List[T],
          env: StreamExecutionEnvironment,
          flinkNodeContext: FlinkCustomNodeContext
      ): DataStreamSource[T] = {
        val offsetOpt =
          flinkNodeContext.nodeDeploymentData
            .flatMap(_.get(OFFSET_PARAMETER_NAME))
            .flatMap(s => Try(s.toInt).toOption)
        val elementsWithOffset = offsetOpt match {
          case Some(offset) => list.drop(offset)
          case _            => list
        }
        super.createSourceStream(elementsWithOffset, env, flinkNodeContext)
      }

    }

  private def deployActivityParameters: Map[String, ParameterConfig] = {
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
