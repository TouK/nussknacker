import React, {useCallback, useEffect, useMemo, useState} from "react"
import {useSelector} from "react-redux"
import {getProcessToDisplay} from "../../../reducers/selectors/graph"
import {Edge, EdgeKind, VariableTypes} from "../../../types"
import {NodeRowFields} from "./subprocess-input-definition/NodeRowFields"
import {DndItems} from "./subprocess-input-definition/DndItems"
import {EdgeFields} from "./EdgeFields"
import {ExpressionLang} from "./editors/expression/types"
import NodeUtils from "../NodeUtils"
import {EdgeTypeOption} from "./EdgeTypeSelect"
import {Error, errorValidator, mandatoryValueValidator} from "./editors/Validators"
import {defaultsDeep} from "lodash"

interface EdgeType extends Partial<EdgeTypeOption> {
  value: EdgeKind,
  label?: string,
  onlyOne?: boolean,
}

interface Props {
  nodeId: string,
  label: string,
  value?: Edge[],
  onChange?: (edges: Edge[]) => void,
  readOnly?: boolean,
  edgeTypes: EdgeType[],
  ordered?: boolean,
  variableTypes?: VariableTypes,
  fieldErrors?: Error[],
}

export type WithTempId<T> = T & { _id?: string }

//mutate to avoid unnecessary renders
function withFakeId(edge: WithTempId<Edge>): WithTempId<Edge> {
  if (edge.to?.length > 0) {
    delete edge._id
  } else if (!edge._id) {
    edge._id = `id${Math.random()}`
  }
  return edge
}

function getDefaultEdgeType(kind: EdgeKind): Edge["edgeType"] {
  switch (kind) {
    case EdgeKind.switchNext:
      return {
        type: kind,
        condition: {
          expression: "true",
          language: ExpressionLang.SpEL,
        },
      }
    default:
      return {type: kind}
  }
}

function getDefaultEdge(kind: EdgeKind): Edge {
  return {from: "", to: "", edgeType: getDefaultEdgeType(kind)}
}

function withDefaults<T extends Edge>(edge: Partial<T>): T {
  return defaultsDeep(edge, getDefaultEdge(edge.edgeType.type))
}

export function EdgesDndComponent(props: Props): JSX.Element {
  const {nodeId, label, readOnly, value, onChange, ordered, variableTypes, fieldErrors = []} = props
  const process = useSelector(getProcessToDisplay)
  const [edges, setEdges] = useState<WithTempId<Edge>[]>(() => value || process.edges.filter(({from}) => from === nodeId))

  const edgeTypes = useMemo(
    () => props.edgeTypes.map((t) => ({...t, label: t.label || NodeUtils.edgeTypeLabel(t.value)})),
    [props.edgeTypes]
  )

  const availableTypes = useMemo(
    () => edgeTypes.filter(t => !t.onlyOne || !edges.some(e => e.edgeType?.type === t.value)),
    [edgeTypes, edges]
  )

  const replaceEdge = useCallback((current: WithTempId<Edge>) => (next: WithTempId<Edge>) => {
    if (current !== next) {
      setEdges(edges => edges.map(e => e === current ? withFakeId(withDefaults(next)) : e))
    }
  }, [])

  const removeEdge = useCallback((n, index) => setEdges(edges => edges.filter((e, i) => i !== index)), [])

  const addEdge = useCallback(() => {
    const [{value: type}] = availableTypes
    setEdges(edges => edges.concat(withFakeId(withDefaults({from: nodeId, edgeType: {type}}))))
  }, [availableTypes, nodeId])

  useEffect(() => {
    onChange?.(edges?.map(e => ({...e, to: e._id || e.to})))
  }, [edges, onChange])

  const edgeItems = useMemo(() => {
    return edges.map((edge, index, array) => {
      const types = edgeTypes
        .filter(t => t.value === edge.edgeType.type || !t.disabled && (!t.onlyOne || !array.some(e => e.edgeType?.type === t.value)))

      return {
        item: edge,
        el: (
          <EdgeFields
            key={edge._id || edge.to}
            index={index}
            readOnly={readOnly}
            value={edge}
            onChange={replaceEdge(edge)}
            edges={array}
            types={types}
            variableTypes={variableTypes}
            validators={[mandatoryValueValidator, errorValidator(fieldErrors, edge._id || edge.to)]}
          />
        ),
      }
    })
  }, [edgeTypes, edges, readOnly, replaceEdge, variableTypes, fieldErrors])

  const namespace = `edges`

  return (
    <NodeRowFields
      label={label}
      path={namespace}
      readOnly={readOnly}
      onFieldRemove={removeEdge}
      onFieldAdd={availableTypes.length ? addEdge : null}
    >
      <DndItems disabled={readOnly || !ordered} items={edgeItems} onChange={setEdges}/>
    </NodeRowFields>
  )
}
