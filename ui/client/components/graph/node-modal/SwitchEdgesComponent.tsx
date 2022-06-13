import React, {useCallback, useEffect, useMemo, useState} from "react"
import {useSelector} from "react-redux"
import {getProcessToDisplay} from "../../../reducers/selectors/graph"
import {Edge, EdgeKind} from "../../../types"
import {NodeRowFields} from "./subprocess-input-definition/NodeRowFields"
import {DndItems} from "./subprocess-input-definition/DndItems"
import {EdgeFields} from "./EdgeFields"

interface Props {
  node: string,
  label: string,
  value?: Edge[],
  onChange?: (edges: Edge[]) => void,
  readOnly?: boolean,
  edgeTypes?: { value: EdgeKind, label: string, one?: boolean, legacy?: boolean }[],
  ordered?: boolean,
}

type WithTempId<T> = T & { _id?: string }

export function SwitchEdgesComponent(props: Props): JSX.Element {
  const {
    node, label, readOnly, value, onChange, ordered,
    edgeTypes = [
      {value: EdgeKind.switchNext, label: "Condition"},
      {value: EdgeKind.switchDefault, label: "Default", one: true, legacy: true},
    ],
  } = props
  const process = useSelector(getProcessToDisplay)
  const [edges, setEdges] = useState<WithTempId<Edge>[]>(() => value || process.edges.filter(({from}) => from === node))

  const availableTypes = useMemo(() => edgeTypes.filter(t => !t.one || !edges.some(e => e.edgeType?.type === t.value)), [edgeTypes, edges])

  const replaceEdge = useCallback((edge: WithTempId<Edge>) => (value: WithTempId<Edge>) => {
    if (edge !== value) {
      if (value.to) {
        delete value._id
      } else if (!value._id) {
        value._id = Math.random().toString()
      }
      setEdges(edges => edges.map(e => e === edge ? value : e))
    }
  }, [])

  const removeEdge = useCallback((n, index) => setEdges(edges => edges.filter((e, i) => i !== index)), [])

  const addEdge = useCallback(() => {
    const [{value}] = availableTypes
    const item: Edge = {
      from: node,
      to: "",
      edgeType: {type: value},
    }
    setEdges(edges => edges.concat(item))
  }, [availableTypes, node])

  useEffect(() => {
    onChange?.(edges)
  }, [edges, onChange])

  const edgeItems = useMemo(() => {
    return edges.map((edge, index, array) => {
      const types = edgeTypes
        .filter(t => t.value === edge.edgeType.type || !t.legacy && (!t.one || !array.some(e => e.edgeType?.type === t.value)))

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
