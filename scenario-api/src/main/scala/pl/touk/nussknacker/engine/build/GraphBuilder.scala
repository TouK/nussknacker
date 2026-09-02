package pl.touk.nussknacker.engine.build

import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.graph.{node, EdgeType}
import pl.touk.nussknacker.engine.graph.evaluatedparam.{BranchParameters, Parameter => NodeParameter}
import pl.touk.nussknacker.engine.graph.expression._
import pl.touk.nussknacker.engine.graph.fragment.FragmentRef
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{FragmentClazzRef, FragmentParameter}
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.graph.variable._

trait GraphBuilder[R] {

  def creator: GraphBuilder.Creator[R]

  def build(inner: GraphBuilder.Creator[R]): GraphBuilder[R]

  def source(id: String, typ: String, params: (String, Expression)*): GraphBuilder[SourceNode] =
    new SimpleGraphBuilder(node => SourceNode(Source(id, SourceRef(typ, toNodeParameters(params))), node))

  def buildVariable(id: String, varName: String, fields: (String, Expression)*): GraphBuilder[R] =
    build(node =>
      creator(
        Some(OneOutputSubsequentNode(VariableBuilder(id, varName, fields.map(f => Field(f._1, f._2)).toList), node))
      )
    )

  def buildSimpleVariable(id: String, varName: String, value: Expression): GraphBuilder[R] =
    build(node => creator(Some(OneOutputSubsequentNode(Variable(id, varName, value), node))))

  def enricher(id: String, output: String, svcId: String, params: (String, Expression)*): GraphBuilder[R] =
    build(node =>
      creator(
        Some(
          OneOutputSubsequentNode(
            Enricher(id, ServiceRef(svcId, toNodeParameters(params)), output, mockExpression = None),
            node
          )
        )
      )
    )

  def enricherWithMockExpression(
      id: String,
      output: String,
      svcId: String,
      mockExpression: Expression,
      params: (String, Expression)*
  ): GraphBuilder[R] =
    build(node =>
      creator(
        Some(
          OneOutputSubsequentNode(
            Enricher(id, ServiceRef(svcId, toNodeParameters(params)), output, mockExpression = Some(mockExpression)),
            node
          )
        )
      )
    )

  def processor(id: String, svcId: String, params: (String, Expression)*): GraphBuilder[R] =
    build(node =>
      creator(Some(OneOutputSubsequentNode(Processor(id, ServiceRef(svcId, toNodeParameters(params))), node)))
    )

