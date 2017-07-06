package pl.touk.esp.engine.graph

import pl.touk.esp.engine.definition.DefinitionExtractor
import pl.touk.esp.engine.graph.evaluatedparam.Parameter
import sink.SinkRef
import pl.touk.esp.engine.graph.expression.Expression
import pl.touk.esp.engine.graph.service.ServiceRef
import pl.touk.esp.engine.graph.source.SourceRef
import pl.touk.esp.engine.graph.subprocess.SubprocessRef
import pl.touk.esp.engine.graph.variable.Field

object node {

  sealed trait Node {
    def data: NodeData
    def id: String = data.id
  }

  sealed trait OneOutputNode extends Node {
    def next: SubsequentNode
  }

  case class SourceNode(data: StartingNodeData, next: SubsequentNode) extends OneOutputNode

  sealed trait SubsequentNode extends Node

  case class OneOutputSubsequentNode(data: OneOutputSubsequentNodeData, next: SubsequentNode) extends OneOutputNode with SubsequentNode

  case class FilterNode(data: Filter, nextTrue: SubsequentNode, nextFalse: Option[SubsequentNode] = None) extends SubsequentNode

  //this should never occur in process to be run (unresolved)
  case class SubprocessNode(data: SubprocessInput, nexts: Map[String, SubsequentNode]) extends SubsequentNode

  case class SwitchNode(data: Switch, nexts: List[Case], defaultNext: Option[SubsequentNode] = None) extends SubsequentNode

  case class SplitNode(data: Split, nextParts: List[SubsequentNode]) extends SubsequentNode

  case class Case(expression: Expression, node: SubsequentNode)

  case class EndingNode(data: EndingNodeData) extends SubsequentNode

  trait UserDefinedAdditionalNodeFields
  sealed trait NodeData {
    def id: String
    def additionalFields: Option[UserDefinedAdditionalNodeFields]
  }

  trait Disableable {

    def isDisabled: Option[Boolean]
  }

  sealed trait OneOutputSubsequentNodeData extends NodeData

  sealed trait EndingNodeData extends NodeData

  sealed trait StartingNodeData extends NodeData

  case class Source(id: String, ref: SourceRef, additionalFields: Option[UserDefinedAdditionalNodeFields] = None) extends StartingNodeData

  case class Filter(id: String, expression: Expression, isDisabled: Option[Boolean] = None,
                    additionalFields: Option[UserDefinedAdditionalNodeFields] = None) extends NodeData with Disableable

  case class Switch(id: String, expression: Expression, exprVal: String, additionalFields: Option[UserDefinedAdditionalNodeFields] = None) extends NodeData

  case class VariableBuilder(id: String, varName: String, fields: List[Field], additionalFields: Option[UserDefinedAdditionalNodeFields] = None) extends OneOutputSubsequentNodeData

  case class Variable(id: String, varName: String, value: Expression, additionalFields: Option[UserDefinedAdditionalNodeFields] = None) extends OneOutputSubsequentNodeData

  case class Enricher(id: String, service: ServiceRef, output: String, additionalFields: Option[UserDefinedAdditionalNodeFields] = None) extends OneOutputSubsequentNodeData

  case class CustomNode(id: String, outputVar: Option[String], nodeType: String, parameters: List[Parameter], additionalFields: Option[UserDefinedAdditionalNodeFields] = None) extends OneOutputSubsequentNodeData

  case class Split(id: String, additionalFields: Option[UserDefinedAdditionalNodeFields] = None) extends NodeData

  case class Processor(id: String, service: ServiceRef, isDisabled: Option[Boolean] = None, additionalFields: Option[UserDefinedAdditionalNodeFields] = None) extends OneOutputSubsequentNodeData with EndingNodeData with Disableable

  case class Sink(id: String, ref: SinkRef, endResult: Option[Expression] = None, additionalFields: Option[UserDefinedAdditionalNodeFields] = None) extends EndingNodeData

  case class SubprocessInput(id: String, ref: SubprocessRef,
                             additionalFields: Option[UserDefinedAdditionalNodeFields] = None) extends OneOutputSubsequentNodeData with EndingNodeData


  //this is used after resolving subprocess, used for detecting when subprocess ends and context should change
  case class SubprocessOutput(id: String, outputName: String, additionalFields: Option[UserDefinedAdditionalNodeFields] = None)
    extends OneOutputSubsequentNodeData

  //this is used only in subprocess definition
  case class SubprocessInputDefinition(id: String,
                                      //TODO: should it be separate class?
                                       parameters: List[DefinitionExtractor.Parameter],
                                       additionalFields: Option[UserDefinedAdditionalNodeFields] = None) extends StartingNodeData

  //this is used only in subprocess definition
  case class SubprocessOutputDefinition(id: String, outputName: String, additionalFields: Option[UserDefinedAdditionalNodeFields] = None)
    extends EndingNodeData

  //it has to be here, otherwise it won't compile...
  def prefixNodeId[T<:NodeData](prefix: List[String], nodeData: T) : T = {
    import pl.touk.esp.engine.util.copySyntax._
    nodeData.asInstanceOf[NodeData].copy(id = (prefix :+ nodeData.id).mkString("-")).asInstanceOf[T]
  }
}
