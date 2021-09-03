/* eslint-disable i18next/no-literal-string */
import {dia} from "jointjs"
import {isEqual} from "lodash"
import {Layout} from "../../../actions/nk"
import {Process, ProcessDefinitionData} from "../../../types"
import {makeElement, makeLink} from "../EspNode"
import NodeUtils from "../NodeUtils"
import {redraw} from "./redraw"
import {updateLayout} from "./updateLayout"

export function drawGraph(
  process: Process,
  layout: Layout,
  processDefinitionData: ProcessDefinitionData,
): void {
  const graph: dia.Graph = this.graph
  const directedLayout = this.directedLayout
  const _updateLayout = updateLayout(graph, directedLayout)
  const _redraw = redraw(graph)

  performance.mark("redrawing start")
  this.redrawing = true

  const nodesWithGroups = NodeUtils.nodesFromProcess(process)
  const edgesWithGroups = NodeUtils.edgesFromProcess(process)

  const nodes = nodesWithGroups.map(makeElement(processDefinitionData))
  const edges = edgesWithGroups.map(value => makeLink(value, [...this.processGraphPaper?.defs?.children].find(def => def.nodeName === "marker")?.id))

  performance.mark("nodes, links & bounding")

  const cells = [...nodes, ...edges]

  const currentCells = graph.getCells()
  const currentIds = currentCells.map(c => c.id)
  const newCells = cells.filter(cell => !currentIds.includes(cell.id))
  const deletedCells = currentCells.filter(oldCell => !cells.find(cell => cell.id === oldCell.id))
  const changedCells = cells.filter(cell => {
    const old = graph.getCell(cell.id)
    return old && !isEqual(old.get("definitionToCompare"), cell.get("definitionToCompare"))
  })
  performance.mark("compute")

  _redraw(newCells, deletedCells, changedCells)
  performance.mark("redraw")

  _updateLayout(layout)
  performance.mark("layout")

  this.redrawing = false
  performance.mark("redrawing done")
}
