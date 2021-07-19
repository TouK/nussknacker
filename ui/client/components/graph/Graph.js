/* eslint-disable i18next/no-literal-string */
import {dia} from "jointjs"
import "jointjs/dist/joint.css"
import _, {cloneDeep, debounce, isEqual, sortBy} from "lodash"
import PropTypes from "prop-types"
import React from "react"
import {getProcessCategory, getSelectionState} from "../../reducers/selectors/graph"
import {getLoggedUser, getProcessDefinitionData} from "../../reducers/selectors/settings"
import "../../stylesheets/graph.styl"
import {filterDragHovered, setLinksHovered} from "./dragHelpers"
import {updateNodeCounts} from "./EspNode/element"
import {FocusableDiv} from "./focusable"
import {createPaper, directedLayout, drawGraph, isBackgroundObject, isGroupElement, isModelElement} from "./GraphPartialsInTS"
import styles from "./graphTheme.styl"

import * as GraphUtils from "./GraphUtils"
import {Events} from "./joint-events"
import EdgeDetailsModal from "./node-modal/EdgeDetailsModal"
import NodeDetailsModal from "./node-modal/NodeDetailsModal"
import NodeUtils from "./NodeUtils"
import {PanZoomPlugin} from "./PanZoomPlugin"
import {RangeSelectPlugin, SelectionMode} from "./RangeSelectPlugin"
import "./svg-export/export.styl"
import {prepareSvg} from "./svg-export/prepareSvg"

export class Graph extends React.Component {

  static propTypes = {
    processToDisplay: PropTypes.object.isRequired,
    loggedUser: PropTypes.object.isRequired,
    connectDropTarget: PropTypes.func,
    width: PropTypes.string,
    height: PropTypes.string,
  }
  redrawing = false
  directedLayout = directedLayout.bind(this)
  createPaper = createPaper.bind(this)
  drawGraph = drawGraph.bind(this)
  forceLayout = debounce(() => {
    this.directedLayout(this.props.selectionState)
    this.panAndZoom.fitSmallAndLargeGraphs()
  }, 50)

  constructor(props) {
    super(props)

    this.graph = new dia.Graph()
    this.graph.on(Events.REMOVE, (e, f) => {
      if (e.isLink() && !this.redrawing) {
        this.props.actions.nodesDisconnected(e.attributes.source.id, e.attributes.target.id)
      }
    })
    this.nodesMoving()

    this.espGraphRef = React.createRef()
  }

  getEspGraphRef = () => {
    return this.espGraphRef.current
  }

  componentWillUnmount() {
    // force destroy event on model for plugins cleanup
    this.processGraphPaper.model.destroy()
    this.unbindEventHandlers()
  }

  bindEventHandlers() {
    this.changeNodeDetailsOnClick()

    this.processGraphPaper.on(Events.BLANK_POINTERUP, event => {
      if (!event.isPropagationStopped()) {
        if (this.props.fetchedProcessDetails != null) {
          this.props.actions.displayNodeDetails(this.props.fetchedProcessDetails.json.properties)
          this.props.actions.resetSelection()
        }
      }
    })
    this.hooverHandling()
  }

  unbindEventHandlers() {

  }

  componentDidMount() {
    this.processGraphPaper = this.createPaper()
    this.processGraphPaper.freeze()
    this.drawGraph(
      this.props.processToDisplay,
      this.props.layout,
      this.props.processDefinitionData,
    )
    this.processGraphPaper.unfreeze()
    this._prepareContentForExport()

    // event handlers binding below. order sometimes matters
    this.panAndZoom = new PanZoomPlugin(this.processGraphPaper)
    new RangeSelectPlugin(this.processGraphPaper)
    this.processGraphPaper.on("rangeSelect:selected", ({elements, mode}) => {
      const nodes = elements
        .filter(el => isModelElement(el) || isGroupElement(el))
        .map(({id}) => id)
      if (mode === SelectionMode.toggle) {
        this.props.actions.toggleSelection(...nodes)
      } else {
        this.props.actions.resetSelection(...nodes)
      }
    })

    this.bindEventHandlers()
    this.highlightNodes(this.props.processToDisplay, this.props.nodeToDisplay)
    this.updateNodesCounts()

    this.graph.on(Events.CHANGE_DRAG_OVER, () => {
      const links = this.graph.getLinks()
      links.forEach(l => this.unhighlightCell(l, styles.dragHovered))

      const [active] = filterDragHovered(links)

      if (active) {
        this.highlightCell(active, styles.dragHovered)
        active.toBack()
      }
    })

    this.graph.on(Events.ADD, (cell) => {
      this.handleInjectBetweenNodes(cell)
      setLinksHovered(this.graph)
    })

    this.panAndZoom.fitSmallAndLargeGraphs()
  }

