package pl.touk.nussknacker.engine.graph

import io.circe._
import io.circe.generic.JsonCodec
import io.circe.generic.extras.{ConfiguredJsonCodec, JsonKey}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder, deriveUnwrappedCodec}
import org.apache.commons.lang3.ClassUtils
import pl.touk.nussknacker.engine.api.{JoinReference, LayoutData}
import pl.touk.nussknacker.engine.api.CirceUtil._
import pl.touk.nussknacker.engine.api.definition.FixedExpressionValue
import pl.touk.nussknacker.engine.api.parameter.{
  ParameterName,
  ParameterValueCompileTimeValidation,
  ParameterValueInput
}
import pl.touk.nussknacker.engine.graph.evaluatedparam.{BranchParameters, Parameter => NodeParameter}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.fragment.FragmentRef
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.FragmentParameter
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.graph.variable.Field

import scala.reflect.ClassTag
import scala.util.Try

object node {

  // TODO JOIN: this is a bit artificial, as we need it to handle BranchEnd - which is not 'normal' node.
  // Tree structures probably should be phased out...
  sealed trait Node

  sealed trait NodeWithData extends Node {
    def data: NodeData

    def id: String = data.id
  }

  sealed trait OneOutputNode extends NodeWithData {
    def next: Option[SubsequentNode]
  }

  case class SourceNode(data: StartingNodeData, next: Option[SubsequentNode]) extends OneOutputNode

  sealed trait SubsequentNode extends Node

  case class OneOutputSubsequentNode(data: OneOutputSubsequentNodeData, next: Option[SubsequentNode])
      extends OneOutputNode
      with SubsequentNode

  case class FilterNode(data: Filter, nextTrue: Option[SubsequentNode], nextFalse: Option[SubsequentNode] = None)
      extends SubsequentNode

  // this should never occur in process to be run (unresolved)
  case class FragmentNode(data: FragmentInput, nexts: Map[String, Option[SubsequentNode]]) extends SubsequentNode

  // defaultNext is deprecated, will be removed in future versions
  case class SwitchNode(data: Switch, nexts: List[Case], defaultNext: Option[SubsequentNode] = None)
      extends SubsequentNode

  case class SplitNode(data: Split, nextParts: List[SubsequentNode]) extends SubsequentNode

  case class Case(expression: Expression, node: Option[SubsequentNode])

  object Case {
    def apply(expression: Expression, node: SubsequentNode): Case = Case(expression, Some(node))
  }

  case class EndingNode(data: EndingNodeData) extends SubsequentNode

  case class BranchEnd(data: BranchEndData) extends SubsequentNode

  @JsonCodec case class UserDefinedAdditionalNodeFields(description: Option[String], layoutData: Option[LayoutData])

  sealed trait NodeData {
    def id: String

    def additionalFields: Option[UserDefinedAdditionalNodeFields]

    // TODO: after migration to cats > 1.0.0 shapeless cast on node subclasses won't compile outside package :|

    import shapeless.syntax.typeable._

    def asSource: Option[Source] = this.cast[Source]

    def asFragmentInputDefinition: Option[FragmentInputDefinition] =
      this.cast[FragmentInputDefinition]

    def asProcessor: Option[Processor] = this.cast[Processor]

    lazy val parametersOrEmpty: List[NodeParameter] = this.cast[WithParameters].map(_.parameters).getOrElse(List.empty)

    lazy val branchParametersOrEmpty: List[BranchParameters] =
      this.cast[Join].map(_.branchParameters).getOrElse(List.empty)

  }

  object NodeData {
    implicit val nodeDataEncoder: Encoder[NodeData] = deriveConfiguredEncoder
    implicit val nodeDataDecoder: Decoder[NodeData] = deriveConfiguredDecoder
  }

  // this represents node that originates from real node on UI, in contrast with Branch
  sealed trait RealNodeData extends NodeData

  sealed trait Disableable {
    self: NodeData =>

    def isDisabled: Option[Boolean]
  }

  trait WithOptionalOutputVar { self: NodeData =>

    def outputVar: Option[String]

  }

  sealed trait CustomNodeData
      extends CompilableNodeData
      with WithComponent
      with RealNodeData
      with WithParameters
      with WithOptionalOutputVar {
    // TODO: rename to componentId
    def nodeType: String

    def parameters: List[NodeParameter]

  }

  trait WithComponent {
    def componentId: String
  }

  /*
  When a new implementation of WithParameters is added, the new type needs also to be handled on FE
  in order to properly display dynamic parameters in NodeDetailsContent.
  See: ParametersUtils.ts/#parametersPath and ParametersUtils.ts/#adjustParameters
   */
  trait WithParameters {
    def parameters: List[NodeParameter]
  }

  trait WithFields {
    def fields: List[Field]
  }

  sealed trait CompilableNodeData extends NodeData

  sealed trait OneOutputSubsequentNodeData extends NodeData with RealNodeData

