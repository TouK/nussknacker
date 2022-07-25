/* eslint-disable i18next/no-literal-string */
import {dia} from "jointjs"
import {flatMap, groupBy, isEqual} from "lodash"
import {Layout} from "../../../actions/nk"
import {Process, ProcessDefinitionData} from "../../../types"
import {makeElement, makeLink} from "../EspNode"
import NodeUtils from "../NodeUtils"
import {redraw} from "./redraw"
import {updateLayout} from "./updateLayout"
import {isEdgeConnected} from "./EdgeUtils"

export function drawGraph(
  process: Process,
  layout: Layout,
  processDefinitionData: ProcessDefinitionData,
): void {
  const graph: dia.Graph = this.graph
  const directedLayout = this.directedLayout
  const _updateLayout = updateLayout(graph, directedLayout)
  const _redraw = redraw(graph)

  this.redrawing = true

  const nodesWithGroups = NodeUtils.nodesFromProcess(process)
  const edgesWithGroups = NodeUtils.edgesFromProcess(process)

  const nodes = nodesWithGroups.map(makeElement(processDefinitionData))
  const indexed = flatMap(groupBy(edgesWithGroups, "from"), edges => edges.map((edge, i) => ({...edge, index: ++i})))
  const edges = indexed.filter(isEdgeConnected).map(value => makeLink(value, [...this.processGraphPaper?.defs?.children].find(def => def.nodeName === "marker")?.id))

  const cells = [...nodes, ...edges]

  const currentCells = graph.getCells()
  const currentIds = currentCells.map(c => c.id)
  const newCells = cells.filter(cell => !currentIds.includes(cell.id))
  const deletedCells = currentCells.filter(oldCell => !cells.find(cell => cell.id === oldCell.id))
  const changedCells = cells.filter(cell => {
    const old = graph.getCell(cell.id)
    return old && !isEqual(old.get("definitionToCompare"), cell.get("definitionToCompare"))
  })

  _redraw(newCells, deletedCells, changedCells)
  _updateLayout(layout)

  this.redrawing = false
}
