import joint from 'jointjs'
import _ from 'lodash'

import markup from './markups/markup.html';

joint.shapes.devs.EspNode = joint.shapes.basic.Generic.extend(_.extend({}, joint.shapes.basic.PortsModelInterface, {

    markup: markup,
    portMarkup: '<g class="port port<%= id %>"><circle class="port-body"/></g>',

    defaults: joint.util.deepSupplement({

        type: 'devs.Model',
        size: {width: 1, height: 1},

        inPorts: [],
        outPorts: [],

        attrs: {
            '.': {magnet: false},
            '.body': {
                width: 300, height: 130,
                stroke: '#B5B5B5',
                'stroke-width': 1,
                rx: 10
            },
            text: {
                fill: '#1E1E1E',
                'pointer-events': 'none',
                'font-weight': 400
            },
            'rect.blockHeader': {
              x: 0, y: 0,
              rx: 10,
              'stroke-width': 0
            },
            '.headerLabel': {
                text: 'Model',
                'font-size': 14,
                'font-weight': 600,
                'ref': '.blockHeader',
                'ref-x': 10, 'ref-y': .3
            },
            '.contentText': {
                'font-size': 14,
                'ref': '.blockHeader',
                'ref-x': 20, 'ref-y': 50
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
                r: 5,
                magnet: true,
                'font-size': 10
            },
            '.inPorts circle': {
              fill: '#73E5B7',
              magnet: 'passive',
              type: 'input'
            },
            '.outPorts circle': {
              fill: '#F27980',
              type: 'output'
            }
        }

    }, joint.shapes.basic.Generic.prototype.defaults),

    getPortAttrs: function (portName, index, total, selector, type) {

        var attrs = {};

        var portClass = 'port' + index;
        var portSelector = selector + '>.' + portClass;
        var portBodySelector = portSelector + '>.port-body';

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
        var customAttrs = require('json!../../assets/json/nodeAttributes.json');

        var headerLabel = _.toUpper(customAttrs[node.type].name);
        var bodyContent = node.id ? node.id : "";

        // Compute width/height of the rectangle based on the number
        // of lines in the label and the letter size. 0.6 * letterSize is
        // an approximation of the monospace font letter width.
        var maxLineLength = _.max(bodyContent.split('\n'), function (l) {
            return l.length;
        }).length;
        var letterSize = 14;
        var calculatedWidth = 2 * (letterSize * (0.5 * maxLineLength + 1));
        var minBlockWidth = 300;
        var width = _.max([minBlockWidth, calculatedWidth]);
        var height = 150;

        // header styles
        var headerHeight = 35;
        var headerLength =  letterSize * (0.5 * headerLabel.length);
        var headerLabelPosX = (width / 2) - ((headerLength / 2) + 5);
        var headerFillerHeight = headerHeight / 2;

        var attrs = {
          '.body': {
            width: width
          },
          'rect.headerFiller': {
            x: 0, y: headerFillerHeight,
            height: headerFillerHeight,
            width: width,
            fill: customAttrs[node.type].styles.fill
          },
          'rect.blockHeader': {
            width: width,
            height: headerHeight,
            fill: customAttrs[node.type].styles.fill
          },
          '.headerLabel': {
            text: headerLabel,
            'ref-x': headerLabelPosX
          },
          '.contentText': {
            text: bodyContent
          }
        };

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
        markup: [
            '<path class="connection"/>',
            '<path class="marker-source"/>',
            '<path class="marker-target"/>',
            '<path class="connection-wrap"/>',
            '<g class="labels" />',
            '<g class="marker-vertices"/>',
            '<g class="link-tools" />'
        ].join(''),
        labelMarkup: [
          '<g class="esp-label">',
          '<rect class="label-border"/>',
          '<text />',
          '</g>'
        ].join(''),
        source: {id: edge.from, port: 'Out'},
        target: {id: edge.to, port: 'In'},
        labels: [{
          position: 0.5,
          attrs: {
            'rect': {
            fill: '#F5F5F5',
            rx: 13, ry: 13
            },
            'text': {
              text: joint.util.breakText((_.get(edge, 'label.expression') || ''), { width: 300 }),
              'font-weight': '300',
              'font-size': 10,
              fill: '#686868',
              'ref': 'rect',
              'ref-x': 0,
              'ref-y': 0
            }
          }
        }],
        attrs: {
          '.connection': { stroke: '#B5B5B5' },
          '.marker-source': { 'stroke-width': 0, fill: '#B5B5B5', d: 'M 10 0 L 0 5 L 10 10 z' },
          '.marker-target': { 'stroke-width': 0, fill: '#B5B5B5', d: 'M 10 0 L 0 5 L 10 10 z' },
          minLen: 10
        },
        edgeData: edge
     });
   }
}
