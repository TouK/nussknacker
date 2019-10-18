import React from 'react'
import * as joint from 'jointjs'
import * as dagre from 'dagre'
import EspNode from './EspNode'
import 'jointjs/dist/joint.css'
import _ from 'lodash'
import svgPanZoom from 'svg-pan-zoom'
import {connect} from 'react-redux'
import ActionsUtils from '../../actions/ActionsUtils'
import NodeDetailsModal from './NodeDetailsModal'
import EdgeDetailsModal from './EdgeDetailsModal'
import {DropTarget} from 'react-dnd'
import '../../stylesheets/graph.styl'
import SVGUtils from '../../common/SVGUtils';
import NodeUtils from './NodeUtils.js'
import cssVariables from "../../stylesheets/_variables.styl"
import * as GraphUtils from "./GraphUtils"
import * as JointJsGraphUtils from "./JointJsGraphUtils"
import PropTypes from 'prop-types'

class Graph extends React.Component {

  redrawing = false

  static propTypes = {
    processToDisplay: PropTypes.object.isRequired,
    groupingState: PropTypes.array,
    loggedUser: PropTypes.object.isRequired,
    connectDropTarget: PropTypes.func
  }

  constructor(props) {
    super(props);

    this.graph = new joint.dia.Graph();
    this.graph.on("remove", (e, f) => {
      if (e.isLink && !this.redrawing) {
        this.props.actions.nodesDisconnected(e.attributes.source.id, e.attributes.target.id)
      }
    })
    this.nodesMoving();

    this.espGraphRef = React.createRef()

    this.windowListeners = {
      resize: this.updateDimensions.bind(this)
    }
  }

  getEspGraphRef = () => {
    return this.espGraphRef.current
  }

  componentDidMount() {
    this.processGraphPaper = this.createPaper()
    this.drawGraph(this.props.processToDisplay, this.props.layout, this.props.processCounts, true, [])
    this._prepareContentForExport()
    this.drawGraph(this.props.processToDisplay, this.props.layout, this.props.processCounts, false, this.props.expandedGroups)
    this.panAndZoom = this.enablePanZoom();
    this.changeNodeDetailsOnClick();
    this.hooverHandling();
    this.cursorBehaviour();
    this.highlightNodes(this.props.processToDisplay, this.props.nodeToDisplay);
    _.forOwn(this.windowListeners, (listener, type) => window.addEventListener(type, listener))
  }

  updateDimensions() {
    let area = document.getElementById('working-area')
    this.processGraphPaper.fitToContent()
    this.svgDimensions(area.offsetWidth, area.offsetHeight)
    this.processGraphPaper.setDimensions(area.offsetWidth, area.offsetHeight)
  }

  canAddNode(node) {
    return this.props.capabilities.write &&
      NodeUtils.isNode(node) &&
      !NodeUtils.nodeIsGroup(node) &&
      NodeUtils.isAvailable(node, this.props.processDefinitionData, this.props.processCategory)
  }

