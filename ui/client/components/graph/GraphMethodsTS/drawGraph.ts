/* eslint-disable i18next/no-literal-string */
import NodeUtils from "../NodeUtils"
import {makeLink, makeElement, boundingRect} from "../EspNode"
import {Process, GroupId, ProcessDefinitionData} from "../../../types"
import {Layout} from "../../../actions/nk"
import {Graph} from "../Graph"
import {ProcessCounts} from "../../../reducers/graph"

export function drawGraph(
  process: Process,
  layout: Layout,
  processCounts: ProcessCounts,
  processDefinitionData: ProcessDefinitionData,
  expandedGroups: GroupId[],
) {
  if (!(this instanceof Graph)) {
    throw "bind drawGraphFn to Graph instance!"
  }
  performance.mark("redrawing start")
  this.redrawing = true

  const nodesWithGroups = NodeUtils.nodesFromProcess(process, expandedGroups)
  const edgesWithGroups = NodeUtils.edgesFromProcess(process, expandedGroups)
  const groups = NodeUtils.getExpandedGroups(process, expandedGroups)

  const nodes = nodesWithGroups.map(makeElement(processCounts, processDefinitionData))
  const edges = edgesWithGroups.map(makeLink)
  const boundingRects = groups.map(boundingRect(nodes, layout, nodesWithGroups))

  performance.mark("nodes, links & bounding")

  const cells = [].concat(boundingRects, nodes, edges)

  const newCells = cells.filter(cell => !this.graph.getCell(cell.id))
  const deletedCells = this.graph.getCells().filter(oldCell => !cells.find(cell => cell.id === oldCell.id))
  const changedCells = cells.filter(cell => {
    const old = this.graph.getCell(cell.id)
    //TODO: some different ways of comparing?
    return old && JSON.stringify(old.get("definitionToCompare")) !== JSON.stringify(cell.get("definitionToCompare"))
  })
  performance.mark("compute")

  if (newCells.length + deletedCells.length + changedCells.length > 3) {
    this.graph.resetCells(cells)
  } else {
    this.graph.removeCells(deletedCells)
    this._updateChangedCells(changedCells)
    this.graph.addCells(newCells)
  }
  performance.mark("redraw")

  this._layout(layout)
  performance.mark("layout")

  boundingRects.forEach(rect => rect.toBack())
  performance.mark("boundingRects")

  this.redrawing = false
  performance.mark("redrawing done")
}
