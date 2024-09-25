package pl.touk.nussknacker.engine.management.sample.source

import org.apache.flink.streaming.api.datastream.DataStreamSource
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.api.component.{ParameterConfig, UnboundedStreamComponent}
import pl.touk.nussknacker.engine.api.definition.{FixedExpressionValue, FixedValuesParameterEditor, RawParameterEditor}
import pl.touk.nussknacker.engine.api.deployment.ScenarioActionName
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.{SourceFactory, WithActivityParameters}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, Unknown}
import pl.touk.nussknacker.engine.api.{MethodToInvoke, ParamName}
import pl.touk.nussknacker.engine.flink.api.process.FlinkCustomNodeContext
import pl.touk.nussknacker.engine.flink.util.source.CollectionSource

import scala.jdk.CollectionConverters._

object BoundedSource extends SourceFactory with UnboundedStreamComponent {

  @MethodToInvoke
  def source(@ParamName("elements") elements: java.util.List[Any]) =
    new CollectionSource[Any](elements.asScala.toList, None, Unknown)

}

object BoundedSourceWithOffset extends SourceFactory with UnboundedStreamComponent {

  @MethodToInvoke
  def source(@ParamName("elements") elements: java.util.List[Any]) =
    new CollectionSource[Any](elements.asScala.toList, None, Unknown) with WithActivityParameters {

      override def activityParametersDefinition: Map[String, Map[String, ParameterConfig]] = {
        val fixedValuesEditor = Some(
          FixedValuesParameterEditor(
            List(
              FixedExpressionValue("LATEST", "LATEST"),
              FixedExpressionValue("EARLIEST", "EARLIEST"),
              FixedExpressionValue("NONE", "NONE"),
            )
          )
        )
        Map(
          ScenarioActionName.Deploy.value -> Map(
            "offset" -> ParameterConfig(
              defaultValue = None,
              editor = Some(RawParameterEditor),
              validators = None,
              label = None,
              hintText = Some(
                "Set offset to setup source to emit elements from specified start point in input collection. Empty field resets collection to the beginning."
              )
            ),
            "offsetResetStrategy" -> ParameterConfig(
              defaultValue = Some("EARLIEST"),
              editor = fixedValuesEditor,
              validators = None,
              label = None,
              hintText = Some("Example of parameter with fixed values")
            ),
          )
        )
      }

      override protected def createSourceStream[T](
          list: List[T],
          env: StreamExecutionEnvironment,
          flinkNodeContext: FlinkCustomNodeContext
      ): DataStreamSource[T] = {
        val offsetOpt = flinkNodeContext.nodeDeploymentData.flatMap(_.get("offset"))
        val elementsWithOffset = offsetOpt match {
          case Some(offset) => list.drop(offset.toInt)
          case _            => list
        }
        super.createSourceStream(elementsWithOffset, env, flinkNodeContext)
      }

    }

}
