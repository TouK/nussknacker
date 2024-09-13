package pl.touk.nussknacker.engine.management.sample.source

import org.apache.flink.streaming.api.datastream.DataStreamSource
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.api.component.{KafkaSourceOffset, UnboundedStreamComponent}
import pl.touk.nussknacker.engine.api.definition.Parameter
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

      override def activityParametersDefinition: Map[String, List[Parameter]] = Map(
        ScenarioActionName.Deploy.value -> List(
          Parameter(ParameterName("offset"), Typed.apply[Long])
        )
      )

      override protected def createSourceStream[T](
          list: List[T],
          env: StreamExecutionEnvironment,
          flinkNodeContext: FlinkCustomNodeContext
      ): DataStreamSource[T] = {
        val deploymentDataOpt = flinkNodeContext.nodeDeploymentData.collect { case d: KafkaSourceOffset => d }
        val elementsWithOffset = deploymentDataOpt match {
          case Some(data) => list.drop(data.offset.toInt)
          case _          => list
        }
        super.createSourceStream(elementsWithOffset, env, flinkNodeContext)
      }

    }

}
