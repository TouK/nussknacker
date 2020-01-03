package pl.touk.nussknacker.engine.build

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.{BatchMetaData, Group, MetaData, ProcessAdditionalFields, StandaloneMetaData, StreamMetaData}
import pl.touk.nussknacker.engine.build.GraphBuilder.Creator
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.{EspProcess, evaluatedparam}

class ProcessMetaDataBuilder private[build](metaData: MetaData) {

  //TODO: exception when non-streaming process?
  def parallelism(p: Int) =
    new ProcessMetaDataBuilder(metaData.copy(typeSpecificData = StreamMetaData(Some(p))))

  //TODO: exception when non-standalone process?
  def path(p: Option[String]) =
    new ProcessMetaDataBuilder(metaData.copy(typeSpecificData = StandaloneMetaData(p)))

  def subprocessVersions(subprocessVersions: Map[String, Long]) =
    new ProcessMetaDataBuilder(metaData.copy(subprocessVersions = subprocessVersions))

  def additionalFields(description: Option[String] = None,
                       groups: Set[Group] = Set.empty,
                       properties: Map[String, String] = Map.empty) =
    new ProcessMetaDataBuilder(metaData.copy(
      additionalFields = Some(ProcessAdditionalFields(description, groups, properties)))
    )

  def exceptionHandler(params: (String, Expression)*) =
    new ProcessExceptionHandlerBuilder(ExceptionHandlerRef(params.map(evaluatedparam.Parameter.tupled).toList))

  def exceptionHandlerNoParams() = new ProcessExceptionHandlerBuilder(ExceptionHandlerRef(List.empty))

  class ProcessExceptionHandlerBuilder private[ProcessMetaDataBuilder](exceptionHandlerRef: ExceptionHandlerRef) {

    def source(id: String, typ: String, params: (String, Expression)*): ProcessGraphBuilder =
      new ProcessGraphBuilder(GraphBuilder.source(id, typ, params: _*).creator
          .andThen(r => EspProcess(metaData, exceptionHandlerRef, NonEmptyList.of(r))))

    class ProcessGraphBuilder private[ProcessExceptionHandlerBuilder](val creator: Creator[EspProcess])
      extends GraphBuilder[EspProcess] {

      override def build(inner: Creator[EspProcess]) = new ProcessGraphBuilder(inner)
    }

  }

}

object EspProcessBuilder {

  def id(id: String) =
    new ProcessMetaDataBuilder(MetaData(id, StreamMetaData()))

}

object BatchProcessBuilder {

  def id(id: String) =
    new ProcessMetaDataBuilder((MetaData(id, BatchMetaData())))
}

object StandaloneProcessBuilder {

  def id(id: ProcessName) =
    new ProcessMetaDataBuilder(MetaData(id.value, StandaloneMetaData(None)))

}