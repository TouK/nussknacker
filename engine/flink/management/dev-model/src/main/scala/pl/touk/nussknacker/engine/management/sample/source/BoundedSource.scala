package pl.touk.nussknacker.engine.management.sample.source

import pl.touk.nussknacker.engine.api.process.SourceFactory
import pl.touk.nussknacker.engine.api.typed.typing.Unknown
import pl.touk.nussknacker.engine.api.{MethodToInvoke, ParamName}
import pl.touk.nussknacker.engine.flink.util.source.CollectionSource

import scala.collection.JavaConverters._
import org.apache.flink.streaming.api.scala._

object BoundedSource extends SourceFactory {

  @MethodToInvoke
  def source(@ParamName("elements") elements: java.util.List[Any]) =
    new CollectionSource[Any](elements.asScala.toList, None, Unknown)

}
