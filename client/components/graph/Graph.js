import React from 'react'
import { render, findDOMNode } from 'react-dom'
import joint from 'jointjs'
import EspNode from './EspNode'
import 'jointjs/dist/joint.css'
import _ from 'lodash'
import svgPanZoom from 'svg-pan-zoom'
import $ from 'jquery'
import classNames from 'classnames';
import { connect } from 'react-redux';
import { bindActionCreators } from 'redux';
import * as EspActions from '../../actions/actions';
import NodeDetailsModal from './nodeDetailsModal.js';
import { DropTarget } from 'react-dnd';
import '../../stylesheets/graph.styl'


class Graph extends React.Component {

    static propTypes = {
        processToDisplay: React.PropTypes.object.isRequired,
        loggedUser: React.PropTypes.object.isRequired,
        connectDropTarget: React.PropTypes.func.isRequired
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
        //dodajemy w inny sposob...
        /*  .on("add", (e, f) => {
            console.log("created", e, f)
            if (e.isElement()) {
              setTimeout(() => {
                this.props.actions.nodeAdded(e, e.get('position'));
              }, 100);
            }
        })*/
        this.state = {
            toolboxVisible: false
        };
    }

    addNode(node, position) {
      this.props.actions.nodeAdded(node, position);
    }

    componentDidMount() {
        this.processGraphPaper = this.createPaper()
        this.drawGraph(this.props.processToDisplay)
        this.panAndZoom = this.enablePanZoom();
        this.changeNodeDetailsOnClick(this.processGraphPaper);
        this.labelToFrontOnHover(this.processGraphPaper);
        this.cursorBehaviour(this.processGraphPaper);
    }

    componentWillUpdate(nextProps, nextState) {
        if (!_.isEqual(this.props.processToDisplay, nextProps.processToDisplay) || !_.isEqual(this.props.layout, nextProps.layout)) {
            this.drawGraph(nextProps.processToDisplay, nextProps.layout)
        }
    }

    directedLayout() {
        joint.layout.DirectedGraph.layout(this.graph, {
            nodeSep: 200,
            edgeSep: 500,
            rankSep: 100,
            minLen: 300,
            rankDir: "TB"
        });
        this.changeLayoutIfNeeded()
    }

    createPaper = () => {
        const canWrite = this.props.loggedUser.canWrite
        return new joint.dia.Paper({
            el: $('#esp-graph'),
            gridSize: 1,
            height: $('#esp-graph').height(),
            width: $('#esp-graph').width(),
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
            defaultLink: EspNode.makeLink({})

        })
          .on("cell:pointerup", (c, e) => {
            this.changeLayoutIfNeeded()
          })
          .on("link:connect", (c) => this.props.actions.nodesConnected(c.sourceView.model.id, c.targetView.model.id))
    }

    drawGraph = (data, layout) => {
        var nodes = _.map(data.nodes, (n) => { return EspNode.makeElement(n) });
        var edges = _.map(data.edges, (e) => { return EspNode.makeLink(e) });
        var cells = nodes.concat(edges);
        this.graph.resetCells(cells);

        _.keys(data.validationResult.invalidNodes)
          .forEach(name => {
              const cell = this.graph.getCell(name) // na razie robimy tak, bo nie wiemy jak pokazac blad np w propertiesach procesu, bo nie pokazujemy propertiesow jako zwykly node
              if (cell) {
                  this.processGraphPaper.findViewByModel(cell).highlight(null, {
                      highlighter: {
                          name: 'addClass',
                          options: {
                              className: 'node-validation-error'
                          }
                      }
                  })
              }
          });
        if (!layout) {
          this.directedLayout()
        } else {
          _.map(layout, el => this.graph.getCell(el.id).set('position', el.position));
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
      this.processGraphPaper.on('blank:pointerdown', function(evt, x, y) {
        $('#esp-graph').css('cursor', 'move')
      })
      this.processGraphPaper.on('blank:pointerup', function(evt, x, y) {
        $('#esp-graph').css('cursor', 'auto')
      })
    }

    toggleToolbox = () => {
        this.setState({toolboxVisible: !this.state.toolboxVisible})
    }

    render() {
        var displayedEdges = this.graph.attributes.cells.models.filter(m => m.attributes.source).map(l => l.attributes.edgeData)
        var displayedNodes = this.graph.attributes.cells.models.filter(m => !m.attributes.source).map(n => n.attributes.nodeData)
        return this.props.connectDropTarget(
            <div>
                <h2 id="process-name">{this.props.processToDisplay.id}</h2>
                {!_.isEmpty(this.props.nodeToDisplay) ? <NodeDetailsModal/> : null }
                {this.processGraphPaper && this.panAndZoom && this.state.toolboxVisible ?
                    <Toolbox processGraphPaper={this.processGraphPaper} panAndZoom={this.panAndZoom} graph={this.graph}/> : null
                }
                <div ref="espGraph" id="esp-graph"></div>
                <button type="button" className="btn btn-default hidden" onClick={this.directedLayout}>Directed layout</button>
            </div>
        );

    }
}

function mapState(state) {
    return {
        nodeToDisplay: state.espReducer.nodeToDisplay,
        processToDisplay: state.espReducer.processToDisplay,
        loggedUser: state.espReducer.loggedUser,
        layout: state.espReducer.layout
    };
}

function mapDispatch(dispatch) {
    return {
        actions: bindActionCreators(EspActions, dispatch)
    };
}

var spec = {
  drop: (props, monitor, component) => {
    const pan = component.panAndZoom.getPan()
    const sizes = component.panAndZoom.getSizes()
    var pointerOffset = monitor.getClientOffset()
    var rect = findDOMNode(component).getBoundingClientRect();
    var zoom = component.panAndZoom.getSizes().realZoom
    //czegos tu chyba jeszcze brakuje... ale nie wiem czego :|
    var relOffset = { x: (pointerOffset.x - rect.left - pan.x)/zoom, y : (pointerOffset.y - rect.top - pan.y)/zoom }
    console.log(relOffset)

    component.addNode(monitor.getItem(), relOffset)

  }
};

//withRef jest po to, zeby parent mogl sie dostac
export default connect(mapState, mapDispatch, null, {withRef: true})(DropTarget("element", spec, (connect, monitor) => ({
  connectDropTarget: connect.dropTarget()
}))(Graph));

