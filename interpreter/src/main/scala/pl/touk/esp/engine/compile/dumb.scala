package pl.touk.esp.engine.compile

import pl.touk.esp.engine.api.process.{Sink, Source}
import pl.touk.esp.engine.api.{FoldingFunction, MetaData}
import pl.touk.esp.engine.definition.{ServiceInvoker, SinkCreator, SourceCreator}
import pl.touk.esp.engine.graph.{sink, source}

import scala.concurrent.ExecutionContext

object dumb {

  object DumbServiceInvoker extends ServiceInvoker {

    override def invoke(params: Map[String, Any])
                       (implicit ec: ExecutionContext) = throw new IllegalAccessException("Dumb service shouldn't be invoked")

  }

  object DumbSourceCreator extends SourceCreator[Any] {

    override def create(processMetaData: MetaData, params: List[source.Parameter]): Source[Any] =
      new Source[Any] {
        override def toFlinkSource = throw new IllegalAccessException("Dumb source shouldn't be used")

        override def typeInformation = throw new IllegalAccessException("Dumb source shouldn't be used")

        override def timestampAssigner = throw new IllegalAccessException("Dumb source shouldn't be used")
      }
  }

  object DumbSinkCreator extends SinkCreator {
    override def create(processMetaData: MetaData, params: List[sink.Parameter]): Sink =
      new Sink {
        override def toFlinkFunction = throw new IllegalAccessException("Dumb sink shouldn't be used")
      }
  }



  object DumbFoldingFunction extends FoldingFunction[Any] {

    override def fold(value: AnyRef, acc: Option[Any]) = throw new IllegalAccessException("Dumb folding function shouldn't be invoked")

  }

}
