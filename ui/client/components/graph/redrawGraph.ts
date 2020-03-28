/* eslint-disable i18next/no-literal-string */
import _ from "lodash"
import {Layout} from "../../actions/nk"
import {GroupId, Process, ProcessDefinitionData} from "../../types"
import {boundingRect, makeElement, makeLink} from "./EspNode"
import NodeUtils from "./NodeUtils"

export function redrawGraph(
  process: Process,
  expandedGroups: GroupId[],
  processCounts,
  forExport: boolean,
  processDefinitionData: ProcessDefinitionData,
  layout: Layout,
  graph,
  _updateChangedCells,
  _layout,
) {
  console.time("redrawGraph")
  const nodesWithGroups = NodeUtils.nodesFromProcess(process, expandedGroups)
  const edgesWithGroups = NodeUtils.edgesFromProcess(process, expandedGroups)

  const nodes = nodesWithGroups.map(n => makeElement(processCounts, forExport, processDefinitionData)(n))
  const edges = edgesWithGroups.map(e => makeLink(forExport)(e))

  const boundingRects = NodeUtils.getExpandedGroups(process, expandedGroups).map(expandedGroup => ({
    group: expandedGroup,
    rect: boundingRect(nodes, expandedGroup, layout, NodeUtils.createGroupNode(nodesWithGroups, expandedGroup)),
  }))

  const cells = boundingRects.map(g => g.rect).concat(nodes.concat(edges))

  const newCells = cells.filter(cell => !graph.getCell(cell.id))
  const deletedCells = graph.getCells().filter(oldCell => !_.find(cells, cell => cell.id === oldCell.id))
  const changedCells = cells.filter(cell => {
    const old = graph.getCell(cell.id)
    //TODO: some different ways of comparing?
    return old && JSON.stringify(old.get("definitionToCompare")) !== JSON.stringify(cell.get("definitionToCompare"))
  })

  // 5, 0, 0,
  if (newCells.length + deletedCells.length + changedCells.length > 3) {
    graph.resetCells(cells)
  } else {
    graph.removeCells(deletedCells)
    _updateChangedCells(changedCells)
    graph.addCells(newCells)
  }

  // 6, 2, 0
  _layout(layout)

  boundingRects.forEach(g => g.rect.toBack())
  console.timeEnd("redrawGraph")
}
