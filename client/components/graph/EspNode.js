import joint from 'jointjs'
import _ from 'lodash'

joint.shapes.devs.EspNode = joint.shapes.basic.Generic.extend(_.extend({}, joint.shapes.basic.PortsModelInterface, {

    markup: '<g class="rotatable"><g class="scalable"><rect class="body"/></g><text class="label"/><g class="inPorts"/><g class="outPorts"/></g>',
    portMarkup: '<g class="port port<%= id %>"><circle class="port-body"/><text class="port-label"/></g>',

    defaults: joint.util.deepSupplement({

        type: 'devs.Model',
        size: {width: 1, height: 1},

        inPorts: [],
        outPorts: [],

        attrs: {
            '.': {magnet: false},
            '.body': {
                width: 150, height: 250,
                stroke: 'black'
            },
            '.port-body': {
                r: 10,
                magnet: true,
                stroke: 'black'
            },
            text: {
                fill: 'black',
                'pointer-events': 'none'
            },
            '.label': {text: 'Model', 'ref-x': 10, 'ref-y': .2, 'ref': '.body'},

            // CHANGED: find better positions for port labels
            '.inPorts .port-label': {dy: -30, x: 4},
            '.outPorts .port-label': {dy: 15, x: 4}
            //
        }

    }, joint.shapes.basic.Generic.prototype.defaults),

    getPortAttrs: function (portName, index, total, selector, type) {

        var attrs = {};

        var portClass = 'port' + index;
        var portSelector = selector + '>.' + portClass;
        var portLabelSelector = portSelector + '>.port-label';
        var portBodySelector = portSelector + '>.port-body';

        attrs[portLabelSelector] = {text: portName};
        attrs[portBodySelector] = {port: {id: portName || _.uniqueId(type), type: type}};

        // CHANGED: swap x and y ports coordinates ('ref-y' => 'ref-x')
        attrs[portSelector] = {ref: '.body', 'ref-x': (index + 0.5) * (1 / total)};
        // ('ref-dx' => 'ref-dy')
        if (selector === '.outPorts') {
            attrs[portSelector]['ref-dy'] = 0;
        }
        //

        return attrs;
    }
}));


export default {

    makeElement(node) {

        var maxLineLength = _.max(node.label.split('\n'), function (l) {
            return l.length;
        }).length;

        // Compute width/height of the rectangle based on the number
        // of lines in the label and the letter size. 0.6 * letterSize is
        // an approximation of the monospace font letter width.
        var letterSize = 8;
        var width = 2 * (letterSize * (0.6 * maxLineLength + 1));
        var height = 3 * ((node.label.split('x').length + 1) * letterSize);

        var inPorts = [];
        var outPorts = [];
        if (node.type == 'End') {
            inPorts = ['In']
        } else if (node.type == 'Start') {
            outPorts = ['Out']
        } else {
            inPorts = ['In'];
            outPorts = ['Out']
        }

        var attrs = {
            '.label': {text: node.label},
            text: {text: node.label, 'font-size': letterSize, 'font-family': 'monospace'},
            rect: {
                width: width, height: height,
                rx: 5, ry: 5,
                stroke: '#fe854f',
                //ondblclick: '$("#builderModal").modal()',
                'stroke-width': 2,
                fill: '#feb663'
            },
            '.inPorts circle': {fill: '#16A085', magnet: 'passive', type: 'input'},
            '.outPorts circle': {fill: '#E74C3C', type: 'output'}
        };

        if (node.shape == 'circle') {
            attrs.rect.fill = 'green';

        }
        if (node.shape == 'diamond') {
            attrs.rect.fill = 'blue';

        }

        return new joint.shapes.devs.EspNode({
            id: node.id,
            size: {width: width, height: height},
            inPorts: inPorts,
            outPorts: outPorts,
            attrs: attrs,
            rankDir: 'R'
        });
    },

    makeLink(parentElementLabel, childElementLabel, edgeLabel) {

        return new joint.dia.Link({
            source: {id: parentElementLabel, port: 'Out'},
            target: {id: childElementLabel, port: 'In'},
            labels: [{position: 0.5, attrs: {text: {text: edgeLabel || '', 'font-weight': 'bold'}}}],
            attrs: {

                '.tool-options': {display: 'none'},
                //'.marker-target': {d: 'M 4 0 L 0 2 L 4 4 z', fill: '#7c68fc', stroke: '#7c68fc'},
                '.connection': {stroke: '#7c68fc'},
                minLen: 5
            }
        });
    }

}

