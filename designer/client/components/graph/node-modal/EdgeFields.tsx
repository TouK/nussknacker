import {Edge, EdgeKind, VariableTypes} from "../../../types"
import {useSelector} from "react-redux"
import {getProcessToDisplay} from "../../../reducers/selectors/graph"
import React, {useCallback, useEffect, useMemo, useState} from "react"
import {NodeValue} from "./subprocess-input-definition/NodeValue"
import {EdgeTypeOption, EdgeTypeSelect} from "./EdgeTypeSelect"
import {EditableEditor} from "./editors/EditableEditor"
import {css, cx} from "@emotion/css"
import {FieldsRow} from "./subprocess-input-definition/FieldsRow"
import {SelectWithFocus} from "../../withFocus"
import NodeUtils from "../NodeUtils"
import {uniq} from "lodash"
import {ExpressionLang} from "./editors/expression/types"
import {Validator} from "./editors/Validators"
import {getProcessDefinitionData} from "../../../reducers/selectors/settings"
import {useTranslation} from "react-i18next"

interface Props {
  index: number,
  readOnly?: boolean,
  value: Edge,
  onChange: (edge: Edge) => void,
  edges: Edge[],
  types?: EdgeTypeOption[],
  variableTypes?: VariableTypes,
  validators?: Validator[],
}

export function EdgeFields(props: Props): JSX.Element {
  const {t} = useTranslation()
  const {readOnly, value, index, onChange, edges, types, variableTypes, validators = []} = props
  const process = useSelector(getProcessToDisplay)
  const processDefinitionData = useSelector(getProcessDefinitionData)

  const [edge, setEdge] = useState(value)

  useEffect(() => {
    onChange(edge)
  }, [edge, onChange])

  //NOTE: subprocess node preview is read only so we can ignore wrong "process" and nodes here.
  const availableNodes = process.nodes.filter(n => NodeUtils.hasInputs(n))
  const otherEdges = useMemo(() => process.edges.filter(e => e.from !== edge.from), [edge.from, process.edges])
  const targetNodes = useMemo(() => availableNodes.filter(n => n.id === edge.to), [availableNodes, edge.to])
  const freeNodes = useMemo(() => {
    return availableNodes
      .filter(n => {
        //filter this switch
        if (n.id === edge.from) {
          return false
        }
        //filter already used
        if (edges.some(e => e.to === n.id)) {
          return false
        }
        return NodeUtils.canHaveMoreInputs(n, otherEdges.filter(e => e.to === n.id), processDefinitionData)
      })
  }, [availableNodes, edge.from, edges, otherEdges, processDefinitionData])

  const freeInputs = useMemo(
    () => uniq(freeNodes.concat(targetNodes).map(n => n.id)),
    [freeNodes, targetNodes]
  )

  const onValueChange = useCallback(expression => setEdge(e => ({
    ...e, edgeType: {
      ...e.edgeType, condition: {
        ...e.edgeType?.condition, expression,
      },
    },
  })), [])

  function getValueEditor() {
    if (edge.edgeType.type === EdgeKind.switchNext) {
      return (
        <EditableEditor
          valueClassName={cx("node-value", css({gridArea: "expr"}))}
          variableTypes={variableTypes}
          fieldLabel={t("node.fields.edge.expression", "Expression")}
          expressionObj={{
            expression: edge.edgeType.condition?.expression !== undefined ? edge.edgeType.condition?.expression : "true",
            language: edge.edgeType.condition?.language || ExpressionLang.SpEL,
          }}
          readOnly={readOnly}
          onValueChange={onValueChange}
          validators={validators}
          showValidation
        />
      )
    }
    return null
  }

  const showType = types.length > 1 || uniq(edges.map(e => e.edgeType?.type)).length > 1
  return (
    <FieldsRow
      index={index}
      className={cx("movable-row", css({
        "&&&&": {
          display: "grid",
          gridTemplateColumns: "1fr 2fr auto",
          gridTemplateRows: "auto auto",
          gridTemplateAreas: `"field field remove" "expr expr x"`,
        },
      }))}
    >
      {showType ?
        (
          <NodeValue>
            <EdgeTypeSelect
              readOnly={readOnly || types.length < 2}
              edge={edge}
              onChange={type => setEdge(({edgeType: {condition, ...edgeType}, ...edge}) => ({
                ...edge,
                edgeType: type === EdgeKind.switchNext ? {...edgeType, type, condition} : {...edgeType, type},
              }))}
              options={types}
            />
          </NodeValue>
        ) :
        null}
      <NodeValue className={css({gridArea: !showType && "field"})}>
        <SelectWithFocus
          title={freeInputs.length ?
            t("node.fields.edge.target", "Edge target node") :
            t("node.fields.edge.target.empty", "No free target nodes")
          }
          className="node-input"
          value={edge.to}
          onChange={(event) => setEdge(edge => ({...edge, to: event.target.value}))}
          disabled={readOnly || !freeInputs.length}
        >
          {readOnly ?
            (
              <option value={edge.to}>{edge.to}</option>
            ) :
            (
              <>
                <option value={""}>⇢</option>
                {freeInputs.map((node) => (
                  <option key={node} value={node}>➝ {node}</option>
                ))}
              </>
            )}
        </SelectWithFocus>
      </NodeValue>
      {getValueEditor()}
    </FieldsRow>
  )
}
