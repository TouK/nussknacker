import React, {useCallback, useMemo} from "react"
import {useSelector} from "react-redux"
import ProcessUtils from "../../../../common/ProcessUtils"
import {getProcessCategory} from "../../../../reducers/selectors/graph"
import {getCapabilities} from "../../../../reducers/selectors/other"
import {getProcessDefinitionData} from "../../../../reducers/selectors/settings"
import {Edge, EdgeType, Process} from "../../../../types"
import NodeUtils from "../../NodeUtils"
import EdgeDetailsContent from "./EdgeDetailsContent"

export function Details({
  edge,
  onChange,
  processToDisplay,
}: {edge: Edge, onChange: (value: Edge) => void, processToDisplay: Process}): JSX.Element {
  const processDefinitionData = useSelector(getProcessDefinitionData)
  const processCategory = useSelector(getProcessCategory)
  const {write} = useSelector(getCapabilities)

  const nodeId = edge.from
  const readOnly = !write

  const variableTypes = useMemo(() => {
    const findAvailableVariables = ProcessUtils.findAvailableVariables(
      processDefinitionData,
      processCategory,
      processToDisplay,
    )
    return findAvailableVariables(nodeId, undefined)
  }, [nodeId, processCategory, processDefinitionData, processToDisplay])

  const updateEdgeProp = useCallback((prop, value) => {
    onChange({...edge, [prop]: value})
  }, [onChange, edge])

  const changeEdgeTypeValue = useCallback((edgeTypeValue: EdgeType) => {
    const fromNode = NodeUtils.getNodeById(edge.from, processToDisplay)
    const defaultEdgeType = NodeUtils
      .edgesForNode(fromNode, processDefinitionData).edges.find(e => e.type === edgeTypeValue)
    updateEdgeProp("edgeType", defaultEdgeType)
  }, [edge.from, processDefinitionData, processToDisplay, updateEdgeProp])

  return (
    <EdgeDetailsContent
      changeEdgeTypeValue={changeEdgeTypeValue}
      updateEdgeProp={updateEdgeProp}
      readOnly={readOnly}
      edge={edge}
      showValidation={true}
      showSwitch={true}
      variableTypes={variableTypes}
    />
  )
}
