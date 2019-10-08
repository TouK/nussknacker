package pl.touk.nussknacker.engine.graph

import io.circe.{Decoder, Encoder}
import io.circe.generic.JsonCodec
import io.circe.generic.extras.Configuration
import org.apache.commons.lang3.ClassUtils
import pl.touk.nussknacker.engine.api.CirceUtil
import pl.touk.nussknacker.engine.api.typed.ClazzRef
import pl.touk.nussknacker.engine.graph.evaluatedparam.{BranchParameters, Parameter}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.NodeData
import pl.touk.nussknacker.engine.graph.node.SubprocessInputDefinition.SubprocessParameter
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.graph.subprocess.SubprocessRef
import pl.touk.nussknacker.engine.graph.variable.Field

import scala.util.Try
import scala.reflect.runtime.universe._

object node {

  //TODO JOIN: this is a bit artificial, as we need it to handle BranchEnd - which is not 'normal' node.
  //Tree structures probably should be phased out...
  sealed trait Node

  sealed trait NodeWithData extends Node {
    def data: NodeData
    def id: String = data.id
  }

  sealed trait OneOutputNode extends NodeWithData {
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

  case class BranchEnd(data: BranchEndData) extends SubsequentNode

  @JsonCodec case class UserDefinedAdditionalNodeFields(description: Option[String])

  sealed trait NodeData {
    def id: String
    def additionalFields: Option[UserDefinedAdditionalNodeFields]
  }

  sealed trait Disableable { self: NodeData =>

    def isDisabled: Option[Boolean]
  }

  sealed trait CustomNodeData extends NodeData with WithComponent {
    def nodeType: String
    def parameters: List[Parameter]
    def outputVar: Option[String]
  }

  trait WithComponent {
    def componentId: String
  }

  sealed trait OneOutputSubsequentNodeData extends NodeData

  sealed trait EndingNodeData extends NodeData

  sealed trait StartingNodeData extends NodeData

  sealed trait SourceNodeData extends StartingNodeData

  case class Source(id: String, ref: SourceRef, additionalFields: Option[UserDefinedAdditionalNodeFields] = None) extends SourceNodeData with WithComponent {
    override val componentId = ref.typ
  }

  // TODO JOIN: move branchParameters to BranchEnd
  case class Join(id: String, outputVar: Option[String], nodeType: String,
                  parameters: List[Parameter], branchParameters: List[BranchParameters],
                  additionalFields: Option[UserDefinedAdditionalNodeFields] = None) extends StartingNodeData with CustomNodeData {
    override val componentId = nodeType
  }

  case class Filter(id: String, expression: Expression, isDisabled: Option[Boolean] = None,
                    additionalFields: Option[UserDefinedAdditionalNodeFields] = None) extends NodeData with Disableable

  case class Switch(id: String, expression: Expression, exprVal: String, additionalFields: Option[UserDefinedAdditionalNodeFields] = None) extends NodeData

  case class VariableBuilder(id: String, varName: String, fields: List[Field], additionalFields: Option[UserDefinedAdditionalNodeFields] = None) extends OneOutputSubsequentNodeData

  case class Variable(id: String, varName: String, value: Expression, additionalFields: Option[UserDefinedAdditionalNodeFields] = None) extends OneOutputSubsequentNodeData

  case class Enricher(id: String, service: ServiceRef, output: String, additionalFields: Option[UserDefinedAdditionalNodeFields] = None) extends OneOutputSubsequentNodeData with WithComponent {
    override val componentId = service.id
  }

  case class CustomNode(id: String, outputVar: Option[String], nodeType: String, parameters: List[Parameter],
                        additionalFields: Option[UserDefinedAdditionalNodeFields] = None)
    extends OneOutputSubsequentNodeData with CustomNodeData {
    override val componentId = nodeType
  }

  case class Split(id: String, additionalFields: Option[UserDefinedAdditionalNodeFields] = None) extends NodeData

  case class Processor(id: String, service: ServiceRef, isDisabled: Option[Boolean] = None, additionalFields: Option[UserDefinedAdditionalNodeFields] = None) extends OneOutputSubsequentNodeData with EndingNodeData with Disableable with WithComponent {
    override val componentId = service.id
  }

