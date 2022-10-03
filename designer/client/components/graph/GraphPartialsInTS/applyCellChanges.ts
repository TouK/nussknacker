/* eslint-disable i18next/no-literal-string */
import {dia} from "jointjs"
import {flatMap, groupBy, isEqual} from "lodash"
import {Process, ProcessDefinitionData} from "../../../types"
import {makeElement, makeLink} from "../EspNode"
import NodeUtils from "../NodeUtils"
import {isEdgeConnected} from "./EdgeUtils"
import {updateChangedCells} from "./updateChangedCells"

export function applyCellChanges(paper: dia.Paper, process: Process, processDefinitionData: ProcessDefinitionData): void {
  const graph = paper.model
  const nodesWithGroups = NodeUtils.nodesFromProcess(process)
  const edgesWithGroups = NodeUtils.edgesFromProcess(process)

  const nodes = nodesWithGroups.map(makeElement(processDefinitionData))
  const indexed = flatMap(groupBy(edgesWithGroups, "from"), edges => edges.map((edge, i) => ({...edge, index: ++i})))
  const defs = Array.from(paper?.defs?.children)
  const edges = indexed.filter(isEdgeConnected).map(value => makeLink(value, defs.find(def => def.nodeName === "marker")?.id))

  const cells = [...nodes, ...edges]

  const currentCells = graph.getCells()
  const currentIds = currentCells.map(c => c.id)
  const newCells = cells.filter(cell => !currentIds.includes(cell.id))
  const deletedCells = currentCells.filter(oldCell => !cells.find(cell => cell.id === oldCell.id))
  const changedCells = cells.filter(cell => {
    const old = graph.getCell(cell.id)
    return old && !isEqual(old.get("definitionToCompare"), cell.get("definitionToCompare"))
  })

  graph.removeCells(deletedCells)
  updateChangedCells(graph, changedCells)
  graph.addCells(newCells)
}

