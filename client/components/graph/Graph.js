import React from 'react'
import { render } from 'react-dom'
import joint from 'jointjs'
import EspNode from './EspNode'
import 'jointjs/dist/joint.css'
import _ from 'lodash'
import svgPanZoom from 'svg-pan-zoom'
import $ from 'jquery'
import classNames from 'classnames';
import NodeDetailsModal from './nodeDetailsModal.js';

import '../../stylesheets/graph.styl'

export default class Graph extends React.Component {

    constructor(props) {
        super(props);
        this.graph = new joint.dia.Graph();
        this.state = {
            clickedNode: {},
            toolboxVisible: false
        };
    }

    componentDidMount() {
        this.processGraphPaper = this.drawProcessGraph()
        this.panAndZoom = this.enablePanZoom();
        this.changeNodeDetailsOnClick(this.processGraphPaper);
        this.forceUpdate()
    }

    directedLayout = () => {
        joint.layout.DirectedGraph.layout(this.graph, {
            nodeSep: 200,
            edgeSep: 500,
            rankSep: 100,
            minLen: 300,
            rankDir: "TB"
        });
    }

    drawProcessGraph = () => {
        var nodes = _.map(this.props.data.nodes, (n) => { return EspNode.makeElement(n) });
        var edges = _.map(this.props.data.edges, (e) => { return EspNode.makeLink(e) });
        var cells = nodes.concat(edges);
        var paper = new joint.dia.Paper({
            el: $('#esp-graph'),
            gridSize: 1,
            height: $('#esp-graph').height(),
            width: $('#esp-graph').width(),
            model: this.graph,
            snapLinks: { radius: 75 },
            interactive: false //remove when editing won't be such a pain
        });

        this.graph.resetCells(cells);
        this.directedLayout();
        return paper;
    }

    enablePanZoom() {
        var panAndZoom = svgPanZoom(this.refs.espGraph.childNodes[0],
            {
                viewportSelector: this.refs.espGraph.childNodes[0].childNodes[0],
                fit: true,
                zoomScaleSensitivity: 0.4,
                controlIconsEnabled: true,
                panEnabled: false
            });
        this.processGraphPaper.on('blank:pointerdown', (evt, x, y) => {
            panAndZoom.enablePan();
        });
        this.processGraphPaper.on('cell:pointerup blank:pointerup', (cellView, event) => {
            panAndZoom.disablePan();
        });
        return panAndZoom
    }

    changeNodeDetailsOnClick () {
        this.processGraphPaper.on('cell:pointerclick', (cellView, evt, x, y) => {
            if (cellView.model.attributes.nodeData) {
                this.setState({clickedNode: cellView.model.attributes.nodeData});
            }
        });
    }

    onDetailsClosed = () => {
        this.setState({clickedNode: {}})
    }

    toggleToolbox = () => {
        this.setState({toolboxVisible: !this.state.toolboxVisible})
    }

    render() {
        var displayedEdges = this.graph.attributes.cells.models.filter(m => m.attributes.source).map(l => l.attributes.edgeData)
        var displayedNodes = this.graph.attributes.cells.models.filter(m => !m.attributes.source).map(n => n.attributes.nodeData)
        return (
            <div>
                <h2 id="process-name">{this.props.data.metaData.id}</h2>
                <NodeDetailsModal node={this.state.clickedNode} onClose={this.onDetailsClosed}/>
                {this.processGraphPaper && this.panAndZoom && this.state.toolboxVisible ?
                    <Toolbox processGraphPaper={this.processGraphPaper} panAndZoom={this.panAndZoom} graph={this.graph}/> : null
                }
                <div ref="espGraph" id="esp-graph"></div>
                <button type="button" className="btn btn-default hidden" onClick={this.directedLayout}>Directed layout</button>
            </div>
        );
    }
}

class Toolbox extends React.Component {

    constructor(props) {
        super(props);
        this.state = {
            dragAndDropPaper: {
                visible: false,
                left: null,
                top: null
            }
        };
    }

    componentDidMount() {
        this.drawToolbox(this.props.processGraphPaper, this.props.panAndZoom, this.props.graph);
    }

