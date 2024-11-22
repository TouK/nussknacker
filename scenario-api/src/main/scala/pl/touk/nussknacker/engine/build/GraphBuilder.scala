package pl.touk.nussknacker.engine.build

import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.graph.evaluatedparam.BranchParameters
import pl.touk.nussknacker.engine.graph.evaluatedparam.{Parameter => NodeParameter}
import pl.touk.nussknacker.engine.graph.expression._
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{FragmentClazzRef, FragmentParameter}
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.graph.fragment.FragmentRef
import pl.touk.nussknacker.engine.graph.variable._
import pl.touk.nussknacker.engine.graph.{EdgeType, node}

trait GraphBuilder[R] {

  def creator: GraphBuilder.Creator[R]

  def build(inner: GraphBuilder.Creator[R]): GraphBuilder[R]

  def source(id: String, typ: String, params: (String, Expression)*): GraphBuilder[SourceNode] =
    new SimpleGraphBuilder(
      SourceNode(Source(id, SourceRef(typ, toNodeParameters(params))), _)
    )

  def buildVariable(id: String, varName: String, fields: (String, Expression)*): GraphBuilder[R] =
    build(node =>
      creator(OneOutputSubsequentNode(VariableBuilder(id, varName, fields.map(f => Field(f._1, f._2)).toList), node))
    )

  def buildSimpleVariable(id: String, varName: String, value: Expression): GraphBuilder[R] =
    build(node => creator(OneOutputSubsequentNode(Variable(id, varName, value), node)))

  def enricher(id: String, output: String, svcId: String, params: (String, Expression)*): GraphBuilder[R] =
    build(node =>
      creator(
        OneOutputSubsequentNode(Enricher(id, ServiceRef(svcId, toNodeParameters(params)), output), node)
      )
    )

  def processor(id: String, svcId: String, params: (String, Expression)*): GraphBuilder[R] =
    build(node => creator(OneOutputSubsequentNode(Processor(id, ServiceRef(svcId, toNodeParameters(params))), node)))

  def disabledProcessor(id: String, svcId: String, params: (String, Expression)*): GraphBuilder[R] =
    build(node =>
      creator(
        OneOutputSubsequentNode(
          Processor(id, ServiceRef(svcId, toNodeParameters(params)), isDisabled = Some(true)),
          node
        )
      )
    )

  def fragmentOneOut(
      id: String,
      fragmentId: String,
      fragmentOutputDefinitionName: String,
      outputParamName: String,
      params: (String, Expression)*
  ): GraphBuilder[R] =
    build(node =>
      creator(
        FragmentNode(
          FragmentInput(
            id,
            FragmentRef(
              fragmentId,
              toNodeParameters(params),
              Map(fragmentOutputDefinitionName -> outputParamName)
            )
          ),
          Map(fragmentOutputDefinitionName -> node)
        )
      )
    )

  def fragment(
      id: String,
      fragmentId: String,
      params: List[(String, Expression)],
      outputParameters: Map[String, String],
      outputs: Map[String, SubsequentNode]
  ): R =
    creator(
      FragmentNode(
        FragmentInput(id, FragmentRef(fragmentId, toNodeParameters(params), outputParameters)),
        outputs
      )
    )

  def fragmentEnd(id: String, fragmentId: String, params: (String, Expression)*): R =
    creator(
      FragmentNode(
        FragmentInput(id, FragmentRef(fragmentId, toNodeParameters(params), Map.empty)),
        Map()
      )
    )

  def fragmentInput(id: String, params: (String, Class[_])*): GraphBuilder[SourceNode] =
    new SimpleGraphBuilder(
      SourceNode(
        FragmentInputDefinition(
          id = id,
          parameters = params.map(kv => FragmentParameter(ParameterName(kv._1), FragmentClazzRef(kv._2.getName))).toList
        ),
        _
      )
    )

  def fragmentInputWithRawParameters(id: String, params: FragmentParameter*): GraphBuilder[SourceNode] =
    new SimpleGraphBuilder(
      SourceNode(
        FragmentInputDefinition(
          id = id,
          parameters = params.toList
        ),
        _
      )
    )

  def fragmentOutput(id: String, outputName: String, params: (String, Expression)*): R =
    creator(EndingNode(FragmentOutputDefinition(id, outputName, params.map(kv => Field(kv._1, kv._2)).toList)))

  def filter(
      id: String,
      expression: Expression,
      disabled: Option[Boolean] = None,
      edgeType: EdgeType = EdgeType.FilterTrue
  ): GraphBuilder[R] =
    build(node =>
      creator(
        FilterNode(
          Filter(id, expression, disabled),
          Some(node).filter(_ => edgeType == EdgeType.FilterTrue),
          Some(node).filter(_ => edgeType == EdgeType.FilterFalse)
        )
      )
    )

