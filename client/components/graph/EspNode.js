import joint from 'jointjs'
import _ from 'lodash'
import NodeUtils from './NodeUtils'
import GraphUtils from './GraphUtils'

import nodeMarkup from './markups/node.html';
import boundingMarkup from './markups/bounding.html';
import expandIcon from '../../assets/img/expand.svg'
import collapseIcon from '../../assets/img/collapse.svg'


import InlinedSvgs from '../../assets/icons/InlinedSvgs'

const rectWidth = 300
const rectHeight = 60
const nodeLabelFontSize = 15
const edgeStroke = '#b3b3b3'


const attrsConfig = () => {
  return {
    '.': {magnet: false},
    '.body': {
      fill: "none",
      width: rectWidth, height: rectHeight,
      stroke: '#B5B5B5',
      'stroke-width': 1,
    },
    '.background': {
      width: rectWidth, height: rectHeight,
    },
    text: {
      fill: '#1E1E1E',
      'pointer-events': 'none',
      'font-weight': 400
    },
    '.nodeIconPlaceholder': {
      x: 0, y: 0,
      height: rectHeight,
      width: rectHeight
    },
    '.nodeIconItself': {
      width: rectHeight/2, height: rectHeight/2,
      'ref': '.nodeIconPlaceholder',
      'ref-x': rectHeight/4, 'ref-y': rectHeight/4
    },
    '.contentText': {
      'font-size': nodeLabelFontSize,
      'ref': '.nodeIconPlaceholder',
      'ref-x': rectHeight + 10, 'ref-y': rectHeight/2 - nodeLabelFontSize/2
    },
    '.testResultsPlaceholder': {
      'ref': '.nodeIconPlaceholder',
      'ref-x': rectWidth, y: 0,
      height: rectHeight,
      width: rectHeight

    },
    '.testResultsSummary': {
      width: rectHeight/2, height: rectHeight/2,
      'text-anchor': 'middle',
      'alignment-baseline': "middle",
      'ref-y': rectHeight/3
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
      fill: '#FFFFFF',
      magnet: 'passive',
      stroke: edgeStroke,
      'stroke-width': '1',
      type: 'input'
    },
    '.outPorts circle': {
      fill: '#FFFFFF',
      stroke: edgeStroke,
      'stroke-width': '1',
      type: 'output'
    }
  }
}

joint.shapes.devs.EspNode = joint.shapes.basic.Generic.extend(_.extend({}, joint.shapes.basic.PortsModelInterface, {

    markup: nodeMarkup,
    portMarkup: '<g class="port port<%= id %>"><circle class="port-body"/></g>',

    defaults: joint.util.deepSupplement({

        type: 'devs.Model',
        size: {width: 1, height: 1},

        inPorts: [],
        outPorts: [],

        attrs: attrsConfig()
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

    makeElement(node, processCounts, forExport) {
        const hasCounts = !_.isEmpty(processCounts)
        var descr = (node.additionalFields || {}).description
        var customAttrs = require('json!../../assets/json/nodeAttributes.json');

        var bodyContent = node.id ? node.id : "";
        //TODO: displaying counts for subprocesses
        //FIXME: proper width for large numbers
        var countsContent = hasCounts ? (processCounts ? processCounts.all : "0") : ""
        var hasErrors = hasCounts && processCounts && processCounts.errors > 0

        // Compute width/height of the rectangle based on the number
        // of lines in the label and the letter size. 0.6 * letterSize is
        // an approximation of the monospace font letter width.
        var maxLineLength = _.max(bodyContent.split('\n'), function (l) {
            return l.length;
        }).length;
        var letterSize = 15;
        var calculatedWidth = 1.2 * (letterSize * (0.6 * maxLineLength));
        var width = _.max([rectWidth, calculatedWidth]);
        var height = 150;
        var icon = InlinedSvgs.svgs[node.type]

        var widthWithTestResults = width + (hasCounts ? rectHeight : 0)

        var attrs = {
          '.background': {
            width: widthWithTestResults
          },
          '.background title': {
            text: descr
          },
          '.body': {
            width: widthWithTestResults
          },
          'rect.nodeIconPlaceholder': {
            fill: customAttrs[node.type].styles.fill,
            opacity: node.isDisabled ? 0.5 : 1
          },
          '.nodeIconItself': {
            'xlink:href': 'data:image/svg+xml;utf8,' + encodeURIComponent(icon)
          },
          '.contentText': {
            text: bodyContent
          },
          '.testResultsPlaceHolder': {
            display: hasCounts && !forExport ? 'block' : 'none',
            'ref-x': width
          },
          '.testResultsSummary': {
            text: countsContent,
            fill: hasErrors ? 'red' : 'white',
            'ref-x': width + rectHeight/2
          },
          '.groupElements': {
            'display': NodeUtils.nodeIsGroup(node) ? 'block' : 'none'
          },
          '.expandIcon': {
            'xlink:href': expandIcon,
            width: 26,
            height: 26,
            'ref-x': width - 13,
            'ref-y': - 13
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
            nodeData: node,
            definitionToCompare: {
              node: node,
              processCounts: processCounts,
              forExport: forExport
            }
        });
    },

    boundingRect(nodes, expandedGroup, layout, group) {
      const boundingRect = GraphUtils.computeBoundingRect(expandedGroup, layout, nodes, rectHeight, 15)

      return new joint.shapes.basic.Rect({
          id: group.id,
          markup: boundingMarkup,
          position: { x: boundingRect.x, y: boundingRect.y },
          backgroundObject: true,
          nodeData: group,
          size: { width: boundingRect.width, height: boundingRect.height },
          attrs: {
            rect: {
              fill: 'green', opacity: 0.1
            },
            '.collapseIcon': {
              'xlink:href': collapseIcon,
              'ref-x': boundingRect.width - 13,
              'ref-y': - 13,
              width: 26,
              height: 26,
            },
          },
          definitionToCompare: {
            boundingRect: boundingRect,
            group
          }
      })
    },

    makeLink(edge, outgoingEdges, forExport) {
      const label = NodeUtils.edgeLabel(edge, outgoingEdges)
      return new joint.dia.Link({
        //TODO: czy da sie jakos inaczej, ale unikanie i deterministycznie?
        id: `${edge.from}-${edge.to}-${label}`,
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
            },
            'text': {
              text: joint.util.breakText(label, { width: rectWidth }),
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
          '.link-tools': forExport ? { display: 'none'} : {},
          '.connection': forExport ? { stroke: edgeStroke, 'stroke-width': 2, fill: edgeStroke } : { stroke: 'white', 'stroke-width': 2, fill: 'none' },
          '.marker-target': { 'stroke-width': forExport ? 1 : 0, stroke: forExport ? edgeStroke : 'white', fill: 'white', d: 'M 10 0 L 0 5 L 10 10 L 8 5 z' },
          minLen: label ? 20 : 10
        },
        edgeData: edge,
        definitionToCompare: {
          edge: edge,
          label: label,
          forExport: forExport
        }
     });
   }
}
