/* eslint-disable i18next/no-literal-string */
import NodeUtils from "../NodeUtils"
import {makeLink, makeElement, boundingRect} from "../EspNode"
import {Process, GroupId, ProcessDefinitionData} from "../../../types"
import {Layout} from "../../../actions/nk"
import {ProcessCounts} from "../../../reducers/graph"
import {updateLayout} from "./updateLayout"
import {redraw} from "./redraw"
import {dia} from "jointjs"
import {isEqual} from "lodash"

export function drawGraph(
  process: Process,
  layout: Layout,
  processCounts: ProcessCounts,
  processDefinitionData: ProcessDefinitionData,
  expandedGroups: GroupId[],
) {
  const graph: dia.Graph = this.graph
  const directedLayout = this.directedLayout
  const _updateLayout = updateLayout(graph, directedLayout)
  const _redraw = redraw(graph)

  performance.mark("redrawing start")
  this.redrawing = true

  const nodesWithGroups = NodeUtils.nodesFromProcess(process, expandedGroups)
  const edgesWithGroups = NodeUtils.edgesFromProcess(process, expandedGroups)
  const groups = NodeUtils.getExpandedGroups(process, expandedGroups)

  const nodes = nodesWithGroups.map(makeElement(processCounts, processDefinitionData))
  const edges = edgesWithGroups.map(makeLink)
  const boundingRects = groups.map(boundingRect(nodes, layout, nodesWithGroups))

  performance.mark("nodes, links & bounding")

  const cells = [...boundingRects, ...nodes, ...edges]

  const newCells = cells.filter(cell => !graph.getCell(cell.id))
  const deletedCells = graph.getCells().filter(oldCell => !cells.find(cell => cell.id === oldCell.id))
  const changedCells = cells.filter(cell => {
    const old = graph.getCell(cell.id)
    return !isEqual(old?.get("definitionToCompare"), cell.get("definitionToCompare"))
  })
  performance.mark("compute")

  _redraw(newCells, deletedCells, changedCells, cells)
  performance.mark("redraw")

  _updateLayout(layout)
  performance.mark("layout")

  boundingRects.forEach(rect => rect.toBack())
  performance.mark("boundingRects")

  this.redrawing = false
  performance.mark("redrawing done")
}
