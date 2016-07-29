import React from 'react'
import { render } from 'react-dom'
import joint from 'jointjs'
import EspNode from './EspNode'
import 'jointjs/dist/joint.css'
import _ from 'lodash'
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
        var nodes = _.map(this.props.data.nodes, (n) => { return EspNode.makeElement(n) });
        var edges = _.map(this.props.data.edges, (n) => { return EspNode.makeLink(n.from, n.to, _.get(n, 'label.original')) });
        var cells = nodes.concat(edges);
        var paper = new joint.dia.Paper({
            el: this.refs.espGraph,
            width: this.refs.espGraph.offsetWidth,
            gridSize: 1,
            model: this.graph
        });

        this.graph.resetCells(cells);
        this.directedLayout();
        return paper;
    }

    enablePanZoom(paper) {
        var panAndZoom = svgPanZoom(this.refs.espGraph.childNodes[0],
            {
                viewportSelector: this.refs.espGraph.childNodes[0].childNodes[0],
                fit: true,
                zoomScaleSensitivity: 0.4,
                controlIconsEnabled: true,
                panEnabled: false
            });

        paper.on('blank:pointerdown', (evt, x, y) => {
            panAndZoom.enablePan();
        });
        paper.on('cell:pointerup blank:pointerup', (cellView, event) => {
            panAndZoom.disablePan();
        });
    }

    changeNodeDetailsOnClick (paper) {
        paper.on('cell:pointerclick', (cellView, evt, x, y) => {
            if (cellView.model.attributes.nodeData) {
                this.setState({clickedNode: cellView.model.attributes.nodeData});
            }
        });
    }

    onDetailsClosed = () => {
        this.setState({clickedNode: {}})
    }

    render() {
        return (
            <div>
                <NodeDetailsModal node={this.state.clickedNode} onClose={this.onDetailsClosed}/>
                <div ref="espGraph"></div>
                <button type="button" className="btn btn-default" onClick={this.directedLayout}>Wyśrodkuj proces</button>
            </div>
        );
    }
}


import Modal from 'react-modal'
import Reactable from 'reactable'

class NodeDetailsModal extends React.Component {
    closeModal = () => {
        this.props.onClose()
    }

    contentForNode = () => {
        if (!_.isEmpty(this.props.node)) {
            switch (this.props.node.type) {
                case 'Filter':
                    return (
                        <div>
                            <Reactable.Table className="table" data={[this.props.node.expression]}/>
                            <NodeDetails node={this.props.node}/>
                        </div>
                    )
                case 'Enricher':
                    return (
                        <div>
                            <Reactable.Table className="table" data={_.map(this.props.node.service.parameters, (f) => {
                                return {'name': f.name, expr: f.expression.original}}  )
                            }/>
                            <NodeDetails node={this.props.node}/>
                        </div>
                    )
                case 'VariableBuilder':
                    return (
                        <div>
                            <Reactable.Table className="table" data={_.map(this.props.node.fields, (f) => { return {'name': f.name, expr: f.expression.original}}  )}/>
                            <NodeDetails node={this.props.node}/>
                        </div>
                    )
                default:
                    return (
                        <div>
                            <NodeDetails node={this.props.node}/>
                        </div>
                    )
            }
        }
    }

    render () {
        var isOpen = !(_.isEmpty(this.props.node))
        return (
            <div>
                <Modal isOpen={isOpen}>
                    <h1>{this.props.node.type}: {this.props.node.id}</h1>
                    {this.contentForNode()}
                    <button onClick={this.closeModal}>Close</button>
                </Modal>
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