  case class BranchEndData(id: String, definition: BranchEndDefinition) extends EndingNodeData {
    override val additionalFields: Option[UserDefinedAdditionalNodeFields] = None
  }

  @JsonCodec case class BranchEndDefinition(id: String, joinId: String)

  case class Sink(
                   id: String,
                   ref: SinkRef,
                   endResult: Option[Expression] = None,
                   isDisabled: Option[Boolean] = None,
                   additionalFields: Option[UserDefinedAdditionalNodeFields] = None
                 ) extends EndingNodeData with WithComponent with Disableable {
    override val componentId = ref.typ
  }

  // TODO: A better way of passing information regarding subprocess parameter definition
  case class SubprocessInput(id: String,
                             ref: SubprocessRef,
                             additionalFields: Option[UserDefinedAdditionalNodeFields] = None,
                             isDisabled: Option[Boolean] = None,
                             subprocessParams: Option[List[SubprocessParameter]] = None) extends OneOutputSubsequentNodeData with EndingNodeData with WithComponent  with Disableable{
    override val componentId = ref.id
  }


  //this is used after resolving subprocess, used for detecting when subprocess ends and context should change
  case class SubprocessOutput(id: String, outputName: String, additionalFields: Option[UserDefinedAdditionalNodeFields] = None)
    extends OneOutputSubsequentNodeData

  //this is used only in subprocess definition
  case class SubprocessInputDefinition(id: String,
                                       parameters: List[SubprocessParameter],
                                       additionalFields: Option[UserDefinedAdditionalNodeFields] = None)
    extends SourceNodeData

  //this is used only in subprocess definition
  case class SubprocessOutputDefinition(id: String, outputName: String, additionalFields: Option[UserDefinedAdditionalNodeFields] = None)
    extends EndingNodeData

  //we don't use DefinitionExtractor.Parameter here, because this class should be serializable to json and Parameter has ClazzRef which has *real* class inside  
  //TODO: probably should be able to handle class parameters or typed maps
  //shape of this data should probably change, currently we leave it for backward compatibility
  object SubprocessInputDefinition {

    @JsonCodec case class SubprocessParameter(name: String, typ: SubprocessClazzRef)

    object SubprocessClazzRef {

      def apply[T: TypeTag]: SubprocessClazzRef = SubprocessClazzRef(ClazzRef.fromDetailedType[T].refClazzName)

    }

    @JsonCodec case class SubprocessClazzRef(refClazzName: String) {

      def toRuntimeClass(classLoader: ClassLoader): Try[Class[_]] =
        Try(ClassUtils.getClass(classLoader, refClazzName))

    }

  }

  //it has to be here, otherwise it won't compile...
  def prefixNodeId[T<:NodeData](prefix: List[String], nodeData: T) : T = {
    import pl.touk.nussknacker.engine.util.copySyntax._
    nodeData.asInstanceOf[NodeData].copy(id = (prefix :+ nodeData.id).mkString("-")).asInstanceOf[T]
  }

  //TODO: after migration to cats > 1.0.0 shapeless cast on node subclasses won't compile outside package :|
  import shapeless.syntax.typeable._

  def asSource(nodeData: NodeData): Option[Source] = nodeData.cast[Source]

  def asCustomNode(nodeData: NodeData): Option[CustomNode] = nodeData.cast[CustomNode]

  def asSubprocessInput(nodeData: NodeData): Option[SubprocessInput] = nodeData.cast[SubprocessInput]

  def asProcessor(nodeData: NodeData): Option[Processor] = nodeData.cast[Processor]


}

// we don't do this is in NodeData because: https://circe.github.io/circe/codecs/known-issues.html#knowndirectsubclasses-error
// seems our hierarchy is too complex for scala :)
object NodeDataCodec {

  import io.circe.generic.extras.semiauto._
  private implicit val config: Configuration = CirceUtil.configuration

  implicit val nodeDataEncoder: Encoder[NodeData] = deriveEncoder
  implicit val nodeDataDecoder: Decoder[NodeData] = deriveDecoder


}