  sealed trait EndingNodeData extends NodeData

  sealed trait StartingNodeData extends NodeData

  sealed trait SourceNodeData extends StartingNodeData with CompilableNodeData

  sealed trait DeadEndingData extends NodeData

  case class Source(id: String, ref: SourceRef, additionalFields: Option[UserDefinedAdditionalNodeFields] = None)
      extends SourceNodeData
      with WithComponent
      with RealNodeData
      with WithParameters {
    override val componentId: String = ref.typ

    override def parameters: List[NodeParameter] = ref.parameters
  }

  // TODO JOIN: move branchParameters to BranchEnd
  case class Join(
      id: String,
      outputVar: Option[String],
      // TODO: rename to componentId
      nodeType: String,
      parameters: List[NodeParameter],
      branchParameters: List[BranchParameters],
      additionalFields: Option[UserDefinedAdditionalNodeFields] = None
  ) extends StartingNodeData
      with CustomNodeData {
    override val componentId: String = nodeType
  }

  case class Filter(
      id: String,
      expression: Expression,
      isDisabled: Option[Boolean] = None,
      additionalFields: Option[UserDefinedAdditionalNodeFields] = None
  ) extends NodeData
      with CompilableNodeData
      with Disableable
      with RealNodeData
      with DeadEndingData

  object Switch {

    def apply(id: String): Switch = Switch(id, None, None)

  }

  // expression and expressionVal are deprecated, will be removed in the future
  // TODO: rename to Choice
  case class Switch(
      id: String,
      expression: Option[Expression],
      exprVal: Option[String],
      additionalFields: Option[UserDefinedAdditionalNodeFields] = None
  ) extends NodeData
      with CompilableNodeData
      with RealNodeData
      with DeadEndingData

  // TODO: rename to RecordVariable
  case class VariableBuilder(
      id: String,
      varName: String,
      fields: List[Field],
      additionalFields: Option[UserDefinedAdditionalNodeFields] = None
  ) extends OneOutputSubsequentNodeData
      with WithFields
      with CompilableNodeData

  case class Variable(
      id: String,
      varName: String,
      value: Expression,
      additionalFields: Option[UserDefinedAdditionalNodeFields] = None
  ) extends OneOutputSubsequentNodeData
      with CompilableNodeData

  case class Split(id: String, additionalFields: Option[UserDefinedAdditionalNodeFields] = None)
      extends NodeData
      with RealNodeData

  sealed trait ServiceNodeData
      extends OneOutputSubsequentNodeData
      with CompilableNodeData
      with WithComponent
      with WithParameters
      with WithOptionalOutputVar {

    val service: ServiceRef

    override def componentId: String = service.id

    override def parameters: List[NodeParameter] = service.parameters

  }

  case class Enricher(
      id: String,
      override val service: ServiceRef,
      output: String,
      additionalFields: Option[UserDefinedAdditionalNodeFields] = None,
      mockExpression: Option[Expression] = None
  ) extends ServiceNodeData {

    override def outputVar: Option[String] = Some(output)

  }

  case class CustomNode(
      id: String,
      outputVar: Option[String],
      // TODO: rename to componentId
      nodeType: String,
      parameters: List[NodeParameter],
      additionalFields: Option[UserDefinedAdditionalNodeFields] = None
  ) extends OneOutputSubsequentNodeData
      with CustomNodeData
      with EndingNodeData {
    override val componentId: String = nodeType
  }

  case class Processor(
      id: String,
      service: ServiceRef,
      isDisabled: Option[Boolean] = None,
      additionalFields: Option[UserDefinedAdditionalNodeFields] = None
  ) extends ServiceNodeData
      with EndingNodeData
      with Disableable {

    override def outputVar: Option[String] = None

  }

  case class BranchEndData(definition: BranchEndDefinition) extends EndingNodeData {

    override val additionalFields: Option[UserDefinedAdditionalNodeFields] = None

    override val id: String = definition.artificialNodeId
  }

  // id - id of particular branch ending in joinId, currently on UI it's id of *previous* node (i.e. node where branch edge originates)
  // joinId - id of join node
  @JsonCodec case class BranchEndDefinition(id: String, joinId: String) {

    // in CanonicalProcess and EspProcess we have to add artifical node (BranchEnd), we use this generated, unique id
    // TODO: we're using this also in ProcessUtils.ts (findContextForBranch, findVariablesForBranches). This should be refactored, so
    // that we're passing ValidationContext for nodes explicitly
    def artificialNodeId: String = s"$$edge-$id-$joinId"

    // TODO: remove it and replace with sth more understandable
    def joinReference: JoinReference = JoinReference(artificialNodeId, id, joinId)
  }