  def disabledProcessor(id: String, svcId: String, params: (String, Expression)*): GraphBuilder[R] =
    build(node =>
      creator(
        Some(
          OneOutputSubsequentNode(
            Processor(id, ServiceRef(svcId, toNodeParameters(params)), isDisabled = Some(true)),
            node
          )
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
        Some(
          FragmentNode(
            FragmentInput(
              id,
              FragmentRef(
                fragmentId,
                toNodeParameters(params),
                Map(fragmentOutputDefinitionName -> outputParamName)
              )
            ),
            Map(fragmentOutputDefinitionName -> node),
          )
        )
      )
    )

  def fragment(
      id: String,
      fragmentId: String,
      params: List[(String, Expression)],
      outputParameters: Map[String, String],
      outputs: Map[String, Option[SubsequentNode]]
  ): R =
    creator(
      Some(
        FragmentNode(
          FragmentInput(id, FragmentRef(fragmentId, toNodeParameters(params), outputParameters)),
          outputs
        )
      )
    )

  def fragmentEnd(id: String, fragmentId: String, params: (String, Expression)*): R =
    creator(
      Some(
        FragmentNode(
          FragmentInput(id, FragmentRef(fragmentId, toNodeParameters(params), Map.empty)),
          Map()
        )
      )
    )

  def fragmentInput(id: String, params: (String, Class[_])*): GraphBuilder[SourceNode] =
    new SimpleGraphBuilder(node =>
      SourceNode(
        FragmentInputDefinition(
          id = id,
          parameters = params.map(kv => FragmentParameter(ParameterName(kv._1), FragmentClazzRef(kv._2.getName))).toList
        ),
        node
      )
    )

  def fragmentInputWithRawParameters(id: String, params: FragmentParameter*): GraphBuilder[SourceNode] =
    new SimpleGraphBuilder(node =>
      SourceNode(
        FragmentInputDefinition(
          id = id,
          parameters = params.toList
        ),
        node
      )
    )

  def fragmentOutput(id: String, outputName: String, params: (String, Expression)*): R =
    creator(Some(EndingNode(FragmentOutputDefinition(id, outputName, params.map(kv => Field(kv._1, kv._2)).toList))))

  def filter(
      id: String,
      expression: Expression,
      disabled: Option[Boolean] = None,
      edgeType: EdgeType = EdgeType.FilterTrue
  ): GraphBuilder[R] =
    build(node =>
      creator(
        Some(
          FilterNode(
            Filter(id, expression, disabled),
            node.filter(_ => edgeType == EdgeType.FilterTrue),
            node.filter(_ => edgeType == EdgeType.FilterFalse)
          )
        )
      )
    )

  def filter(id: String, expression: Expression, nextFalse: Option[SubsequentNode]): GraphBuilder[R] =
    build(node => creator(Some(FilterNode(Filter(id, expression), nextTrue = node, nextFalse = nextFalse))))

  def endWithoutSink: R =
    creator(None)

  def emptySink(id: String, typ: String, params: (String, Expression)*): R =
    creator(Some(EndingNode(Sink(id, SinkRef(typ, toNodeParameters(params))))))

  def disabledSink(id: String, typ: String): R =
    creator(Some(EndingNode(Sink(id, SinkRef(typ, List()), isDisabled = Some(true)))))

  def processorEnd(id: String, svcId: String, params: (String, Expression)*): R =
    creator(Some(EndingNode(Processor(id, ServiceRef(svcId, toNodeParameters(params))))))

  def disabledProcessorEnd(id: String, svcId: String, params: (String, Expression)*): R =
    creator(
      Some(EndingNode(Processor(id, ServiceRef(svcId, toNodeParameters(params)), isDisabled = Some(true))))
    )

  def branchEnd(branchId: String, joinId: String): R =
    creator(Some(BranchEnd(node.BranchEndData(BranchEndDefinition(branchId, joinId)))))

  def switch(id: String, nexts: Case*): R =
    creator(Some(SwitchNode(Switch(id), nexts.toList, None)))

  def switch(id: String, expression: Expression, exprVal: String, nexts: Case*): R =
    creator(Some(SwitchNode(Switch(id, Some(expression), Some(exprVal)), nexts.toList, None)))

  def switch(
      id: String,
      expression: Expression,
      exprVal: String,
      defaultNext: Option[SubsequentNode],
      nexts: Case*
  ): R =
    creator(Some(SwitchNode(Switch(id, Some(expression), Some(exprVal)), nexts.toList, defaultNext)))

  def customNode(id: String, outputVar: String, customNodeRef: String, params: (String, Expression)*): GraphBuilder[R] =
    build(node =>
      creator(
        Some(
          OneOutputSubsequentNode(
            CustomNode(id, Some(outputVar), customNodeRef, toNodeParameters(params)),
            node
          )
        )
      )
    )

  // outputVar must be provided always when parameter with @OutputVariableName annotation is used - look into comment in @OutputVariableName
  def endingCustomNode(id: String, outputVar: Option[String], customNodeRef: String, params: (String, Expression)*): R =
    creator(Some(EndingNode(CustomNode(id, outputVar, customNodeRef, toNodeParameters(params)))))

  def customNodeNoOutput(id: String, customNodeRef: String, params: (String, Expression)*): GraphBuilder[R] =
    build(node =>
      creator(Some(OneOutputSubsequentNode(CustomNode(id, None, customNodeRef, toNodeParameters(params)), node)))
    )

  // The builder has no main concept: the caller lists every wired output, the main included, as an ordinary named
  // pair (the builder cannot know the component's declared main). An unwired main is simply an absent pair; with
  // every output unwired the node becomes an EndingNode.
  def customNodeWithOutputs(
      id: String,
      outputVar: Option[String],
      customNodeRef: String,
      outputs: List[(String, SubsequentNode)],
      params: (String, Expression)*
  ): R =
    creator(
      Some {
        val data = CustomNode(id, outputVar, customNodeRef, toNodeParameters(params))
        CustomNodeWithOutputs.orEnding(data, outputs.map { case (name, next) => Output(name, next) })
      }
    )

  def split(id: String, nexts: Option[SubsequentNode]*): R = creator(Some(SplitNode(Split(id), nexts.toList.flatten)))

  def to(node: Option[SubsequentNode]): R =
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
    new SimpleGraphBuilder(n => SourceNode(node.Join(id, output, typ, toNodeParameters(params), branchParameters), n))
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

private[build] class SimpleGraphBuilder[R](val creator: GraphBuilder.Creator[R]) extends GraphBuilder[R] {
  override def build(inner: GraphBuilder.Creator[R]) = new SimpleGraphBuilder(inner)
}

object GraphBuilder extends GraphBuilder[Option[SubsequentNode]] {

  type Creator[R] = Option[SubsequentNode] => R

  override def creator: Creator[Option[SubsequentNode]] = identity[Option[SubsequentNode]]

  override def build(inner: Creator[Option[SubsequentNode]]) = new SimpleGraphBuilder[Option[SubsequentNode]](inner)

}