  canAddNode(node) {
    return this.props.capabilities.write &&
      NodeUtils.isNode(node) &&
      !NodeUtils.nodeIsGroup(node) &&
      NodeUtils.isAvailable(node, this.props.processDefinitionData, this.props.processCategory)
  }

  addNode(node, position) {
    if (this.canAddNode(node)) {
      this.props.actions.nodeAdded(node, position)
    }
  }

  componentWillUpdate(nextProps, nextState) {
    const processChanged = !isEqual(this.props.processToDisplay, nextProps.processToDisplay) ||
      !isEqual(this.props.layout, nextProps.layout) ||
      !isEqual(this.props.expandedGroups, nextProps.expandedGroups) ||
      !isEqual(this.props.processDefinitionData, nextProps.processDefinitionData)
    if (processChanged) {
      this.drawGraph(
        nextProps.processToDisplay,
        nextProps.layout,
        nextProps.processDefinitionData,
      )
    }

    //when e.g. layout changed we have to remember to highlight nodes
    const nodeToDisplayChanged = !isEqual(this.props.nodeToDisplay, nextProps.nodeToDisplay)
    const selectedNodesChanged = !isEqual(this.props.selectionState, nextProps.selectionState)
    if (processChanged || nodeToDisplayChanged || selectedNodesChanged) {
      this.highlightNodes(nextProps.processToDisplay, nextProps.nodeToDisplay, nextProps.selectionState)
    }
  }

  componentDidUpdate(prevProps, prevState) {
    const {processCounts} = this.props
    if (!isEqual(processCounts, prevProps.processCounts)) {
      this.updateNodesCounts()
    }
  }

  updateNodesCounts() {
    const {processCounts} = this.props
    const nodes = this.graph.getElements().filter(isModelElement)
    nodes.forEach(updateNodeCounts(processCounts))
  }

  zoomIn() {
    this.panAndZoom.zoomIn()
  }

  zoomOut() {
    this.panAndZoom.zoomOut()
  }

  async exportGraph() {
    return await prepareSvg(this._exportGraphOptions)
  }

  validateConnection = (cellViewS, magnetS, cellViewT, magnetT) => {
    const from = cellViewS.model.id
    const to = cellViewT.model.id
    return magnetT && NodeUtils.canMakeLink(from, to, this.props.processToDisplay, this.props.processDefinitionData)
  }

  disconnectPreviousEdge = (previousEdge) => {
    const nodeIds = previousEdge.split("-").slice(0, 2)
    if (this.graphContainsEdge(nodeIds)) {
      this.props.actions.nodesDisconnected(...nodeIds)
    }
  }

  graphContainsEdge(nodeIds) {
    return this.props.processToDisplay.edges.some(edge => edge.from === nodeIds[0] && edge.to === nodeIds[1])
  }