    drawToolbox(processGraphPaper, panAndZoom, graph) {
        var toolboxPaper = this.initializeToolboxGraph();
        toolboxPaper.on('cell:pointerdown', (cellView, e, x, y) => {
            var originalClickOffset = { x: x - cellView.model.position().x, y: y - cellView.model.position().y};
            this.setState({
                dragAndDropPaper: {
                    visible: true,
                    left: e.pageX - originalClickOffset.x - window.pageXOffset,
                    top: e.pageY - originalClickOffset.y - window.pageYOffset
                }
            })
            var flyShape = this.initializeFlyGraphWithDraggedElement(cellView);

            //todo: sprobowac usuac to jquery stad
            $('body').on('mousemove', (e) => {
                this.setState({
                    dragAndDropPaper: {
                        visible: true,
                        left: e.pageX - originalClickOffset.x - window.pageXOffset + this.refs.dragAndDropPaper.clientLeft,
                        top: e.pageY - originalClickOffset.y - window.pageYOffset + this.refs.dragAndDropPaper.clientTop
                    }})
            });
            $('body').on('mouseup', (e) => {
                var targetPaperOffset = processGraphPaper.$el.offset();
                if (this.isElementDroppedOnTheProcessGraph(e, targetPaperOffset, processGraphPaper)) {
                    var element = this.createDraggedElement(flyShape, panAndZoom, e, targetPaperOffset, originalClickOffset);
                    graph.addCell(element);
                }
                $('body').off('mousemove').off('mouseup');
                flyShape.remove();
                this.setState({dragAndDropPaper: {visible: false }});
            });
        });
    }

    initializeToolboxGraph() {
        var toolboxGraph = new joint.dia.Graph
        var toolboxPaper = new joint.dia.Paper({
            el: this.refs.toolbox,
            height: 60,
            model: toolboxGraph,
            interactive: false
        });

        toolboxGraph.addCells([EspNode.makeElement({type: 'Filter'}), EspNode.makeElement({type: 'Sink'})]);
        joint.layout.DirectedGraph.layout(toolboxGraph, {nodeSep: 200, edgeSep: 500, minLen: 300, rankDir: "TB"})
        return toolboxPaper;
    }

    initializeFlyGraphWithDraggedElement(cellView) {
        var flyGraph = new joint.dia.Graph
        var dragAndDropPaper = new joint.dia.Paper({
            el: this.refs.dragAndDropPaper,
            model: flyGraph,
            interactive: false
        })
        var flyShape = cellView.model.clone()
        flyShape.position(0, 0);
        flyGraph.addCell(flyShape);
        return flyShape;
    }

    createDraggedElement(flyShape, panAndZoom, e, targetPaperOffset, originalClickOffset) {
        var element = flyShape.clone();
        var pan = panAndZoom.getPan()
        var zoom = panAndZoom.getSizes().realZoom
        var resX = (e.pageX - targetPaperOffset.left - originalClickOffset.x - pan.x) / zoom;
        var resY = (e.pageY - targetPaperOffset.top - originalClickOffset.y - pan.y) / zoom;
        element.position(resX, resY);
        return element;
    }

    isElementDroppedOnTheProcessGraph = (e, targetPaperOffset, processGraphPaper) => {
        return e.pageX > targetPaperOffset.left &&
            e.pageX < targetPaperOffset.left + processGraphPaper.$el.width() &&
            e.pageY > targetPaperOffset.top &&
            e.pageY < targetPaperOffset.top + processGraphPaper.$el.height();
    }


    render() {
        var dragAndDropPaperStyles = {
            position: 'fixed', zIndex:100, opacity:.7, pointerEvent: 'none',
            top: this.state.dragAndDropPaper.top, left: this.state.dragAndDropPaper.left
        }

        return (
            <div>
                <div ref="toolbox" style={ {background: "#146DFF" }}></div>
                {this.state.dragAndDropPaper.visible ?
                    <div ref="dragAndDropPaper" id="dragAndDropPaper" style={dragAndDropPaperStyles}></div> : null }
            </div>
        );

    }
};
