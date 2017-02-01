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
import NodeDetailsModal from './nodeDetailsModal.js';
import { DropTarget } from 'react-dnd';
import '../../stylesheets/graph.styl'
import SVGUtils from '../../common/SVGUtils';
import NodeUtils from './NodeUtils.js'
import TestResultUtils from '../../common/TestResultUtils'


class Graph extends React.Component {

    static propTypes = {
        processToDisplay: React.PropTypes.object.isRequired,
        groupingState: React.PropTypes.array,
        loggedUser: React.PropTypes.object.isRequired,
        connectDropTarget: React.PropTypes.func.isRequired,
        testResults: React.PropTypes.object.isRequired
    }

    constructor(props) {
        super(props);
        this.graph = new joint.dia.Graph();
        this.graph
          .on("remove", (e, f) => {
            if (e.isLink) {
              this.props.actions.nodesDisconnected(e.attributes.source.id, e.attributes.target.id)
            }
        })
    }

    addNode(node, position) {
      this.props.actions.nodeAdded(node, position);
    }

    componentDidMount() {
        this.processGraphPaper = this.createPaper()
        this.drawGraph(this.props.processToDisplay, this.props.layout, this.props.testResults, true)
        this._prepareContentForExport()
        this.drawGraph(this.props.processToDisplay, this.props.layout, this.props.testResults)
        this.panAndZoom = this.enablePanZoom();
        this.changeNodeDetailsOnClick();
        this.labelToFrontOnHover();
        this.cursorBehaviour();
        this.highlightNodes(this.props.processToDisplay, this.props.nodeToDisplay);

    }

    componentWillUpdate(nextProps, nextState) {
      const processNotChanged = _.isEqual(this.props.processToDisplay, nextProps.processToDisplay) &&
        _.isEqual(this.props.layout, nextProps.layout) &&
        _.isEqual(this.props.testResults, nextProps.testResults) &&
        _.isEqual(this.props.groupingState, nextProps.groupingState)

      if (!processNotChanged) {
        this.drawGraph(nextProps.processToDisplay, nextProps.layout, nextProps.testResults)
      }
      //when e.g. layout changed we have to remember to highlight nodes
      if (!processNotChanged || !_.isEqual(this.props.nodeToDisplay, nextProps.nodeToDisplay)){
        this.highlightNodes(nextProps.processToDisplay, nextProps.nodeToDisplay, nextProps.groupingState);
      }
    }

    directedLayout() {
        joint.layout.DirectedGraph.layout(this.graph, {
            nodeSep: 0,
            edgeSep: 0,
            rankSep: -40,
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

    nodeInputs = (nodeId) => {
      return NodeUtils.edgesFromProcess(this.props.processToDisplay).filter(e => e.to == nodeId)
    }

    nodeOutputs = (nodeId) => {
      return NodeUtils.edgesFromProcess(this.props.processToDisplay).filter(e => e.from == nodeId)
    }

    isMultiOutput = (nodeId) => {
      var node = NodeUtils.nodesFromProcess(this.props.processToDisplay).find(n => n.id == nodeId)
      return node.type == "Split"
    }

    //we don't allow multi outputs other than split, and no multiple inputs
    validateConnection = (cellViewS, magnetS, cellViewT, magnetT) => {
      var from = cellViewS.model.id
      var to = cellViewT.model.id

      var targetHasNoInput = this.nodeInputs(to).length == 0
      var sourceHasNoOutput = this.nodeOutputs(from).length == 0
      var canLinkFromSource = sourceHasNoOutput || this.isMultiOutput(from)

      return magnetT && targetHasNoInput && canLinkFromSource;
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
                if (!canWrite) {
                  return false;
                } else if (cellView.model instanceof joint.dia.Link) {
                    // Disable the default vertex add functionality on pointerdown.
                    return { vertexAdd: false };
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
          .on("link:connect", (c) => this.props.actions.nodesConnected(c.sourceView.model.id, c.targetView.model.id))
    }

    drawGraph = (data, layout, testResults, forExport) => {
      const nodesWithGroups = NodeUtils.nodesFromProcess(data)
      const edgesWithGroups = NodeUtils.edgesFromProcess(data)

      const testSummary = (n) => TestResultUtils.nodeResultsSummary(testResults, n)

      const nodes = _.map(nodesWithGroups, (n) => { return EspNode.makeElement(n, testResults.nodeResults, testSummary(n))});
      const edges = _.map(edgesWithGroups, (e) => { return EspNode.makeLink(e, forExport) });
      const cells = nodes.concat(edges);
      this.graph.resetCells(cells);
      if (_.isEmpty(layout)) {
        this.directedLayout()
      } else {
        _.forEach(layout, el => {
          const cell = this.graph.getCell(el.id)
          if (cell) cell.set('position', el.position)
        });
      }
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
      _.keys(data.validationResult.invalidNodes).forEach(name => { this.highlightNode(name, 'node-validation-error') });
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
      var newLayout = _.map(this.graph.getElements(), (el) => {
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
      })
      this.processGraphPaper.on('cell:pointerclick', (cellView, evt, x, y) => {
        if (cellView.model.attributes.nodeData) {
          this.props.actions.displayNodeDetails(cellView.model.attributes.nodeData)
        }
      });
    }

    labelToFrontOnHover () {
        this.processGraphPaper.on('cell:mouseover', (cellView, evt, x, y) => {
          cellView.model.toFront();
        });
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

    render() {

        return this.props.connectDropTarget(
            <div>
                {!_.isEmpty(this.props.nodeToDisplay) ? <NodeDetailsModal/> : null }
                <div ref="espGraph" id="esp-graph"></div>
            </div>
        );

    }
}

function mapState(state) {
    return {
        nodeToDisplay: state.graphReducer.nodeToDisplay,
        groupingState: state.graphReducer.groupingState,
        processToDisplay: state.graphReducer.processToDisplay,
        loggedUser: state.settings.loggedUser,
        layout: state.graphReducer.layout,
        testResults: state.graphReducer.testResults || {}
    };
}

var spec = {
  drop: (props, monitor, component) => {
    const pan = component.panAndZoom ? component.panAndZoom.getPan() : {x: 0, y: 0}
    var zoom = component.panAndZoom ? component.panAndZoom.getSizes().realZoom : 1

    var pointerOffset = monitor.getClientOffset()
    var rect = findDOMNode(component).getBoundingClientRect();
    //czegos tu chyba jeszcze brakuje... ale nie wiem czego :|
    const graphPosition = $('#esp-graph svg').position()
    var relOffset = { x: (pointerOffset.x - rect.left - pan.x - graphPosition.left)/zoom, y : (pointerOffset.y - rect.top - pan.y - graphPosition.top)/zoom }
    component.addNode(monitor.getItem(), relOffset)

  }
};

//withRef jest po to, zeby parent mogl sie dostac
export default connect(mapState, ActionsUtils.mapDispatchWithEspActions, null, {withRef: true})(DropTarget("element", spec, (connect, monitor) => ({
  connectDropTarget: connect.dropTarget()
}))(Graph));

