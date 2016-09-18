package pl.touk.esp.engine.build

import pl.touk.esp.engine.api.MetaData
import pl.touk.esp.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.esp.engine.graph.expression.Expression
import pl.touk.esp.engine.graph.node._
import pl.touk.esp.engine.graph.{EspProcess, param}

import scala.concurrent.duration.Duration

class ProcessMetaDataBuilder private[build](metaData: MetaData) {

  def parallelism(p: Int) =
    new ProcessMetaDataBuilder(metaData.copy(parallelism = Some(p)))

  def exceptionHandler(params: (String, String)*) =
    new ProcessExceptionHandlerBuilder(ExceptionHandlerRef(params.map(param.Parameter.tupled).toList))

  class ProcessExceptionHandlerBuilder private[ProcessMetaDataBuilder](exceptionHandlerRef: ExceptionHandlerRef) {

    def source(id: String, typ: String, params: (String, String)*): ProcessGraphBuilder =
      new ProcessGraphBuilder(GraphBuilder.source(id, typ, params: _*))

    class ProcessGraphBuilder private[ProcessExceptionHandlerBuilder](graphBuilder: GraphBuilder[Source]) {

      def buildVariable(id: String, varName: String, fields: (String, Expression)*) =
        new ProcessGraphBuilder(graphBuilder.buildVariable(id, varName, fields: _*))

      def processor(id: String, svcId: String, params: (String, Expression)*) =
        new ProcessGraphBuilder(graphBuilder.processor(id, svcId, params: _*))

      def enricher(id: String, output: String, svcId: String, params: (String, Expression)*) =
        new ProcessGraphBuilder(graphBuilder.enricher(id, output, svcId, params: _*))

      def customNode(id: String, outputVar: String, customNodeRef: String, params: (String, Expression)*) =
        new ProcessGraphBuilder(graphBuilder.customNode(id, outputVar, customNodeRef, params: _*))

      def filter(id: String, expression: Expression, nextFalse: Option[Node] = Option.empty) =
        new ProcessGraphBuilder(graphBuilder.filter(id, expression, nextFalse))

      def aggregate(id: String, aggregatedVar: String,
                    keyExpression: Expression, duration: Duration, step: Duration,
                    triggerExpression: Option[Expression] = None, foldingFunRef: Option[String] = None) =
        new ProcessGraphBuilder(graphBuilder.aggregate(id, aggregatedVar, keyExpression,
          duration, step, triggerExpression, foldingFunRef))

      def sink(id: String, typ: String, params: (String, String)*) =
        create(graphBuilder.sink(id, typ, params: _*))

      def sink(id: String, expression: Expression, typ: String, params: (String, String)*) =
        create(graphBuilder.sink(id, expression, typ, params: _*))

      def processorEnd(id: String, svcId: String, params: (String, Expression)*) =
        create(graphBuilder.processorEnd(id, svcId, params: _*))

      def switch(id: String, expression: Expression, exprVal: String, nexts: Case*) =
        create(graphBuilder.switch(id, expression, exprVal, nexts: _*))

      def switch(id: String, expression: Expression, exprVal: String,
                 defaultNext: Node, nexts: Case*) =
        create(graphBuilder.switch(id, expression, exprVal, defaultNext, nexts: _*))

      def to(node: Node) =
        create(graphBuilder.to(node))

      private def create(source: Source): EspProcess =
        EspProcess(metaData, exceptionHandlerRef, source)

    }

  }

}

object EspProcessBuilder {

  def id(id: String) =
    new ProcessMetaDataBuilder(MetaData(id))

}