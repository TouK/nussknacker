import * as joint from 'jointjs/index'
import _ from 'lodash'
import NodeUtils from './NodeUtils'
import * as GraphUtils from './GraphUtils'
import ProcessUtils from '../../common/ProcessUtils';
import * as LoaderUtils from '../../common/LoaderUtils'

import nodeMarkup from './markups/node.html';
import boundingMarkup from './markups/bounding.html';
import expandIcon from '../../assets/img/expand.svg'
import collapseIcon from '../../assets/img/collapse.svg'
import customAttrs from '../../assets/json/nodeAttributes.json'


import SVGUtils from "../../common/SVGUtils";

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
    '.': {
      magnet: false
		},
    '.body': {
      fill: "none",
      width: rectWidth,
      height: rectHeight,
      stroke: '#B5B5B5',
      strokeWidth: 1,
    },
    '.background': {
      width: rectWidth,
      height: rectHeight,
    },
    '.disabled-node-layer': {
      width: rectWidth,
      height: rectHeight,
      zIndex: 0
    },
    text: {
      fill: '#1E1E1E',
      pointerEvents: 'none',
      fontWeight: 400
    },
    '.nodeIconPlaceholder': {
      x: 0,
      y: 0,
      height: rectHeight,
      width: rectHeight
    },
    '.nodeIconItself': {
      width: rectHeight/2,
      height: rectHeight/2,
      ref: '.nodeIconPlaceholder',
      refX: rectHeight/4,
      refY: rectHeight/4
    },
    '.contentText': {
      fontSize: nodeLabelFontSize,
      ref: '.nodeIconPlaceholder',
      refX: rectHeight + 10,
      refY: rectHeight/2,
      textVerticalAnchor: 'middle'
    },
    '.testResultsPlaceholder': {
      ref: '.nodeIconPlaceholder',
      refX: rectWidth,
      y: 0,
      height: rectHeight,
      width: rectHeight
    },
    '.testResultsSummary': {
      textAnchor: 'middle',
      alignmentBaseline: "middle"
    }
  }
}

const portsAttrs = () => {
  return {
    '.port': {
      refX: 0,
      refY: 0,
    },
    '.port-body': {
      r: 5,
      magnet: true,
      fontSize: 10
    }
  }
}

const portInAttrs = () => {
  return Object.assign({},  portsAttrs(), {
  '.port circle': {
    fill: '#FFFFFF',
    magnet: 'passive',
    stroke: edgeStroke,
    strokeWidth: '1',
    type: 'input'
  }})
}

const portOutAttrs = () => {
  return Object.assign({},  portsAttrs(), {
    '.port circle': {
      fill: '#FFFFFF',
      stroke: edgeStroke,
      strokeWidth: '1',
      type: 'output'
  }})
}

joint.shapes.devs.EspNode =  joint.shapes.devs.Model.extend({
	markup: nodeMarkup,
	portMarkup: '<g class="port"><circle class="port-body"/></g>',
	portLabelMarkup: null,

	defaults: joint.util.deepSupplement({
		type: 'devs.GenericModel',
		attrs: attrsConfig(),
		size: {width: 1, height: 1},
		inPorts: [],
		outPorts: [],
		ports: {
			groups: {
        'in': {
          position: 'top',
          attrs: portInAttrs()
        },
        'out': {
          position: 'bottom',
          attrs: portOutAttrs()
        }
			}
		}
	}, joint.shapes.devs.Model.prototype.defaults)
});

