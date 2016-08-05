import joint from 'jointjs'
import _ from 'lodash'

import markup from './markups/markup.html';

joint.shapes.devs.EspNode = joint.shapes.basic.Generic.extend(_.extend({}, joint.shapes.basic.PortsModelInterface, {

    markup: markup,
    portMarkup: '<g class="port port<%= id %>"><circle class="port-body"/><text class="port-label"/></g>',

    defaults: joint.util.deepSupplement({

        type: 'devs.Model',
        size: {width: 1, height: 1},

        inPorts: [],
        outPorts: [],

        attrs: {
            '.': {magnet: false},
            '.body': {
                width: 200, height: 100,
                stroke: '#616161',
                'stroke-width': 1
            },
            text: {
                fill: 'black',
                'pointer-events': 'none'
            },
            '.headerLabel': {
                text: 'Model',
                'font-size': 14,
                'font-weight': 400,
                'ref': '.blockHeader',
                'ref-x': 10, 'ref-y': .3
            },
            '.contentText': {
                text: 'Node Id',
                'font-size': 14,
                'font-weight': 400,
                'ref': '.blockContent',
                'ref-x': 10, 'ref-y': .2
            },
            // markups styling
            '.inPorts': {
                'ref-x': 0, 'ref-y': 0,
                'ref': '.body'
            },
            '.outPorts': {
                'ref-x': 0, 'ref-y': 0,
                'ref': '.body'
            },
            '.port-body': {
                r: 10,
                magnet: true,
                stroke: '#616161',
                'font-size': 10
            },
            '.inPorts .port-label': {
                y: 4, x: -4,
                'font-size': 10
            },
            '.outPorts .port-label': {
                y: 4, x: -9,
                'font-size': 10
            }
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

        var headerLabel = node.type;
        var bodyContent = node.id ? node.id : "";
        var maxLineLength = _.max(bodyContent.split('\n'), function (l) {
            return l.length;
        }).length;

        // Compute width/height of the rectangle based on the number
        // of lines in the label and the letter size. 0.6 * letterSize is
        // an approximation of the monospace font letter width.
        var letterSize = 14;
        var calculatedWidth = 2 * (letterSize * (0.5 * maxLineLength + 1));
        var minBlockWidth = 200;
        var width = _.max([minBlockWidth, calculatedWidth]);

        // var calculatedHeight = 3 * ((label.split('x').length + 1) * letterSize);
        var height = 100;
        var headerHeight = 30;

        var customAttrs = {
          'Source': {
            fill: '#A6D969'
          },
          'Sink': {
            fill: '#D94E1F'
          },
          'Filter': {
            fill: '#1395BA'
          },
          'Switch': {
            fill: '#EBC844'
          },
          'VariableBuilder': {
            fill: '#BEB9B5'
          },
          'Enricher': {
            fill: '#EBC844'
          },
          'Processor': {
            fill: '#FCF4D9'
          }
        };

        var attrs = {
          '.body': {
            width: width
          },
          'rect.blockHeader': {
            width: width,
            height: headerHeight,
            x: 0, y: 0,
            stroke: '#616161',
            'stroke-width': 1,
            fill: customAttrs[headerLabel].fill
          },
          '.headerLabel': {
            text: headerLabel,
            'font-weight': 600
          },
          'rect.blockContent': {
            width: width,
            height: (height - headerHeight),
            x: 0, y: headerHeight,
            stroke: '#616161',
            'stroke-width': 1,
            fill: '#fff'
          },
          '.contentText': {
            text: bodyContent,
            'font-weight': 400
          },
          '.inPorts circle': {
            fill: '#16A085',
            magnet: 'passive',
            type: 'input'
          },
          '.outPorts circle': {
            fill: '#E74C3C',
            type: 'output'
          }
        };

        // if (node.type == 'Source') {
        //     attrs['rect.blockHeader'].fill = '#A6D969';
        // } else if (node.type == 'Sink') {
        //     attrs['rect.blockHeader'].fill = '#D94E1F';
        // } else if (node.type == 'Filter') {
        //     attrs['rect.blockHeader'].fill = '#1395BA';
        // } else if (node.type == 'Switch') {
        //     attrs['rect.blockHeader'].fill = '#EBC844';
        // } else if (node.type == 'VariableBuilder') {
        //     attrs['rect.blockHeader'].fill = '#BEB9B5';
        // } else if (node.type == 'Enricher') {
        //     attrs['rect.blockHeader'].fill = '#EBC844';
        // } else if (node.type == 'Processor') {
        //     attrs['rect.blockHeader'].fill = '#FCF4D9';
        // }
        // else {
        //     console.warn("unknown node type found: " + node.type)
        //     attrs.rect.fill = 'black';
        // }

        var inPorts = [];
        var outPorts = [];
        if (node.type == 'Sink') {
            inPorts = ['In']
        } else if (node.type == 'Source') {
            outPorts = ['Out']
        } else {
            inPorts = ['In'];
            outPorts = ['Out']
        }

        return new joint.shapes.devs.EspNode({
            id: node.id,
            size: {width: width, height: height},
            inPorts: inPorts,
            outPorts: outPorts,
            attrs: attrs,
            rankDir: 'R',
            nodeData: node
        });
    },

    makeLink(edge) {
        return new joint.dia.Link({
            source: {id: edge.from, port: 'Out'},
            target: {id: edge.to, port: 'In'},
            labels: [{position: 0.5, attrs: {text: {text: _.get(edge, 'label.original') || '', 'font-weight': 'bold'}}}],
            attrs: {

                '.tool-options': {display: 'none'},
                //'.marker-target': {d: 'M 4 0 L 0 2 L 4 4 z', fill: '#7c68fc', stroke: '#7c68fc'},
                '.connection': {stroke: '#7c68fc'},
                minLen: 5
            },
            edgeData: edge
        });
    }

}
