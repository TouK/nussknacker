package pl.touk.process.model.graph

import pl.touk.process.model.graph.expression.Expression
import pl.touk.process.model.graph.processor.ProcessorRef

import scala.language.implicitConversions

object node {

  implicit def meta(id: String): MetaData = MetaData(id)

  sealed trait Node {
    def metaData: MetaData
  }

  case class MetaData(id: String, description: String = "")

  case class StartNode(metaData: MetaData, next: Node) extends Node

  case class End(metaData: MetaData, endResult: Option[Expression] = None) extends Node

  case class Processor(metaData: MetaData, processor: ProcessorRef, next: Node) extends Node

  case class Enricher(metaData: MetaData, processor: ProcessorRef, output: String, next: Node) extends Node

  case class Filter(metaData: MetaData, expression: Expression, nextTrue: Node,
                    nextFalse: Option[Node] = Option.empty) extends Node

  case class Switch(metaData: MetaData, expression: Expression,
                    exprVal: String, nexts: List[(Expression, Node)],
                    defaultEnd: Option[Expression] = None) extends Node

}
