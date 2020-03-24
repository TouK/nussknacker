/* eslint-disable i18next/no-literal-string */
import _ from "lodash"
import {GroupId, Process, ProcessDefinitionData} from "../../types"
import {boundingRect, makeElement, makeLink} from "./EspNode"
import NodeUtils from "./NodeUtils"

export function redrawGraph(
  process: Process,
  expandedGroups: GroupId[],
  processCounts,
  forExport: boolean,
  processDefinitionData: ProcessDefinitionData,
  layout,
  graph,
  _updateChangedCells,
  _layout,
) {
  const nodesWithGroups = NodeUtils.nodesFromProcess(process, expandedGroups)
  const edgesWithGroups = NodeUtils.edgesFromProcess(process, expandedGroups)

  const nodes = nodesWithGroups.map(node => makeElement(node, processCounts[node.id], forExport, processDefinitionData.nodesConfig || {}))

  const edges = _.map(edgesWithGroups, (e) => makeLink(e, forExport))

  const boundingRects = NodeUtils.getExpandedGroups(process, expandedGroups).map(expandedGroup => ({
    group: expandedGroup,
    rect: boundingRect(nodes, expandedGroup, layout, NodeUtils.createGroupNode(nodesWithGroups, expandedGroup)),
  }))

  const cells = boundingRects.map(g => g.rect).concat(nodes.concat(edges))

  const newCells = _.filter(cells, cell => !graph.getCell(cell.id))
  const deletedCells = _.filter(graph.getCells(), oldCell => !_.find(cells, cell => cell.id === oldCell.id))
  const changedCells = _.filter(cells, cell => {
    const old = graph.getCell(cell.id)
    //TODO: some different ways of comparing?
    return old && JSON.stringify(old.get("definitionToCompare")) !== JSON.stringify(cell.get("definitionToCompare"))
  })

  if (newCells.length + deletedCells.length + changedCells.length > 3) {
    graph.resetCells(cells)
  } else {
    graph.removeCells(deletedCells)
    _updateChangedCells(changedCells)
    graph.addCells(newCells)
  }

  _layout(layout)

  _.forEach(boundingRects, rect => rect.rect.toBack())
}
