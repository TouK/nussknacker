import React from 'react'
import { render } from 'react-dom'
import joint from 'jointjs'
import EspNode from './EspNode'
import 'jointjs/dist/joint.css'
import _ from 'lodash'
import $ from 'jquery'
import svgPanZoom from 'svg-pan-zoom'

export default class Graph extends React.Component {

    constructor(props) {
        super(props);
        this.graph = new joint.dia.Graph();
        this.state = {
            clickedNode: {}
        };
    }

    componentDidMount() {
        var paper = this.drawGraph();
        this.enablePanZoom(paper);
        this.changeNodeDetailsOnClick(paper)
    }

    directedLayout = () => {
        joint.layout.DirectedGraph.layout(this.graph, {
            nodeSep: 200,
            edgeSep: 500,
            minLen: 300,
            rankDir: "TB"
        });
    }

    drawGraph = () => {
        var data = this.props.data
        var nodes = _.map(data.nodes, function (n) {
            return EspNode.makeElement(n)
        });
        var edges = _.map(data.edges, function (n) {
            return EspNode.makeLink(n.from, n.to, _.get(n, 'label.expr'))
        });
        var cells = nodes.concat(edges);
        var paper = new joint.dia.Paper({
            el: $('#esp-graph'),
            width: "100%",
            height: 600,
            gridSize: 1,
            model: this.graph
        });

        this.graph.resetCells(cells);
        this.directedLayout();
        return paper;
    }

    enablePanZoom(paper) {
        var graphElement = $('#esp-graph')[0]
        var panAndZoom = svgPanZoom(graphElement.childNodes[0],
            {
                viewportSelector: graphElement.childNodes[0].childNodes[0],
                fit: true,
                zoomScaleSensitivity: 0.4,
                controlIconsEnabled: true,
                panEnabled: false
            });

        paper.on('blank:pointerdown', function (evt, x, y) {
            panAndZoom.enablePan();
        });

        paper.on('cell:pointerup blank:pointerup', function (cellView, event) {
            panAndZoom.disablePan();
        });
    }

    changeNodeDetailsOnClick (paper) {
        paper.on('cell:pointerclick',
            (cellView, evt, x, y) => {
                this.setState({clickedNode: cellView.model.attributes.nodeData});
            }
        );
    }

    render() {
        return (
            <div>
                <div id="esp-graph" ref="placeholder"></div>
                <NodeDetails node={this.state.clickedNode}/>
                <button type="button" className="btn btn-default" onClick={this.directedLayout}>Wyśrodkuj proces</button>
            </div>
        );
    }
}


class NodeDetails extends React.Component {
    render() {
        return (
            <div>
                <pre>{JSON.stringify(this.props.node, null, 2)}</pre>
            </div>
        );
    }

}