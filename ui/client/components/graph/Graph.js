import React from 'react'
import { render, findDOMNode } from 'react-dom'
import joint from 'jointjs'
import EspNode from './EspNode'
import 'jointjs/dist/joint.css'
import _ from 'lodash'
import $ from 'jquery'
import svgPanZoom from 'svg-pan-zoom'
import { connect } from 'react-redux';
import { bindActionCreators } from 'redux';
import ActionsUtils from '../../actions/ActionsUtils';
import NodeDetailsModal from './nodeDetailsModal';
import EdgeDetailsModal from './EdgeDetailsModal';
import { DropTarget } from 'react-dnd';
import '../../stylesheets/graph.styl'
import SVGUtils from '../../common/SVGUtils';
import NodeUtils from './NodeUtils.js'
import cssVariables from "../../stylesheets/_variables.styl"


class Graph extends React.Component {

    redrawing = false

    static propTypes = {
        processToDisplay: React.PropTypes.object.isRequired,
        groupingState: React.PropTypes.array,
        loggedUser: React.PropTypes.object.isRequired,
        connectDropTarget: React.PropTypes.func.isRequired
    }

    constructor(props) {
        super(props);
        this.graph = new joint.dia.Graph();
        this.graph
          .on("remove", (e, f) => {
            if (e.isLink && !this.redrawing) {
              this.props.actions.nodesDisconnected(e.attributes.source.id, e.attributes.target.id)
            }
        })
    }

    addNode(node, position) {
      this.props.actions.nodeAdded(node, position);
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

    }

    componentWillUpdate(nextProps, nextState) {
      const processNotChanged = _.isEqual(this.props.processToDisplay, nextProps.processToDisplay) &&
        _.isEqual(this.props.layout, nextProps.layout) &&
        _.isEqual(this.props.processCounts, nextProps.processCounts) &&
        _.isEqual(this.props.groupingState, nextProps.groupingState) &&
        _.isEqual(this.props.expandedGroups, nextProps.expandedGroups)

      if (!processNotChanged) {
        this.drawGraph(nextProps.processToDisplay, nextProps.layout, nextProps.processCounts, false, nextProps.expandedGroups)
      }
      //when e.g. layout changed we have to remember to highlight nodes
      if (!processNotChanged || !_.isEqual(this.props.nodeToDisplay, nextProps.nodeToDisplay)){
        this.highlightNodes(nextProps.processToDisplay, nextProps.nodeToDisplay, nextProps.groupingState);
      }
    }

