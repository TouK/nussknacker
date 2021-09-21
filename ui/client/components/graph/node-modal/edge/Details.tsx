import React, {useCallback, useMemo} from "react"
import {useSelector} from "react-redux"
import ProcessUtils from "../../../../common/ProcessUtils"
import {getProcessCategory} from "../../../../reducers/selectors/graph"
import {getCapabilities} from "../../../../reducers/selectors/other"
import {getProcessDefinitionData} from "../../../../reducers/selectors/settings"
import {Edge, EdgeType, Process} from "../../../../types"
import NodeUtils from "../../NodeUtils"
import {ExpressionObj} from "../editors/expression/types"
import EdgeDetailsContent from "./EdgeDetailsContent"

export function Details({
  edge,
  onChange,
  processToDisplay,
}: {edge: Edge, onChange: (value: Edge) => void, processToDisplay: Process}): JSX.Element {
  const processDefinitionData = useSelector(getProcessDefinitionData)
  const processCategory = useSelector(getProcessCategory)
  const {editFrontend} = useSelector(getCapabilities)

  const nodeId = edge.from
  const readOnly = !editFrontend

  const variableTypes = useMemo(() => {
    const findAvailableVariables = ProcessUtils.findAvailableVariables(
      processDefinitionData,
      processCategory,
      processToDisplay,
    )
    return findAvailableVariables(nodeId, undefined)
  }, [nodeId, processCategory, processDefinitionData, processToDisplay])

  const changeEdgeTypeValue = useCallback((edgeTypeValue: EdgeType["type"]) => {
    const fromNode = NodeUtils.getNodeById(edge.from, processToDisplay)
    const defaultEdgeType: EdgeType = NodeUtils
      .edgesForNode(fromNode, processDefinitionData).edges.find(e => e.type === edgeTypeValue)
    onChange({...edge, edgeType: defaultEdgeType})
  }, [edge, onChange, processDefinitionData, processToDisplay])

  const changeEdgeTypeCondition = useCallback((expression: ExpressionObj["expression"]) => {
    onChange({...edge, edgeType: {...edge.edgeType, condition: {...edge.edgeType.condition, expression}}})
  }, [edge, onChange])

  return (
    <EdgeDetailsContent
      changeEdgeTypeValue={changeEdgeTypeValue}
      changeEdgeTypeCondition={changeEdgeTypeCondition}
      readOnly={readOnly}
      edge={edge}
      showValidation={true}
      showSwitch={true}
      variableTypes={variableTypes}
    />
  )
}