  handleInjectBetweenNodes = (middleMan) => {
    const links = this.graph.getLinks()
    const [linkBelowCell] = filterDragHovered(links)

    if (linkBelowCell && middleMan) {
      const source = this.graph.getCell(linkBelowCell.getSourceElement().id)
      const target = this.graph.getCell(linkBelowCell.getTargetElement().id)

      const middleManNode = middleMan.attributes.nodeData

      const sourceNodeData = source.attributes.nodeData
      const sourceNode = NodeUtils.nodeIsGroup(sourceNodeData) ? _.last(sourceNodeData.nodes) : sourceNodeData

      const targetNodeData = target.attributes.nodeData
      const targetNode = NodeUtils.nodeIsGroup(targetNodeData) ? _.head(targetNodeData.nodes) : targetNodeData

      if (NodeUtils.nodeIsGroup(middleManNode)) {
        if (!NodeUtils.groupIncludesOneOfNodes(middleManNode, [sourceNode.id, targetNode.id])) {
          // TODO: handle inject when group is middleman
          this.props.notificationActions.info("Injecting group is not possible yet")
        }
      } else if (NodeUtils.nodesAreInOneGroup(this.props.processToDisplay, [sourceNode.id, targetNode.id])) {
        // TODO: handle inject when source and target are in one group
        this.props.notificationActions.info("Injecting node in group is not possible yet")
      } else if (GraphUtils.canInjectNode(
        this.props.processToDisplay,
        sourceNode.id,
        middleMan.id,
        targetNode.id,
        this.props.processDefinitionData,
      )) {
        //TODO: consider doing inject check in actions.js?
        this.props.actions.injectNode(
          sourceNode,
          middleManNode,
          targetNode,
          linkBelowCell.attributes.edgeData.edgeType,
        )
      }
    }
  }

  _prepareContentForExport = () => {
    const {options, defs} = this.processGraphPaper
    this._exportGraphOptions = {
      options: cloneDeep(options),
      defs: defs.cloneNode(true),
    }
  }

  highlightNodes = (data, nodeToDisplay, selectionState) => {
    this.graph.getCells().forEach(cell => {
      this.unhighlightCell(cell, "node-validation-error")
      this.unhighlightCell(cell, "node-focused")
      this.unhighlightCell(cell, "node-focused-with-validation-error")
    })

    const invalidNodeIds = _.keys((data.validationResult && data.validationResult.errors || {}).invalidNodes)
    const selectedNodeIds = selectionState || []

    invalidNodeIds.forEach(id => selectedNodeIds.includes(id) ?
      this.highlightNode(id, "node-focused-with-validation-error") :
      this.highlightNode(id, "node-validation-error"))

    selectedNodeIds.forEach(id => {
      if (!invalidNodeIds.includes(id)) {
        this.highlightNode(id, "node-focused")
      }
    })
  }

  highlightCell(cell, className) {
    this.processGraphPaper.findViewByModel(cell).highlight(null, {
      highlighter: {
        name: "addClass",
        options: {className: className},
      },
    })
  }

  unhighlightCell(cell, className) {
    this.processGraphPaper.findViewByModel(cell).unhighlight(null, {
      highlighter: {
        name: "addClass",
        options: {className: className},
      },
    })
  }

  highlightNode = (nodeId, highlightClass) => {
    const cell = this.graph.getCell(nodeId)
    if (cell) { //prevent `properties` node highlighting
      this.highlightCell(cell, highlightClass)
    }
  }

  changeLayoutIfNeeded = () => {
    const {layout, actions} = this.props
    const elements = this.graph.getElements().filter(isModelElement)
    const newLayout = sortBy(
      elements.map(el => {
        const {x, y} = el.get("position")
        return {id: el.id, position: {x, y}}
      }),
      e => e.id,
    )
    if (!isEqual(layout, newLayout)) {
      actions?.layoutChanged(newLayout)
    }
  }

