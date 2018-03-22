import joint from 'jointjs'
import _ from 'lodash'
import NodeUtils from './NodeUtils'
import * as GraphUtils from './GraphUtils'
import ProcessUtils from '../../common/ProcessUtils';
import * as LoaderUtils from '../../common/LoaderUtils'

import nodeMarkup from './markups/node.html';
import boundingMarkup from './markups/bounding.html';
import expandIcon from '../../assets/img/expand.svg'
import collapseIcon from '../../assets/img/collapse.svg'

const rectWidth = 300
const rectHeight = 60
const nodeLabelFontSize = 15
const edgeStroke = '#b3b3b3'
const maxLineLength = 24;
const maxLineCount = 2;

const summaryCountConfig = {
  breakPoint: 5,
  maxExtraDigits: 2,
  fontSizeStep: 3.5,
  defaultFontSize: 20,
};

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
      'text-anchor': 'middle',
      'alignment-baseline': "middle",
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

export function makeElement(node, processCounts, forExport, nodesSettings){
    var descr = (node.additionalFields || {}).description
    var customAttrs = require('../../assets/json/nodeAttributes.json');

    const { text: bodyContent, multiline } = getBodyContent(node);

    const hasCounts = !_.isEmpty(processCounts);

    const width = rectWidth;
    const widthWithTestResults = hasCounts ? width + rectHeight : width;
    const height = rectHeight;
    const iconFromConfig = (nodesSettings[ProcessUtils.findNodeConfigName(node)] || {}).icon
    const icon = iconFromConfig ? LoaderUtils.loadNodeSvgContent(iconFromConfig) : LoaderUtils.loadNodeSvgContent(`${node.type}.svg`)

    let attrs = {
      '.background': {
        width: widthWithTestResults
      },
      '.background title': {
        text: descr
      },
      '.body': {
        width: widthWithTestResults,
      },
      'rect.nodeIconPlaceholder': {
        fill: customAttrs[node.type].styles.fill,
        opacity: node.isDisabled ? 0.5 : 1
      },
      '.nodeIconItself': {
        'xlink:href': 'data:image/svg+xml;utf8,' + encodeURIComponent(icon) //we encode icon data to have standalone svg that can be used to generate pdf
      },
      '.contentText': {
        text: bodyContent,

        // magic/hack: central vertical position when text is in 2 lines
        y: multiline ? 5 : undefined,
      },
      '.testResultsPlaceHolder': {
        display: hasCounts && !forExport ? 'block' : 'none',
        'ref-x': width
      },
      '.testResultsSummary': getTestResultsSummaryAttr(processCounts, width),
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
}

function getTestResultsSummaryAttr(processCounts, width) {
  const { breakPoint, fontSizeStep, maxExtraDigits, defaultFontSize } = summaryCountConfig;

  const hasCounts = !_.isEmpty(processCounts);
  const hasErrors = hasCounts && processCounts && processCounts.errors > 0;
  const countsContent = hasCounts ?  (processCounts ? `${processCounts.all}` : "0") : "";
  let extraDigitsCount = Math.max(countsContent.length - breakPoint, 0);
  extraDigitsCount = Math.min(extraDigitsCount, maxExtraDigits);
  const summaryCountFontSize = defaultFontSize - extraDigitsCount * fontSizeStep;

  return {
    text: countsContent,
    style: { 'font-size': summaryCountFontSize },
    fill: hasErrors ? 'red' : 'white',
    'ref-x': width + rectHeight/2,

    // magic/hack: central vertical position when font-size changes
    'y': 37 - extraDigitsCount * 1.5,
  }
}

function getBodyContent(node) {
  let bodyContent = node.id || "";

  if (bodyContent.length <= maxLineLength) {
    return {
      text: bodyContent,
      multiline: false,
    };
  }

  const splitContent = bodyContent.split(' ');

  if (splitContent[0].length > maxLineLength) {
    return {
      text: bodyContent.slice(0, maxLineLength) + '...',
      multiline: false,
    }
  }

  let tmpLines = [ splitContent[0] ];

  for ( let str of splitContent.slice(1)) {
    let idx = tmpLines.length - 1;

    if (tmpLines[idx].length + str.length <= maxLineLength) {
      tmpLines[idx] += ' ' + str;
      continue;
    }

    if (tmpLines.length >= maxLineCount) {
      tmpLines[idx] += '...';
      break;
    }

    if (str.length > maxLineLength) {
      tmpLines[idx + 1] = str.slice(0, maxLineLength) + '...';
      break;
    }

    tmpLines[idx + 1] = str;
  }

  let idx = tmpLines.length - 1;
  if (tmpLines[idx].length > maxLineLength) {
      tmpLines[idx] = tmpLines[idx].slice(0, maxLineLength) + '...';
  }

  return {
    text: tmpLines.join('\n'),
    multiline: tmpLines.length > 1,
  }
}

export function boundingRect(nodes, expandedGroup, layout, group) {
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
}

export function makeLink(edge, forExport) {
  const label = NodeUtils.edgeLabel(edge)
  return new joint.dia.Link({
    //TODO: some different way to create id? Must be deterministic and unique
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
      forExport: forExport
    }
 });
}

export default {
  makeElement,
  boundingRect,
  makeLink,
};
