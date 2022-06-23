import React, {useCallback, useEffect, useMemo, useState} from "react"
import {useSelector} from "react-redux"
import {getProcessToDisplay} from "../../../reducers/selectors/graph"
import {Edge, EdgeKind} from "../../../types"
import {NodeRowFields} from "./subprocess-input-definition/NodeRowFields"
import {DndItems} from "./subprocess-input-definition/DndItems"
import {EdgeFields} from "./EdgeFields"
import {ExpressionLang} from "./editors/expression/types"
import NodeUtils from "../NodeUtils"
import {EdgeTypeOption} from "./EdgeTypeSelect"

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
}

type WithTempId<T> = T & { _id?: string }

export function EdgesDndComponent(props: Props): JSX.Element {
  const {nodeId, label, readOnly, value, onChange, ordered} = props
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
      if (next.to) {
        delete next._id
      } else if (!next._id) {
        next._id = Math.random().toString()
      }
      setEdges(edges => edges.map(e => e === current ? next : e))
    }
  }, [])

  const removeEdge = useCallback((n, index) => setEdges(edges => edges.filter((e, i) => i !== index)), [])

  const addEdge = useCallback(() => {
    const [{value}] = availableTypes
    const item: Edge = {
      from: nodeId,
      to: "",
      edgeType: value === EdgeKind.switchNext ?
        {
          type: value, condition: {
            expression: "true",
            language: ExpressionLang.SpEL,
          },
        } :
        {type: value},
    }
    setEdges(edges => edges.concat(item))
  }, [availableTypes, nodeId])

  useEffect(() => {
    onChange?.(edges)
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
          />
        ),
      }
    })
  }, [edgeTypes, edges, readOnly, replaceEdge])

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