    directedLayout() {
      joint.layout.DirectedGraph.layout(this.graph.getCells().filter(cell => !cell.get('backgroundObject')), {
          nodeSep: 0,
          edgeSep: 0,
          rankSep: -20,
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
      var from = cellViewS.model.id
      var to = cellViewT.model.id
      return magnetT && NodeUtils.canMakeLink(from, to, this.props.processToDisplay, this.props.processDefinitionData);
    }

    createPaper = () => {
        const canWrite = this.props.loggedUser.canWrite
        return new joint.dia.Paper({
            el: this.refs.espGraph,
            gridSize: 1,
            height: this.refs.espGraph.offsetHeight,
            width: this.refs.espGraph.offsetWidth,
            model: this.graph,
            snapLinks: { radius: 75 },
            interactive: function(cellView) {
                const model = cellView.model
                if (!canWrite) {
                  return false;
                } else if (model instanceof joint.dia.Link) {
                    // Disable the default vertex add functionality on pointerdown.
                    return { vertexAdd: false };
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
          .on("cell:pointerup", (c, e) => {
            this.changeLayoutIfNeeded()
          })
          .on("link:connect", (c) => {
            this.props.actions.nodesConnected(
              c.sourceView.model.attributes.nodeData,
              c.targetView.model.attributes.nodeData,
              this.props.processDefinitionData
            )
          })
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
      var t = performance.now();

      const nodesWithGroups = NodeUtils.nodesFromProcess(process, expandedGroups)
      const edgesWithGroups = NodeUtils.edgesFromProcess(process, expandedGroups)
      const outgoingEdgesGrouped = _.groupBy(edgesWithGroups, "from")
      t = this.time(t, 'start')

      const nodes = _.map(nodesWithGroups, (n) => { return EspNode.makeElement(n, processCounts[n.id], forExport) });
      t = this.time(t, 'nodes')

      const edges = _.map(edgesWithGroups, (e) => { return EspNode.makeLink(e, outgoingEdgesGrouped, forExport) });
      t = this.time(t, 'links')

      const boundingRects = NodeUtils.getExpandedGroups(process, expandedGroups)
        .map(expandedGroup => ({group: expandedGroup,
          
          rect: EspNode.boundingRect(nodes, expandedGroup, layout,
          NodeUtils.createGroupNode(nodesWithGroups, expandedGroup))}))
      t = this.time(t, 'bounding')

      const cells = boundingRects.map(g => g.rect).concat(nodes.concat(edges));

      const newCells = _.filter(cells, cell => !this.graph.getCell(cell.id))
      const deletedCells = _.filter(this.graph.getCells(), oldCell => !_.find(cells, cell => cell.id === oldCell.id))
      const changedCells = _.filter(cells, cell => {
        const old = this.graph.getCell(cell.id)
        //TODO: some different ways of comparing?
        return old &&  JSON.stringify(old.get("definitionToCompare")) !== JSON.stringify(cell.get("definitionToCompare"))
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
      const oldHeight = this.refs.espGraph.offsetHeight
      const oldWidth = this.refs.espGraph.offsetWidth
      //we fit to content to be able to export svg nicely...
      this.processGraphPaper.fitToContent()
      this.setState({exported: SVGUtils.toXml(this.refs.espGraph.childNodes[0])})
      //we have to set former width/height
      this.processGraphPaper.setDimensions(oldWidth, oldHeight)
    }

    highlightNodes = (data, nodeToDisplay, groupingState) => {
      this.graph.getCells().forEach(cell => {
        this.unhighlightCell(cell, 'node-validation-error')
        this.unhighlightCell(cell, 'node-focused')
        this.unhighlightCell(cell, 'node-grouping')

      })
      _.keys((data.validationResult.errors || {}).invalidNodes).forEach(name => { this.highlightNode(name, 'node-validation-error') });
      if (nodeToDisplay) {
        this.highlightNode(nodeToDisplay.id, 'node-focused')
      }
      (groupingState || []).forEach(id => this.highlightNode(id, 'node-grouping'))
    }

    highlightCell(cell, className) {
      this.processGraphPaper.findViewByModel(cell).highlight(null, { highlighter: { name: 'addClass', options: { className: className}} })
    }

    unhighlightCell(cell, className) {
      this.processGraphPaper.findViewByModel(cell).unhighlight(null, { highlighter: {name: 'addClass', options: { className: className}} })
    }

    highlightNode = (nodeId, highlightClass) => {
      const cell = this.graph.getCell(nodeId)
      if (cell) { //zabezpieczenie przed dostaniem sie do node'a propertiesow procesu, ktorego to nie pokazujemy na grafie
        this.highlightCell(cell, highlightClass)
      }
    }

  changeLayoutIfNeeded = () => {
      var newLayout = this.graph.getElements().filter(el => !el.get('backgroundObject'))
        .map(el => {
          var pos = el.get('position');
          return { id: el.id, position: pos }
      })
      if (!_.isEqual(this.props.layout, newLayout)) {
        this.props.actions.layoutChanged(newLayout)
      }
    }

    enablePanZoom() {
      var panAndZoom = svgPanZoom(this.refs.espGraph.childNodes[0],
        {
          viewportSelector: this.refs.espGraph.childNodes[0].childNodes[0],
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
    const toZoomBy = realZoom > 1 ? 1 / realZoom : 0.90 //jak jest za duze powiekszenie to oddalamy bardziej
    panAndZoom.zoomBy(toZoomBy)
  }

    changeNodeDetailsOnClick () {
      this.processGraphPaper.on('cell:pointerdblclick', (cellView, evt, x, y) => {
        if (cellView.model.attributes.nodeData) {
          this.props.actions.displayModalNodeDetails(cellView.model.attributes.nodeData)
        }
        if (cellView.model.attributes.edgeData) {
          this.props.actions.displayModalEdgeDetails(cellView.model.attributes.edgeData)
        }
      })
      this.processGraphPaper.on('cell:pointerclick', (cellView, evt, x, y) => {
        const nodeData = cellView.model.attributes.nodeData
        if (nodeData) {
          this.props.actions.displayNodeDetails(cellView.model.attributes.nodeData)
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

    hooverHandling () {
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
      return relOffset.x >= position.x && relOffset.y >= position.y &&
                    relOffset.x <= position.x + size.width && relOffset.y <= position.y + size.height;
    }

    // FIXME - w chrome 52.0.2743.82 (64-bit) nie działa na esp-graph
    // Trzeba sprawdzić na innych wersjach Chrome u innych, bo może to być kwestia tylko tej wersji chrome
    cursorBehaviour () {
      this.processGraphPaper.on('blank:pointerdown', (evt, x, y) => {
        if (this.refs.espGraph) {
          this.refs.espGraph.style.cursor = "move"
        }
      })
      this.processGraphPaper.on('blank:pointerup', (evt, x, y) => {
        if (this.refs.espGraph) {
          this.refs.espGraph.style.cursor = "auto"
        }
      })
    }

    computeRelOffset(pointerOffset) {
      const pan = this.panAndZoom ? this.panAndZoom.getPan() : {x: 0, y: 0}
      const zoom = this.panAndZoom ? this.panAndZoom.getSizes().realZoom : 1

      //TODO: is it REALLY ok?
      const paddingLeft = cssVariables.svgGraphPaddingLeft
      const paddingTop = cssVariables.svgGraphPaddingTop

      const graphPosition = $('#esp-graph svg').position()
      return { x: (pointerOffset.x - pan.x - graphPosition.left - paddingLeft)/zoom, y : (pointerOffset.y - pan.y - graphPosition.top - paddingTop)/zoom }
    }

    render() {

        return this.props.connectDropTarget(
            <div>
                {!_.isEmpty(this.props.nodeToDisplay) ? <NodeDetailsModal/> : null }
                {!_.isEmpty(this.props.edgeToDisplay) ? <EdgeDetailsModal/> : null }
                <div ref="espGraph" id="esp-graph"></div>
            </div>
        );

    }
}

function mapState(state) {
    return {
        nodeToDisplay: state.graphReducer.nodeToDisplay,
        edgeToDisplay: state.graphReducer.edgeToDisplay,
        groupingState: state.graphReducer.groupingState,
        processToDisplay: state.graphReducer.processToDisplay,
        loggedUser: state.settings.loggedUser,
        layout: state.graphReducer.layout,
        processCounts: state.graphReducer.processCounts || {},
        processDefinitionData: state.settings.processDefinitionData,
        expandedGroups: state.ui.expandedGroups
    };
}

var spec = {
  drop: (props, monitor, component) => {
    var relOffset = component.computeRelOffset(monitor.getClientOffset())
    component.addNode(monitor.getItem(), relOffset)

  }
};

//withRef jest po to, zeby parent mogl sie dostac
export default connect(mapState, ActionsUtils.mapDispatchWithEspActions, null, {withRef: true})(DropTarget("element", spec, (connect, monitor) => ({
  connectDropTarget: connect.dropTarget()
}))(Graph));

