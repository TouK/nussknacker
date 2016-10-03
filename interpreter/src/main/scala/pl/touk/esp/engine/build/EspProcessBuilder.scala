package pl.touk.esp.engine.build

import pl.touk.esp.engine.api.MetaData
import pl.touk.esp.engine.build.GraphBuilder.Creator
import pl.touk.esp.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.esp.engine.graph.{EspProcess, param}

class ProcessMetaDataBuilder private[build](metaData: MetaData) {

  def parallelism(p: Int) =
    new ProcessMetaDataBuilder(metaData.copy(parallelism = Some(p)))

  def exceptionHandler(params: (String, String)*) =
    new ProcessExceptionHandlerBuilder(ExceptionHandlerRef(params.map(param.Parameter.tupled).toList))

  class ProcessExceptionHandlerBuilder private[ProcessMetaDataBuilder](exceptionHandlerRef: ExceptionHandlerRef) {

    def source(id: String, typ: String, params: (String, String)*): ProcessGraphBuilder =
      new ProcessGraphBuilder(GraphBuilder.source(id, typ, params: _*).creator
          .andThen(EspProcess(metaData, exceptionHandlerRef, _)))

    class ProcessGraphBuilder private[ProcessExceptionHandlerBuilder](val creator: Creator[EspProcess])
      extends GraphBuilder[EspProcess] {

      override def build(inner: Creator[EspProcess]) = new ProcessGraphBuilder(inner)
    }

  }

}

object EspProcessBuilder {

  def id(id: String) =
    new ProcessMetaDataBuilder(MetaData(id))

}