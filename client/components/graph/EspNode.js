import joint from 'jointjs'
import _ from 'lodash'

import markup from './markups/markup.html';
import InlinedSvgs from '../../assets/icons/InlinedSvgs'

const rectWidth = 300
const rectHeight = 60
const nodeLabelFontSize = 15

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
      'ref': '.testResultsPlaceholder',
      'ref-x': rectHeight/2, 'ref-y': rectHeight/3,
      'text-anchor': 'middle',
      'alignment-baseline': "middle"
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
}

joint.shapes.devs.EspNode = joint.shapes.basic.Generic.extend(_.extend({}, joint.shapes.basic.PortsModelInterface, {

    markup: markup,
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

    makeElement(node, hasResults, testResult, testErrors) {
        var descr = (node.additionalFields || {}).description
        var customAttrs = require('json!../../assets/json/nodeAttributes.json');

        var bodyContent = node.id ? node.id : "";
        var testResultsContent = hasResults ? (testResult ? testResult.length : "0") : ""
        var testErrorsContent = hasResults && testErrors && testErrors.length > 0 ? testErrors.length :  ""

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
        var textPosX = (width / 2) - ((calculatedWidth / 2) + 5)
        var icon = InlinedSvgs.svgs[node.type]

        var widthWithTestResults = width + (hasResults ? rectHeight : 0)

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
            fill: customAttrs[node.type].styles.fill
          },
          '.nodeIconItself': {
            'xlink:href': 'data:image/svg+xml;utf8,' + encodeURIComponent(icon)
          },
          '.contentText': {
            text: bodyContent
          },
          '.testResultsPlaceHolder': {
            display: hasResults ? 'block' : 'none'
          },
          '.testResultsSummary': {
            text: testResultsContent,
            //TODO: style??
            fill: testErrorsContent ? 'red' : 'white'
          },
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
            },
            'text': {
              text: joint.util.breakText((_.get(edge, 'label.expression') || ''), { width: rectWidth }),
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
          '.connection': { stroke: 'white' },
          '.marker-target': { 'stroke-width': 0, fill: 'white', d: 'M 10 0 L 0 5 L 10 10 z' },
          minLen: 10
        },
        edgeData: edge
     });
   }
}