export function makeElement(node, processCounts, forExport, nodesSettings){
  const description = _.get(node.additionalFields, 'description', null)
  const { text: bodyContent, multiline } = getBodyContent(node);
  const hasCounts = !_.isEmpty(processCounts);
  const width = rectWidth;
  const height = rectHeight;
  const iconFromConfig = (nodesSettings[ProcessUtils.findNodeConfigName(node)] || {}).icon
  const icon = iconFromConfig ? LoaderUtils.loadNodeSvgContent(iconFromConfig) : LoaderUtils.loadNodeSvgContent(`${node.type}.svg`)
  const testResultsHeight = 24
  const pxPerChar = 8
  const countsPadding = 8
  //dynamically sized width
  const testResultsWidth = (_.toArray(_.toString(processCounts ? processCounts.all : "")).length * pxPerChar) + 2 * countsPadding
  let attrs = {
    '.background': {
      width: width,
      opacity: node.isDisabled ? 0.4 : 1
    },
    '.disabled-node-layer': {
      display: node.isDisabled ? 'block' : 'none',
      width: width,
      fill: '#b3b3b3'
    },
    '.background title': {
      text: description
    },
    '.body': {
      width: width,
    },
    'rect.nodeIconPlaceholder': {
      fill: customAttrs[node.type].styles.fill,
      opacity: node.isDisabled ? 0.4 : 1
    },
    '.nodeIconItself': {
      'xlink:href': SVGUtils.svgToDataURL(icon), //we encode icon data to have standalone svg that can be used to generate pdf
    },
    '.contentText': {
      text: bodyContent,
      opacity: node.isDisabled ? 0.65 : 1
    },
    '.testResultsPlaceHolder': {
      display: hasCounts && !forExport ? 'block' : 'none',
      width: testResultsWidth,
      refX: width - testResultsWidth,
      refY: height,
      height: testResultsHeight
    },
    '.testResultsSummary': getTestResultsSummaryAttr(processCounts, width, testResultsWidth, testResultsHeight),
    '.groupElements': {
      display: NodeUtils.nodeIsGroup(node) ? 'block' : 'none'
    },
    '.expandIcon': {
      'xlink:href': expandIcon,
      width: 26,
      height: 26,
      refX: width - 13,
      refY: - 13
    }
  };

  const inPorts = NodeUtils.hasInputs(node) ? ['In'] : [];
  const outPorts = NodeUtils.hasOutputs(node) ? ['Out'] : [];

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

function getTestResultsSummaryAttr(processCounts, width, testResultsWidth, testResultsHeight) {
  const { breakPoint, fontSizeStep, maxExtraDigits, defaultFontSize } = summaryCountConfig;

  const hasCounts = !_.isEmpty(processCounts);
  const hasErrors = hasCounts && processCounts && processCounts.errors > 0;
  const countsContent = hasCounts ?  (processCounts ? `${processCounts.all}` : "0") : "";
  let extraDigitsCount = Math.max(countsContent.length - breakPoint, 0);
  extraDigitsCount = Math.min(extraDigitsCount, maxExtraDigits);

  return {
    text: countsContent,
    fill: hasErrors ? 'red' : '#ccc',
    refX: width - testResultsWidth/2,
    // magic/hack: central vertical position when font-size changes
    y: 78 - extraDigitsCount * 1.5,
    height: 16
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

  for (let str of splitContent.slice(1)) {
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

  let labels = []
  if (label.length !== 0) {
    labels.push({
      position: 0.5,
      attrs: {
        rect: {
          ref: 'text',
          refX: -5,
          refY: -5,
          refWidth: '100%',
          refHeight: '100%',
          refWidth2: 10,
          refHeight2: 10,
          stroke: '#686868',
          fill: '#F5F5F5',
          strokeWidth: 1,
          rx: 5,
          ry: 5,
          cursor: 'pointer'
        },
        text: {
          text: joint.util.breakText(label, { width: rectWidth }),
          fontWeight: 300,
          fontSize: 10,
          fill: '#686868',
          textAnchor: 'middle',
          textVerticalAnchor: 'middle',
        },
      }
    })
  }

  return new joint.dia.Link({
    //TODO: some different way to create id? Must be deterministic and unique
    id: `${edge.from}-${edge.to}-${label}`,
    source: {id: edge.from, port: 'Out'},
    target: {id: edge.to, port: 'In'},
    labels: labels,
    attrs: {
      line: {
        connection: true,
      },
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