  changeNodeDetailsOnClick() {
    this.processGraphPaper.on(Events.CELL_POINTERDBLCLICK, (cellView) => {
      const nodeDataId = cellView.model.attributes.nodeData?.id
      if (nodeDataId) {
        const nodeData = this.getNodeData(cellView.model)
        const prefixedNodeId = this.props.nodeIdPrefixForSubprocessTests + nodeDataId
        this.props.actions.displayModalNodeDetails({...nodeData, id: prefixedNodeId}, this.props.readonly)
      }

      if (cellView.model.attributes.edgeData) {
        this.props.actions.displayModalEdgeDetails(cellView.model.attributes.edgeData)
      }
    })

    if (this.props.singleClickNodeDetailsEnabled) {
      this.processGraphPaper.on(Events.CELL_POINTERCLICK, (cellView, evt) => {

        const nodeDataId = cellView.model.attributes.nodeData?.id
        if (!nodeDataId) {
          return
        }

        this.props.actions.displayNodeDetails(this.getNodeData(cellView.model))

        if (evt.shiftKey || evt.ctrlKey || evt.metaKey) {
          this.props.actions.toggleSelection(nodeDataId)
        } else {
          this.props.actions.resetSelection(nodeDataId)
        }

        //TODO: is this the best place for this? if no, where should it be?
        const targetClass = _.get(evt, "originalEvent.target.className.baseVal")
        if (targetClass.includes("collapseIcon") && nodeDataId) {
          this.props.actions.collapseGroup(nodeDataId)
        }

        if (targetClass.includes("expandIcon") && nodeDataId) {
          this.props.actions.expandGroup(nodeDataId)
        }
      })
    }
  }

  hooverHandling() {
    this.processGraphPaper.on(Events.CELL_MOUSEOVER, (cellView) => {
      const model = cellView.model
      this.showLabelOnHover(model)
      this.showBackgroundIcon(model)
    })
    this.processGraphPaper.on(Events.BLANK_MOUSEOVER, () => {
      this.hideBackgroundsIcons()
    })
  }

  getNodeData(model) {
    const {processToDisplay} = this.props
    return NodeUtils.getNodeById(model.attributes.nodeData.id, processToDisplay)
  }

  //needed for proper switch/filter label handling
  showLabelOnHover(model) {
    if (!isBackgroundObject(model)) {
      model.toFront()
    }
    return model
  }

  //background is below normal node, we cannot use normal hover/mouseover/mouseout...
  showBackgroundIcon(model) {
    if (isBackgroundObject(model)) {
      const el = model.findView(this.processGraphPaper).vel
      el.toggleClass("forced-hover", true)
    }
  }

  hideBackgroundsIcons() {
    this.graph.getElements().filter(isBackgroundObject).forEach(model => {
      const el = model.findView(this.processGraphPaper).vel
      el.toggleClass("forced-hover", false)
    })
  }

  moveSelectedNodesRelatively(element, position) {
    this.redrawing = true
    const movedNodeId = element.id
    const nodeIdsToBeMoved = _.without(this.props.selectionState, movedNodeId)
    const cellsToBeMoved = nodeIdsToBeMoved.map(nodeId => this.graph.getCell(nodeId))
    const {position: originalPosition} = this.findNodeInLayout(movedNodeId)
    const offset = {x: position.x - originalPosition.x, y: position.y - originalPosition.y}
    cellsToBeMoved.filter(isModelElement).forEach(cell => {
      const {position: originalPosition} = this.findNodeInLayout(cell.id)
      cell.position(originalPosition.x + offset.x, originalPosition.y + offset.y)
    })
    this.redrawing = false
  }

  findNodeInLayout(nodeId) {
    return _.find(this.props.layout, n => n.id === nodeId)
  }

  nodesMoving() {
    this.graph.on(Events.CHANGE_POSITION, (element, position) => {
      if (!this.redrawing && this.props.selectionState?.includes(element.id) && isModelElement(element)) {
        this.moveSelectedNodesRelatively(element, position)
      }
    })
  }

  render() {
    const toRender = (
      <div id="graphContainer" style={{padding: this.props.padding}}>
        {this.props.showNodeDetailsModal ? <NodeDetailsModal/> : null}
        {!_.isEmpty(this.props.edgeToDisplay) ? <EdgeDetailsModal/> : null}
        <FocusableDiv ref={this.espGraphRef} id={this.props.divId}/>
      </div>
    )

    return this.props.connectDropTarget ? this.props.connectDropTarget(toRender) : toRender
  }
}

export function commonState(state) {
  return {
    layout: [],
    processCategory: getProcessCategory(state),
    loggedUser: getLoggedUser(state),
    processDefinitionData: getProcessDefinitionData(state),
    selectionState: getSelectionState(state),
  }
}

export const subprocessParent = "modal-content"
