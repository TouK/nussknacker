import React from 'react'
import { render } from 'react-dom'
import joint from 'jointjs'
import EspNode from './EspNode'
import 'jointjs/dist/joint.css'
import _ from 'lodash'


export default class Graph extends React.Component {

    constructor(props) {
        super(props);
        this.graph = new joint.dia.Graph();
        this.directedLayout = () => {
            joint.layout.DirectedGraph.layout(this.graph, {
                nodeSep: 200,
                edgeSep: 500,
                minLen: 300,
                rankDir: "TB"
            });
        };
    }

    componentDidMount() {
        var data = this.props.data
        var nodes = _.map(data.nodes, function (n) {
            return EspNode.makeElement(n)
        });
        var edges = _.map(data.edges, function (n) {
            return EspNode.makeLink(n.idIn, n.idOut, n.label)
        });
        var cells = nodes.concat(edges);
        var paper = new joint.dia.Paper({
            el: this.refs.placeholder,
            width: 1000,
            height: 800,
            gridSize: 1,
            model: this.graph
        });

        this.graph.resetCells(cells);
        this.directedLayout(this.graph);

    }

    render() {
        return (
            <div ref="placeholder" >
                <button type="button" className="btn btn-default" onClick={this.directedLayout}>Wyśrodkuj proces</button>
            </div>

        );
    }
}
