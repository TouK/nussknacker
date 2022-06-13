import {Edge, EdgeKind, VariableTypes} from "../../../types"
import {useSelector} from "react-redux"
import {getProcessCategory, getProcessToDisplay} from "../../../reducers/selectors/graph"
import {getProcessDefinitionData} from "../../../reducers/selectors/settings"
import React, {useEffect, useMemo, useState} from "react"
import ProcessUtils from "../../../common/ProcessUtils"
import {NodeValue} from "./subprocess-input-definition/NodeValue"
import {EdgeTypeSelect} from "./EdgeTypeSelect"
import {EditableEditor} from "./editors/EditableEditor"
import {ExpressionLang} from "./editors/expression/types"
import {css, cx} from "@emotion/css"
import {FieldsRow} from "./subprocess-input-definition/FieldsRow"
import {SelectWithFocus} from "../../withFocus"
import NodeUtils from "../NodeUtils"
import {uniq} from "lodash"

interface Props {
  index: number,
  readOnly?: boolean,
  value: Edge,
  onChange: (edge: Edge) => void,
  edges: Edge[],
  types?: { value: EdgeKind, label: string }[],
}

export function EdgeFields(props: Props): JSX.Element {
  const {readOnly, value, index, onChange, edges, types} = props
  const process = useSelector(getProcessToDisplay)
  const processDefinitionData = useSelector(getProcessDefinitionData)
  const processCategory = useSelector(getProcessCategory)

  const [edge, setEdge] = useState(value)

  const findAvailableVariables = useMemo(() => {
    return ProcessUtils.findAvailableVariables(
      processDefinitionData,
      processCategory,
      process,
    )

  }, [processDefinitionData, processCategory, process])

  const variableTypes = useMemo<VariableTypes>(() => {
    return findAvailableVariables(value.to, undefined)
  }, [findAvailableVariables, value.to])

  useEffect(() => {
    onChange?.(edge)
  }, [edge, onChange])

  const availableNodes = process.nodes.filter(n => NodeUtils.hasInputs(n))

  const otherEdges = useMemo(() => process.edges.filter(e => e.from !== edge.from), [edge.from, process.edges])
  const targetNodes = useMemo(() => availableNodes.filter(n => n.id === edge.to), [availableNodes, edge.to])
  const freeNodes = useMemo(() => {
    return availableNodes
      .filter(n => n.id !== edge.from && !otherEdges.some(e => e.to === n.id) && !edges.some(e => e.to === n.id))
  }, [availableNodes, edge.from, edges, otherEdges])

  const freeInputs = useMemo(
    () => uniq(freeNodes.concat(targetNodes).map(n => n.id)),
    [freeNodes, targetNodes]
  )

  function getValueEditor() {
    if (edge.edgeType.type === EdgeKind.switchNext) {
      return (
        <EditableEditor
          valueClassName={cx("node-value", css({gridArea: "expr"}))}
          variableTypes={variableTypes}
          fieldLabel={"Expression"}
          expressionObj={{
            expression: edge.edgeType.condition?.expression || "true",
            language: edge.edgeType.condition?.language || ExpressionLang.SpEL,
          }}
          readOnly={readOnly}
          onValueChange={expression => setEdge(e => ({
            ...e,
            edgeType: {
              ...e.edgeType,
              condition: {
                ...{
                  expression: edge.edgeType.condition?.expression || "",
                  language: edge.edgeType.condition?.language || ExpressionLang.SpEL,
                },
                ...e.edgeType.condition,
                expression,
              },
            },
          }))}
        />
      )
    }
    return null
  }

  return (
    <FieldsRow
      index={index}
      className={cx("movable-row", css({
        "&&&&": {
          display: "grid",
          gridTemplateColumns: "1fr 1fr auto",
          gridTemplateRows: "auto auto",
          gridTemplateAreas: `"target type " "expr expr "`,
        },
      }))}
    >
      <NodeValue>
        <SelectWithFocus
          placeholder={"Target"}
          className="node-input"
          value={edge.to}
          onChange={(e) => {
            const target = e.target.value
            setEdge(({to, ...e}) => ({
              ...e,
              to: target,
            }))
          }}
          disabled={readOnly || !freeInputs.length}
        >
          <option value={null}></option>
          {freeInputs.map((node) => (
            <option key={node} value={node}>{node}</option>
          ))}
        </SelectWithFocus>
      </NodeValue>
      <NodeValue>
        <EdgeTypeSelect
          readOnly={readOnly || types.length < 2}
          edge={edge}
          onChange={type => setEdge(({edgeType, ...e}) => ({
            ...e,
            edgeType: {
              ...edgeType,
              type,
              condition: type === EdgeKind.switchNext ? edgeType.condition : null,
            },
          }))}

          options={types}
        />
      </NodeValue>
      {getValueEditor()}
    </FieldsRow>
  )
}