  case class Sink(
      id: String,
      ref: SinkRef,
      // this field is left only to make it possible to write NodeMigration (see SinkExpressionMigration in generic)
      @JsonKey("endResult") legacyEndResultExpression: Option[Expression] = None,
      isDisabled: Option[Boolean] = None,
      additionalFields: Option[UserDefinedAdditionalNodeFields] = None
  ) extends EndingNodeData
      with CompilableNodeData
      with WithComponent
      with Disableable
      with RealNodeData
      with WithParameters {
    override val componentId: String = ref.typ

    override def parameters: List[NodeParameter] = ref.parameters
  }

  // TODO: A better way of passing information regarding fragment parameter definition
  case class FragmentInput(
      id: String,
      ref: FragmentRef,
      additionalFields: Option[UserDefinedAdditionalNodeFields] = None,
      isDisabled: Option[Boolean] = None,
      fragmentParams: Option[List[FragmentParameter]] = None
  ) extends OneOutputSubsequentNodeData
      with EndingNodeData
      with WithComponent
      with Disableable {
    override val componentId: String = ref.id
  }

  @JsonCodec final case class Dimensions(width: Long, height: Long)

  @JsonCodec final case class StickyNote(
      id: String,
      content: String,
      color: String,
      dimensions: Dimensions,
      additionalFields: Option[UserDefinedAdditionalNodeFields]
  )

  // this is used after resolving fragment, used for detecting when fragment ends and context should change
  case class FragmentUsageOutput(
      id: String,
      fragmentUsageStartNodeId: String,
      outputName: String,
      outputVar: Option[FragmentOutputVarDefinition],
      additionalFields: Option[UserDefinedAdditionalNodeFields] = None
  ) extends OneOutputSubsequentNodeData

  @JsonCodec case class FragmentOutputVarDefinition(name: String, fields: List[Field])

  // this is used only in fragment definition
  case class FragmentInputDefinition(
      id: String,
      parameters: List[FragmentParameter],
      additionalFields: Option[UserDefinedAdditionalNodeFields] = None
  ) extends SourceNodeData
      with RealNodeData

  // this is used only in fragment definition
  case class FragmentOutputDefinition(
      id: String,
      outputName: String,
      fields: List[Field] = List.empty,
      additionalFields: Option[UserDefinedAdditionalNodeFields] = None
  ) extends EndingNodeData
      with CompilableNodeData
      with RealNodeData
      with WithFields

  // we don't use DefinitionExtractor.Parameter here, because this class should be serializable to json and Parameter has TypedResult which has *real* class inside
  // TODO: probably should be able to handle class parameters or typed maps (i.e. use TypingResult inside FragmentClazzRef)
  // shape of this data should probably change, currently we leave it for backward compatibility
  object FragmentInputDefinition {

    private implicit val parameterNameCodec: Codec[ParameterName] = deriveUnwrappedCodec

    @ConfiguredJsonCodec
    case class FragmentParameter(
        name: ParameterName,
        typ: FragmentClazzRef,
        required: Boolean = false,
        initialValue: Option[FixedExpressionValue],
        hintText: Option[String],
        valueEditor: Option[ParameterValueInput],
        valueCompileTimeValidation: Option[ParameterValueCompileTimeValidation]
    ) {

      def strictTypeChecking: Option[Boolean] = {
        valueCompileTimeValidation.flatMap(_.strictTypeChecking)
      }

    }

    object FragmentParameter {

      def apply(name: ParameterName, typ: FragmentClazzRef): FragmentParameter = {
        FragmentParameter(
          name,
          typ,
          required = false,
          initialValue = None,
          hintText = None,
          valueEditor = None,
          valueCompileTimeValidation = None
        )
      }

    }

    object FragmentClazzRef {

      def apply[T: ClassTag]: FragmentClazzRef = FragmentClazzRef(implicitly[ClassTag[T]].runtimeClass.getName)

    }

    @JsonCodec case class FragmentClazzRef(refClazzName: String)

  }

  val IdFieldName = "$id"
  // TODO: move these FragmentInputDefinition related vals/def under FragmentInputDefinition for better organization
  val ParameterFieldNamePrefix      = "$param"
  val ParameterNameFieldName        = "$name"
  val InitialValueFieldName         = "$initialValue"
  val InputModeFieldName            = "$inputMode"
  val TypFieldName                  = "$typ"
  val FixedValuesListFieldName      = "$fixedValuesList"
  val DictIdFieldName               = "$dictId"
  val ValidationExpressionFieldName = "$validationExpression"

  def qualifiedParamFieldName(
      paramName: ParameterName,
      subFieldName: Option[String]
  ): ParameterName = // for example: "$param.P1.$initialValue"
    ParameterName {
      subFieldName match {
        case Some(subField) => ParameterFieldNamePrefix + "." + paramName.value + "." + subField
        case None           => ParameterFieldNamePrefix + "." + paramName
      }
    }

  def recordKeyFieldName(index: Int)   = s"$$fields-$index-$$key"
  def recordValueFieldName(index: Int) = s"$$fields-$index-$$value"

}
