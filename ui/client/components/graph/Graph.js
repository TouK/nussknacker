/* eslint-disable i18next/no-literal-string */
import * as joint from "jointjs"
// import "jointjs/dist/joint.css"
import _, {isEmpty, isEqual} from "lodash"
import PropTypes from "prop-types"
import React from "react"
import svgPanZoom from "svg-pan-zoom"
import SVGUtils from "../../common/SVGUtils"
import {getProcessCategory, getSelectionState} from "../../reducers/selectors/graph"
import {getLoggedUser, getProcessDefinitionData} from "../../reducers/selectors/settings"
import cssVariables from "../../stylesheets/_variables.styl"
import "../../stylesheets/graph.styl"
import {directedLayout} from "./directedLayout.ts"
import {getPaper} from "./getPaper"
import * as GraphUtils from "./GraphUtils"
import * as JointJsGraphUtils from "./JointJsGraphUtils"
import EdgeDetailsModal from "./node-modal/EdgeDetailsModal"
import NodeDetailsModal from "./node-modal/NodeDetailsModal"
import NodeUtils from "./NodeUtils"
import {redrawGraph} from "./redrawGraph"

export class Graph extends React.Component {

  redrawing = false

  static propTypes = {
    processToDisplay: PropTypes.object.isRequired,
    groupingState: PropTypes.array,
    loggedUser: PropTypes.object.isRequired,
    connectDropTarget: PropTypes.func,
  }

  constructor(props) {
    super(props)

    this.graph = new joint.dia.Graph()
    this.graph.on("remove", (e) => {
      if (e.isLink && !this.redrawing) {
        this.props.actions.nodesDisconnected(e.attributes.source.id, e.attributes.target.id)
      }
    })
    this.nodesMoving()

    this.espGraphRef = React.createRef()
    this.parent = document.getElementById(this.props.parent)

    this.windowListeners = {
      resize: this.updateDimensions.bind(this),
    }
  }

  getEspGraphRef = () => {
    return this.espGraphRef.current
  }

  componentDidMount() {
    const {processCounts, layout, expandedGroups, nodeToDisplay, processDefinitionData, processToDisplay} = this.props
    this.processGraphPaper = this.createPaper()

    this.drawGraph(processToDisplay, layout, processCounts, processDefinitionData, true, [])
    this._prepareContentForExport()

    this.drawGraph(processToDisplay, layout, processCounts, processDefinitionData, false, expandedGroups)
    this.panAndZoom = this.enablePanZoom()
    this.changeNodeDetailsOnClick()
    this.hooverHandling()
    this.cursorBehaviour()
    this.highlightNodes(processToDisplay, nodeToDisplay)
    _.forOwn(this.windowListeners, (listener, type) => window.addEventListener(type, listener))
    this.updateDimensions()
  }