  def filter(id: String, expression: Expression, nextFalse: SubsequentNode): GraphBuilder[R] =
    build(node => creator(FilterNode(Filter(id, expression), nextTrue = Some(node), nextFalse = Some(nextFalse))))

  def emptySink(id: String, typ: String, params: (String, Expression)*): R =
    creator(EndingNode(Sink(id, SinkRef(typ, toNodeParameters(params)))))

  def disabledSink(id: String, typ: String): R =
    creator(EndingNode(Sink(id, SinkRef(typ, List()), isDisabled = Some(true))))

  def processorEnd(id: String, svcId: String, params: (String, Expression)*): R =
    creator(EndingNode(Processor(id, ServiceRef(svcId, toNodeParameters(params)))))

  def disabledProcessorEnd(id: String, svcId: String, params: (String, Expression)*): R =
    creator(
      EndingNode(Processor(id, ServiceRef(svcId, toNodeParameters(params)), isDisabled = Some(true)))
    )

  def branchEnd(branchId: String, joinId: String): R =
    creator(BranchEnd(node.BranchEndData(BranchEndDefinition(branchId, joinId))))

  def switch(id: String, nexts: Case*): R =
    creator(SwitchNode(Switch(id), nexts.toList, None))

  def switch(id: String, expression: Expression, exprVal: String, nexts: Case*): R =
    creator(SwitchNode(Switch(id, Some(expression), Some(exprVal)), nexts.toList, None))

  def switch(id: String, expression: Expression, exprVal: String, defaultNext: SubsequentNode, nexts: Case*): R =
    creator(SwitchNode(Switch(id, Some(expression), Some(exprVal)), nexts.toList, Some(defaultNext)))

  def customNode(id: String, outputVar: String, customNodeRef: String, params: (String, Expression)*): GraphBuilder[R] =
    build(node =>
      creator(
        OneOutputSubsequentNode(
          CustomNode(id, Some(outputVar), customNodeRef, toNodeParameters(params)),
          node
        )
      )
    )

  // outputVar must be provided always when parameter with @OutputVariableName annotation is used - look into comment in @OutputVariableName
  def endingCustomNode(id: String, outputVar: Option[String], customNodeRef: String, params: (String, Expression)*): R =
    creator(EndingNode(CustomNode(id, outputVar, customNodeRef, toNodeParameters(params))))

  def customNodeNoOutput(id: String, customNodeRef: String, params: (String, Expression)*): GraphBuilder[R] =
    build(node =>
      creator(
        OneOutputSubsequentNode(CustomNode(id, None, customNodeRef, toNodeParameters(params)), node)
      )
    )

  def split(id: String, nexts: SubsequentNode*): R = creator(SplitNode(Split(id), nexts.toList))

  def to(node: SubsequentNode): R =
    creator(node)

  def join(
      id: String,
      typ: String,
      output: Option[String],
      branchParams: List[(String, List[(String, Expression)])],
      params: (String, Expression)*
  ): GraphBuilder[SourceNode] = {
    val branchParameters = branchParams.map { case (branchId, bParams) =>
      BranchParameters(branchId, toNodeParameters(bParams))
    }
    new SimpleGraphBuilder(SourceNode(node.Join(id, output, typ, toNodeParameters(params), branchParameters), _))
  }

  def decisionTable(
      decisionTableParamValue: Expression,
      matchConditionParamValue: Expression,
      output: String,
  ): GraphBuilder[R] = {
    enricher(
      id = "decision-table",
      output,
      svcId = "decision-table",
      "Decision Table"  -> decisionTableParamValue,
      "Match condition" -> matchConditionParamValue,
    )
  }

  private def toNodeParameters(params: Iterable[(String, Expression)]) = {
    params.map { case (name, expr) => NodeParameter(ParameterName(name), expr) }.toList
  }

}

private[build] class SimpleGraphBuilder[R <: Node](val creator: GraphBuilder.Creator[R]) extends GraphBuilder[R] {
  override def build(inner: GraphBuilder.Creator[R]) = new SimpleGraphBuilder(inner)
}

object GraphBuilder extends GraphBuilder[SubsequentNode] {

  type Creator[R] = SubsequentNode => R

  override def creator: Creator[SubsequentNode] = identity[SubsequentNode]

  override def build(inner: Creator[SubsequentNode]) = new SimpleGraphBuilder[SubsequentNode](inner)

}