  addNode(node, position) {
    if (this.canAddNode(node)) {
      this.props.actions.nodeAdded(node, position);
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
      !_.isEqual(this.props.expandedGroups, nextProps.expandedGroups)
    if (processChanged) {
      this.drawGraph(nextProps.processToDisplay, nextProps.layout, nextProps.processCounts, false, nextProps.expandedGroups)
    }

    //when e.g. layout changed we have to remember to highlight nodes
    const nodeToDisplayChanged = !_.isEqual(this.props.nodeToDisplay, nextProps.nodeToDisplay)
    const selectedNodesChanged = !_.isEqual(this.props.selectionState, nextProps.selectionState)
    if (processChanged || nodeToDisplayChanged || selectedNodesChanged) {
      this.highlightNodes(nextProps.processToDisplay, nextProps.nodeToDisplay, nextProps.groupingState, nextProps.selectionState);
    }
  }

  componentDidUpdate(previousProps) {
    //we have to do this after render, otherwise graph is not fully initialized yet
    const diff = _.difference(this.props.processToDisplay.nodes.map(n => n.id), previousProps.processToDisplay.nodes.map(n => n.id));
    diff.forEach(nid => {
      const cell = JointJsGraphUtils.findCell(this.graph, nid);
      const cellView = this.processGraphPaper.findViewByModel(cell);
      if (cellView) {
        this.handleInjectBetweenNodes(cellView);
      }
    })
  }

  directedLayout() {
    //TODO `layout` method can take graph or cells
    //when joint.layout.DirectedGraph.layout(this.graph) is used here
    //  then `toFront()` method works as expected but there are issues with group fold/unfold
    //when joint.layout.DirectedGraph.layout(this.graph.getCells().filter(cell => !cell.get('backgroundObject')) is used here
    // then `toFront()` method does not work at all, but group fold/unfold works just fine
    joint.layout.DirectedGraph.layout(this.graph.getCells().filter(cell => !cell.get('backgroundObject')), {
      graphlib: dagre.graphlib,
      dagre: dagre,
      nodeSep: 0,
      edgeSep: 0,
      rankSep: 75,
      minLen: 0,
      rankDir: "TB"
    });
    this.changeLayoutIfNeeded()
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

  validateConnection = (cellViewS, magnetS, cellViewT, magnetT) => {
    const from = cellViewS.model.id
    const to = cellViewT.model.id
    return magnetT && NodeUtils.canMakeLink(from, to, this.props.processToDisplay, this.props.processDefinitionData);
  }

  createPaper = () => {
    const canWrite = this.props.loggedUser.canWrite(this.props.processCategory) && !this.props.readonly;
    return new joint.dia.Paper({
      el: this.getEspGraphRef(),
      gridSize: 1,
      height: this.getEspGraphRef().offsetHeight,
      width: this.getEspGraphRef().offsetWidth,
      model: this.graph,
      snapLinks: {radius: 75},
      interactive: function (cellView) {
        const model = cellView.model
        if (!canWrite) {
          return false;
        } else if (model instanceof joint.dia.Link) {
          // Disable the default vertex add and label move functionality on pointerdown.
          return {vertexAdd: false, labelMove: false};
        } else if (model.get && model.get('backgroundObject')) {
          //Disable moving group rect
          return false
        } else {
          return true;
        }
      },
      linkPinning: false,
      defaultLink: EspNode.makeLink({}),
      validateConnection: this.validateConnection
    })
      .on("cell:pointerup", (cellView, evt, x, y) => {
        this.changeLayoutIfNeeded()
        this.handleInjectBetweenNodes(cellView)
      })
      .on("link:connect", (c) => {
        this.props.actions.nodesConnected(
          c.sourceView.model.attributes.nodeData,
          c.targetView.model.attributes.nodeData
        )
      })
  }

  handleInjectBetweenNodes = (cellView) => {
    const linkBelowCell = JointJsGraphUtils.findLinkBelowCell(this.graph, cellView, this.processGraphPaper)
    if (linkBelowCell) {
      const source = JointJsGraphUtils.findCell(this.graph, linkBelowCell.attributes.source.id)
      const target = JointJsGraphUtils.findCell(this.graph, linkBelowCell.attributes.target.id)
      const middleMan = cellView.model
      //TODO: consider doing this check in actions.js?
      if (GraphUtils.canInjectNode(this.props.processToDisplay, source, middleMan, target, this.props.processDefinitionData)) {
        this.props.actions.injectNode(
          source.attributes.nodeData,
          middleMan.attributes.nodeData,
          target.attributes.nodeData,
          linkBelowCell.attributes.edgeData.edgeType
        )
      }
    }
  }

  time = (start, name) => {
    const now = window.performance.now()
    //uncomment to track performance...
    //console.log("time: ", name, now - start)
    return now
  }

  drawGraph = (process, layout, processCounts, forExport, expandedGroups) => {
    this.redrawing = true

    //leaving performance debug for now, as there is still room for improvement:
    //handling forExport and processCounts without need of full redraw
    const performance = window.performance;
    let t = performance.now();

    const nodesWithGroups = NodeUtils.nodesFromProcess(process, expandedGroups)
    const edgesWithGroups = NodeUtils.edgesFromProcess(process, expandedGroups)
    t = this.time(t, 'start')

    const nodes = _.map(nodesWithGroups, (n) => {
      return EspNode.makeElement(n, processCounts[n.id], forExport, this.props.processDefinitionData.nodesConfig || {})
    })

    t = this.time(t, 'nodes')

    const edges = _.map(edgesWithGroups, (e) => EspNode.makeLink(e, forExport))
    t = this.time(t, 'links')

    const boundingRects = NodeUtils.getExpandedGroups(process, expandedGroups).map(expandedGroup => ({
      group: expandedGroup,
      rect: EspNode.boundingRect(nodes, expandedGroup, layout,
        NodeUtils.createGroupNode(nodesWithGroups, expandedGroup))
    }))

    t = this.time(t, 'bounding')

    const cells = boundingRects.map(g => g.rect).concat(nodes.concat(edges));

    const newCells = _.filter(cells, cell => !this.graph.getCell(cell.id))
    const deletedCells = _.filter(this.graph.getCells(), oldCell => !_.find(cells, cell => cell.id === oldCell.id))
    const changedCells = _.filter(cells, cell => {
      const old = this.graph.getCell(cell.id)
      //TODO: some different ways of comparing?
      return old && JSON.stringify(old.get("definitionToCompare")) !== JSON.stringify(cell.get("definitionToCompare"))
    })

    t = this.time(t, 'compute')

    if (newCells.length + deletedCells.length + changedCells.length > 3) {
      this.graph.resetCells(cells);
    } else {
      this.graph.removeCells(deletedCells)
      this._updateChangedCells(changedCells);
      this.graph.addCells(newCells)
    }
    t = this.time(t, 'redraw')

    this._layout(layout);
    this.time(t, 'layout')

    _.forEach(boundingRects, rect => rect.rect.toBack())

    this.redrawing = false
  }

  _layout(layout) {
    if (_.isEmpty(layout)) {
      this.directedLayout()
    } else {
      _.forEach(layout, el => {
        const cell = this.graph.getCell(el.id)
        if (cell && JSON.stringify(cell.get('position')) !== JSON.stringify(el.position)) cell.set('position', el.position)
      });
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

    this.svgDimensions(oldWidth, oldHeight)

    //we have to set former width/height
    this.processGraphPaper.setDimensions(oldWidth, oldHeight)
  }

  //Hack for FOP to properly export image from svg xml
  svgDimensions = (width, height) => {
    let svg = this.getEspGraphRef().getElementsByTagName("svg")[0]
    svg.setAttribute('width', width)
    svg.setAttribute('height', height)
    this.setState({exported: SVGUtils.toXml(svg)})
  }

  highlightNodes = (data, nodeToDisplay, groupingState, selectionState) => {
    this.graph.getCells().forEach(cell => {
      this.unhighlightCell(cell, 'node-validation-error')
      this.unhighlightCell(cell, 'node-focused')
      this.unhighlightCell(cell, 'node-grouping')

    })

    _.keys((data.validationResult && data.validationResult.errors || {}).invalidNodes).forEach(name => {
      this.highlightNode(name, 'node-validation-error')
    });

    if (nodeToDisplay) {
      this.highlightNode(nodeToDisplay.id, 'node-focused')
    }

    (groupingState || []).forEach(id => this.highlightNode(id, 'node-grouping'));
    (selectionState || []).forEach(id => this.highlightNode(id, 'node-focused'));
  }

  highlightCell(cell, className) {
    this.processGraphPaper.findViewByModel(cell).highlight(null, {
      highlighter: {
        name: 'addClass',
        options: {className: className}
      }
    })
  }

  unhighlightCell(cell, className) {
    this.processGraphPaper.findViewByModel(cell).unhighlight(null, {
      highlighter: {
        name: 'addClass',
        options: {className: className}
      }
    })
  }

  highlightNode = (nodeId, highlightClass) => {
    const cell = this.graph.getCell(nodeId)
    if (cell) { //prevent `properties` node highlighting
      this.highlightCell(cell, highlightClass)
    }
  }

  changeLayoutIfNeeded = () => {
    let newLayout = this.graph.getElements().filter(el => !el.get('backgroundObject')).map(el => {
      const pos = el.get('position');
      return {id: el.id, position: pos}
    })

    if (!_.isEqual(this.props.layout, newLayout) && !this.props.readonly) {
      this.props.actions && this.props.actions.layoutChanged(newLayout)
    }
  }

  enablePanZoom() {
    const svgElement =  this.getEspGraphRef().getElementsByTagName("svg").item(0);

    const panAndZoom = svgPanZoom(svgElement, {
      viewportSelector: '.svg-pan-zoom_viewport',
      fit: this.props.processToDisplay.nodes.length > 1,
      zoomScaleSensitivity: 0.4,
      controlIconsEnabled: false,
      panEnabled: false,
      dblClickZoomEnabled: false,
      minZoom: 0.2,
      maxZoom: 10
    });

    this.processGraphPaper.on('blank:pointerdown', (evt, x, y) => {
      panAndZoom.enablePan();
    });

    this.processGraphPaper.on('cell:pointerup blank:pointerup', (cellView, event) => {
      panAndZoom.disablePan();
    });

    this.fitSmallAndLargeGraphs(panAndZoom)
    return panAndZoom
  }

  fitSmallAndLargeGraphs = (panAndZoom) => {
    const realZoom = panAndZoom.getSizes().realZoom
    const toZoomBy = realZoom > 1 ? 1 / realZoom : 0.90 //the bigger zoom, the further we get
    panAndZoom.zoomBy(toZoomBy)
  }

  changeNodeDetailsOnClick() {
    this.processGraphPaper.on('cell:pointerdblclick', (cellView, evt, x, y) => {
      const nodeData = cellView.model.attributes.nodeData;
      if (nodeData) {
        const prefixedNodeId = this.props.nodeIdPrefixForSubprocessTests + nodeData.id
        this.props.actions.displayModalNodeDetails({...nodeData, id: prefixedNodeId}, this.props.readonly)
      }

      if (cellView.model.attributes.edgeData) {
        this.props.actions.displayModalEdgeDetails(cellView.model.attributes.edgeData)
      }
    })

    if (this.props.singleClickNodeDetailsEnabled) {
      this.processGraphPaper.on('cell:pointerclick', (cellView, evt, x, y) => {

        const nodeData = cellView.model.attributes.nodeData
        if (!nodeData) {
          return
        }

        this.props.actions.displayNodeDetails(cellView.model.attributes.nodeData)

        if (evt.ctrlKey) {
          this.props.actions.expandSelection(nodeData.id)
        } else {
          this.props.actions.resetSelection(nodeData.id)
        }

        //TODO: is this the best place for this? if no, where should it be?
        const targetClass = _.get(evt, 'originalEvent.target.className.baseVal')
        if (targetClass.includes('collapseIcon') && nodeData) {
          this.props.actions.collapseGroup(nodeData.id)
        }

        if (targetClass.includes('expandIcon') && nodeData) {
          this.props.actions.expandGroup(nodeData.id)
        }
      })
    }

    this.processGraphPaper.on('blank:pointerdown', () => {
      if (this.props.fetchedProcessDetails != null) {
        this.props.actions.displayNodeDetails(this.props.fetchedProcessDetails.json.properties)
        this.props.actions.resetSelection()
      }
    })
  }

  hooverHandling() {
    this.processGraphPaper.on('cell:mouseover', (cellView) => {
      const model = cellView.model
      this.showLabelOnHover(model);
      this.showBackgroundIcon(model);
    });
    this.processGraphPaper.on('cell:mouseout', (cellView, evt) => {
      this.hideBackgroundIcon(cellView.model, evt);
    });
  }

  //needed for proper switch/filter label handling
  showLabelOnHover(model) {
    if (model.get && !model.get('backgroundObject')) {
      model.toFront();
    }
    return model;
  }

  //background is below normal node, we cannot use normal hover/mouseover/mouseout...
  showBackgroundIcon(model) {
    if (model.get && model.get('backgroundObject')) {
      const el = this.processGraphPaper.findViewByModel(model).vel
      el.addClass('nodeIconForceHoverBox')
      el.removeClass('nodeIconForceNoHoverBox')
    }
  }

  //background is below normal node, we cannot use normal hover/mouseover/mouseout...
  hideBackgroundIcon(model, evt) {
    if (model.get && model.get('backgroundObject')) {
      if (!this.checkIfCursorInRect(model, evt)) {
        const el = this.processGraphPaper.findViewByModel(model).vel
        el.removeClass('nodeIconForceHoverBox')
        el.addClass('nodeIconForceNoHoverBox')
      }

    }
  }

  checkIfCursorInRect(model, evt) {
    const relOffset = this.computeRelOffset({x: evt.clientX, y: evt.clientY})
    const position = model.attributes.position
    const size = model.attributes.size
    return relOffset.x >= position.x && relOffset.y >= position.y && relOffset.x <= position.x + size.width && relOffset.y <= position.y + size.height;
  }

  cursorBehaviour() {
    this.processGraphPaper.on('blank:pointerdown', (evt, x, y) => {
      if (this.getEspGraphRef()) {
        this.getEspGraphRef().style.cursor = "move"
      }
    })

    this.processGraphPaper.on('blank:pointerup', (evt, x, y) => {
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
      y: (pointerOffset.y - pan.y - graphPosition.top - paddingTop) / zoom
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
      <div id="graphContainer">
        {!_.isEmpty(this.props.nodeToDisplay) ? <NodeDetailsModal/> : null}
        {!_.isEmpty(this.props.edgeToDisplay) ? <EdgeDetailsModal/> : null}
        <div ref={this.espGraphRef} id={this.props.divId}></div>
      </div>
    )

    return this.props.connectDropTarget ? this.props.connectDropTarget(toRender) : toRender
  }
}

const spec = {
  drop: (props, monitor, component) => {
    const relOffset = component.computeRelOffset(monitor.getClientOffset())
    component.addNode(monitor.getItem(), relOffset)

  }
};

function mapState(state, props) {
  return {
    divId: "esp-graph",
    readonly: state.graphReducer.businessView,
    singleClickNodeDetailsEnabled: true,
    nodeIdPrefixForSubprocessTests: "",
    processToDisplay: state.graphReducer.processToDisplay,
    fetchedProcessDetails: state.graphReducer.fetchedProcessDetails,
    processCounts: state.graphReducer.processCounts || {},
    nodeToDisplay: state.graphReducer.nodeToDisplay,
    edgeToDisplay: state.graphReducer.edgeToDisplay,
    groupingState: state.graphReducer.groupingState,
    expandedGroups: state.ui.expandedGroups,
    layout: state.graphReducer.layout,
    ...commonState(state)
  };
}

function mapSubprocessState(state, props) {
  return {
    divId: "esp-graph-subprocess",
    readonly: true,
    singleClickNodeDetailsEnabled: false,
    nodeIdPrefixForSubprocessTests: state.graphReducer.nodeToDisplay.id + "-", //TODO where should it be?
    processToDisplay: props.processToDisplay,
    processCounts: props.processCounts,
    ...commonState(state)
  }
}

function commonState(state) {
  return {
    processCategory: state.graphReducer.fetchedProcessDetails.processCategory,
    loggedUser: state.settings.loggedUser,
    processDefinitionData: state.settings.processDefinitionData || {},
    selectionState: state.graphReducer.selectionState,
  }
}

export let BareGraph = connect(mapSubprocessState, ActionsUtils.mapDispatchWithEspActions)(Graph)

Graph = DropTarget("element", spec, (connect) => ({connectDropTarget: connect.dropTarget()}))(Graph)

//withRef is here so that parent can access methods in graph
Graph = connect(mapState, ActionsUtils.mapDispatchWithEspActions, null, {forwardRef: true})(Graph)

export default Graph;