  updateDimensions() {
    this.processGraphPaper.fitToContent()
    this.updateSvgDimensions(this.parent.offsetWidth, this.parent.offsetHeight)
    if (this.props.parent !== subprocessParent) {
      this.processGraphPaper.setDimensions(this.parent.offsetWidth, this.parent.offsetHeight)
    }
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

  componentWillUnmount() {
    _.forOwn(this.windowListeners, (listener, type) => window.removeEventListener(type, listener))
  }

  componentWillUpdate(nextProps, nextState) {
    const processChanged = !_.isEqual(this.props.processToDisplay, nextProps.processToDisplay) ||
      !_.isEqual(this.props.layout, nextProps.layout) ||
      !_.isEqual(this.props.processCounts, nextProps.processCounts) ||
      !_.isEqual(this.props.groupingState, nextProps.groupingState) ||
      !_.isEqual(this.props.expandedGroups, nextProps.expandedGroups) ||
      !_.isEqual(this.props.processDefinitionData, nextProps.processDefinitionData)
    if (processChanged) {
      this.drawGraph(
        nextProps.processToDisplay,
        nextProps.layout,
        nextProps.processCounts,
        nextProps.processDefinitionData,
        false,
        nextProps.expandedGroups,
      )
    }

    //when e.g. layout changed we have to remember to highlight nodes
    const nodeToDisplayChanged = !_.isEqual(this.props.nodeToDisplay, nextProps.nodeToDisplay)
    const selectedNodesChanged = !_.isEqual(this.props.selectionState, nextProps.selectionState)
    if (processChanged || nodeToDisplayChanged || selectedNodesChanged) {
      this.highlightNodes(nextProps.processToDisplay, nextProps.nodeToDisplay, nextProps.groupingState, nextProps.selectionState)
    }
  }

  componentDidUpdate(previousProps) {
    //we have to do this after render, otherwise graph is not fully initialized yet
    const diff = _.difference(this.props.processToDisplay.nodes.map(n => n.id), previousProps.processToDisplay.nodes.map(n => n.id))
    diff.forEach(nid => {
      const cell = JointJsGraphUtils.findCell(this.graph, nid)
      const cellView = this.processGraphPaper.findViewByModel(cell)
      if (cellView) {
        this.handleInjectBetweenNodes(cellView)
      }
    })
  }

  directedLayout() {
    console.time("layout2")
    directedLayout(this.graph, this.changeLayoutIfNeeded.bind(this))
    console.timeEnd("layout2")
  }

  zoomIn() {
    this.panAndZoom.zoomIn()
  }

  zoomOut() {
    this.panAndZoom.zoomOut()
  }

  exportGraph() {
    return this.state.exported
  }

  validateConnection() {
    const {processToDisplay, processDefinitionData} = this.props
    return (cellViewS, magnetS, cellViewT, magnetT) => {
      const from = cellViewS.model.id
      const to = cellViewT.model.id
      return magnetT && NodeUtils.canMakeLink(from, to, processToDisplay, processDefinitionData)
    }
  }

  createPaper = () => {
    const canWrite = this.props.loggedUser.canWrite(this.props.processCategory) && !this.props.readonly
    const el = this.getEspGraphRef()
    const height = this.parent.clientHeight
    const width = this.parent.clientWidth - 2 * this.props.padding
    const model = this.graph
    const validateConnection = this.validateConnection()
    return getPaper({el, height, width, model, canWrite, validateConnection})
      .on("cell:pointerup", (cellView, evt, x, y) => {
        this.changeLayoutIfNeeded()
        this.handleInjectBetweenNodes(cellView)
      })
      .on("link:connect", (c) => {
        this.disconnectPreviousEdge(c.model.id)
        this.props.actions.nodesConnected(
          c.sourceView.model.attributes.nodeData,
          c.targetView.model.attributes.nodeData,
        )
      })
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

  handleInjectBetweenNodes = (cellView) => {
    const linkBelowCell = JointJsGraphUtils.findLinkBelowCell(this.graph, cellView, this.processGraphPaper)
    if (linkBelowCell) {
      const source = JointJsGraphUtils.findCell(this.graph, linkBelowCell.attributes.source.id)
      const target = JointJsGraphUtils.findCell(this.graph, linkBelowCell.attributes.target.id)
      const middleMan = cellView.model
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

  drawGraph = (process, layout, processCounts, processDefinitionData, forExport, expandedGroups) => {
    const {graph, _updateChangedCells, _layout} = this
    this.redrawing = true
    redrawGraph(
      process,
      expandedGroups,
      processCounts,
      forExport,
      processDefinitionData,
      layout,
      graph,
      _updateChangedCells.bind(this),
      _layout.bind(this),
    )
    this.redrawing = false
  }

  _layout(layout) {
    if (isEmpty(layout)) {
      this.directedLayout()
    } else {
      layout.forEach(el => {
        const cell = this.graph.getCell(el.id)
        if (cell && JSON.stringify(cell.get("position")) !== JSON.stringify(el.position)) {
          cell.set("position", el.position)
        }
      })
    }
  }

  _updateChangedCells(changedCells) {
    _.forEach(changedCells, cell => {
      const cellToRemove = this.graph.getCell(cell.id)
      const links = cellToRemove.isElement ? this.graph.getConnectedLinks(cellToRemove) : []
      cellToRemove.remove()
      this.graph.addCell(cell)
      _.forEach(links, l => {
        l.remove()
        this.graph.addCell(l)
      })
    })
  }

  _prepareContentForExport = () => {
    const oldHeight = this.getEspGraphRef().offsetHeight
    const oldWidth = this.getEspGraphRef().offsetWidth
    //we fit to content to be able to export svg nicely...
    this.processGraphPaper.fitToContent()

    //Hack for FOP to properly export image from svg xml
    let svg = this.updateSvgDimensions(oldWidth, oldHeight)
    this.setState({exported: SVGUtils.toXml(svg)})

    //we have to set former width/height
    this.processGraphPaper.setDimensions(oldWidth, oldHeight)
  }

  updateSvgDimensions = (width, height) => {
    let svg = this.getEspGraphRef().getElementsByTagName("svg")[0]
    svg.setAttribute("width", width)
    svg.setAttribute("height", height)
    return svg
  }

  highlightNodes = (data, nodeToDisplay, groupingState, selectionState) => {
    this.graph.getCells().forEach(cell => {
      this.unhighlightCell(cell, "node-validation-error")
      this.unhighlightCell(cell, "node-focused")
      this.unhighlightCell(cell, "node-focused-with-validation-error")
      this.unhighlightCell(cell, "node-grouping")
    })

    const invalidNodeIds = _.keys((data.validationResult && data.validationResult.errors || {}).invalidNodes)
    const selectedNodeIds = selectionState || []

    invalidNodeIds.forEach(id => selectedNodeIds.includes(id) ?
      this.highlightNode(id, "node-focused-with-validation-error") : this.highlightNode(id, "node-validation-error"));

    (groupingState || []).forEach(id => this.highlightNode(id, "node-grouping"))
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

  changeLayoutIfNeeded() {
    const graph = this.graph
    const {readonly, layout, actions} = this.props

    const newLayout = graph.getElements()
      .filter(el => !el.get("backgroundObject"))
      .map(el => ({id: el.id, position: el.get("position")}))

    if (!isEqual(layout, newLayout) && !readonly) {
      actions && actions.layoutChanged(newLayout)
    }
  }

  enablePanZoom() {
    const svgElement = this.getEspGraphRef().getElementsByTagName("svg").item(0)

    const panAndZoom = svgPanZoom(svgElement, {
      viewportSelector: ".svg-pan-zoom_viewport",
      fit: this.props.processToDisplay.nodes.length > 1,
      zoomScaleSensitivity: 0.4,
      controlIconsEnabled: false,
      panEnabled: false,
      dblClickZoomEnabled: false,
      minZoom: 0.2,
      maxZoom: 10,
    })

    this.processGraphPaper.on("blank:pointerdown", () => {
      panAndZoom.enablePan()
    })

    this.processGraphPaper.on("cell:pointerup blank:pointerup", () => {
      panAndZoom.disablePan()
    })

    this.fitSmallAndLargeGraphs(panAndZoom)
    return panAndZoom
  }

  fitSmallAndLargeGraphs = (panAndZoom) => {
    const realZoom = panAndZoom.getSizes().realZoom
    const toZoomBy = realZoom > 1 ? 1 / realZoom : 0.90 //the bigger zoom, the further we get
    panAndZoom.zoomBy(toZoomBy)
  }

  changeNodeDetailsOnClick() {
    this.processGraphPaper.on("cell:pointerdblclick", (cellView) => {
      if (this.props.groupingState) {
        return
      }

      const nodeData = cellView.model.attributes.nodeData
      if (nodeData) {
        const prefixedNodeId = this.props.nodeIdPrefixForSubprocessTests + nodeData.id
        this.props.actions.displayModalNodeDetails({...nodeData, id: prefixedNodeId}, this.props.readonly)
      }

      if (cellView.model.attributes.edgeData) {
        this.props.actions.displayModalEdgeDetails(cellView.model.attributes.edgeData)
      }
    })

    if (this.props.singleClickNodeDetailsEnabled) {
      this.processGraphPaper.on("cell:pointerclick", (cellView, evt, x, y) => {

        const nodeData = cellView.model.attributes.nodeData
        if (!nodeData) {
          return
        }

        this.props.actions.displayNodeDetails(cellView.model.attributes.nodeData)

        if (evt.ctrlKey || evt.metaKey) {
          this.props.actions.expandSelection(nodeData.id)
        } else {
          this.props.actions.resetSelection(nodeData.id)
        }

        //TODO: is this the best place for this? if no, where should it be?
        const targetClass = _.get(evt, "originalEvent.target.className.baseVal")
        if (targetClass.includes("collapseIcon") && nodeData) {
          this.props.actions.collapseGroup(nodeData.id)
        }

        if (targetClass.includes("expandIcon") && nodeData) {
          this.props.actions.expandGroup(nodeData.id)
        }
      })
    }

    this.processGraphPaper.on("blank:pointerdown", () => {
      if (this.props.fetchedProcessDetails != null) {
        this.props.actions.displayNodeDetails(this.props.fetchedProcessDetails.json.properties)
        this.props.actions.resetSelection()
      }
    })
  }

  hooverHandling() {
    this.processGraphPaper.on("cell:mouseover", (cellView) => {
      const model = cellView.model
      this.showLabelOnHover(model)
      this.showBackgroundIcon(model)
    })
    this.processGraphPaper.on("cell:mouseout", (cellView, evt) => {
      this.hideBackgroundIcon(cellView.model, evt)
    })
  }

  //needed for proper switch/filter label handling
  showLabelOnHover(model) {
    if (model.get && !model.get("backgroundObject")) {
      model.toFront()
    }
    return model
  }

  //background is below normal node, we cannot use normal hover/mouseover/mouseout...
  showBackgroundIcon(model) {
    if (model.get && model.get("backgroundObject")) {
      const el = this.processGraphPaper.findViewByModel(model).vel
      el.addClass("nodeIconForceHoverBox")
      el.removeClass("nodeIconForceNoHoverBox")
    }
  }

  //background is below normal node, we cannot use normal hover/mouseover/mouseout...
  hideBackgroundIcon(model, evt) {
    if (model.get && model.get("backgroundObject")) {
      if (!this.checkIfCursorInRect(model, evt)) {
        const el = this.processGraphPaper.findViewByModel(model).vel
        el.removeClass("nodeIconForceHoverBox")
        el.addClass("nodeIconForceNoHoverBox")
      }

    }
  }

  checkIfCursorInRect(model, evt) {
    const relOffset = this.computeRelOffset({x: evt.clientX, y: evt.clientY})
    const position = model.attributes.position
    const size = model.attributes.size
    return relOffset.x >= position.x && relOffset.y >= position.y && relOffset.x <= position.x + size.width && relOffset.y <= position.y + size.height
  }

  cursorBehaviour() {
    this.processGraphPaper.on("blank:pointerdown", (evt, x, y) => {
      if (this.getEspGraphRef()) {
        this.getEspGraphRef().style.cursor = "move"
      }
    })

    this.processGraphPaper.on("blank:pointerup", (evt, x, y) => {
      if (this.getEspGraphRef()) {
        this.getEspGraphRef().style.cursor = "auto"
      }
    })
  }

  computeRelOffset(pointerOffset) {
    const pan = this.panAndZoom ? this.panAndZoom.getPan() : {x: 0, y: 0}
    const zoom = this.panAndZoom ? this.panAndZoom.getSizes().realZoom : 1

    //TODO: is it REALLY ok?
    const paddingLeft = cssVariables.svgGraphPaddingLeft
    const paddingTop = cssVariables.svgGraphPaddingTop

    const element = document.getElementById(this.props.divId)
    const svg = element.getElementsByTagName("svg").item(0)
    const graphPosition = svg.getBoundingClientRect()

    return {
      x: (pointerOffset.x - pan.x - graphPosition.left - paddingLeft) / zoom,
      y: (pointerOffset.y - pan.y - graphPosition.top - paddingTop) / zoom,
    }
  }

  moveSelectedNodesRelatively(element, position) {
    const movedNodeId = element.id
    const nodeIdsToBeMoved = _.without(this.props.selectionState, movedNodeId)
    const cellsToBeMoved = nodeIdsToBeMoved.map(nodeId => this.graph.getCell(nodeId))
    const originalPosition = _.find(this.props.layout, n => n.id === movedNodeId).position
    const offset = {x: position.x - originalPosition.x, y: position.y - originalPosition.y}
    cellsToBeMoved.forEach(cell => {
      const originalPosition = _.find(this.props.layout, n => n.id === cell.id).position
      cell.position(originalPosition.x + offset.x, originalPosition.y + offset.y)
    })
  }

  nodesMoving() {
    this.graph.on("change:position", (element, position) => {
      if (!this.redrawing && (this.props.selectionState || []).includes(element.id)) {
        this.moveSelectedNodesRelatively(element, position)
      }
    })
  }

  render() {
    const toRender = (
      <div id="graphContainer" style={{padding: this.props.padding}}>
        {this.props.showNodeDetailsModal ? <NodeDetailsModal /> : null}
        {!_.isEmpty(this.props.edgeToDisplay) ? <EdgeDetailsModal /> : null}
        <div ref={this.espGraphRef} id={this.props.divId}></div>
      </div>
    )

    return this.props.connectDropTarget ? this.props.connectDropTarget(toRender) : toRender
  }
}

export function commonState(state) {
  return {
    processCategory: getProcessCategory(state),
    loggedUser: getLoggedUser(state),
    processDefinitionData: getProcessDefinitionData(state),
    selectionState: getSelectionState(state),
  }
}

export const subprocessParent = "modal-content"
