/* eslint-disable i18next/no-literal-string */
import * as joint from "jointjs"
import {edgeStroke, rectWidth, rectHeight, nodeLabelFontSize} from "./misc"
import nodeMarkup from "../markups/node.html"

const attrsConfig = () => {
  return {
    ".": {
      magnet: false,
    },
    ".body": {
      fill: "none",
      width: rectWidth,
      height: rectHeight,
      stroke: "#B5B5B5",
      strokeWidth: 1,
    },
    ".background": {
      width: rectWidth,
      height: rectHeight,
    },
    ".disabled-node-layer": {
      width: rectWidth,
      height: rectHeight,
      zIndex: 0,
    },
    text: {
      fill: "#1E1E1E",
      pointerEvents: "none",
      fontWeight: 400,
    },
    ".nodeIconPlaceholder": {
      x: 0,
      y: 0,
      height: rectHeight,
      width: rectHeight,
    },
    ".nodeIconItself": {
      width: rectHeight / 2,
      height: rectHeight / 2,
      ref: ".nodeIconPlaceholder",
      refX: rectHeight / 4,
      refY: rectHeight / 4,
    },
    ".contentText": {
      fontSize: nodeLabelFontSize,
      ref: ".nodeIconPlaceholder",
      refX: rectHeight + 10,
      refY: rectHeight / 2,
      textVerticalAnchor: "middle",
    },
    ".testResultsPlaceholder": {
      ref: ".nodeIconPlaceholder",
      refX: rectWidth,
      y: 0,
      height: rectHeight,
      width: rectHeight,
    },
    ".testResultsSummary": {
      textAnchor: "middle",
      alignmentBaseline: "middle",
    },
  }
}

const portsAttrs = () => {
  return {
    ".port": {
      refX: 0,
      refY: 0,
    },
    ".port-body": {
      r: 5,
      magnet: true,
      fontSize: 10,
    },
  }
}

const portInAttrs = () => {
  return Object.assign({}, portsAttrs(), {
    ".port circle": {
      fill: "#FFFFFF",
      magnet: "passive",
      stroke: edgeStroke,
      strokeWidth: "1",
      type: "input",
    },
  })
}

const portOutAttrs = () => {
  return Object.assign({}, portsAttrs(), {
    ".port circle": {
      fill: "#FFFFFF",
      stroke: edgeStroke,
      strokeWidth: "1",
      type: "output",
    },
  })
}

const GenericModel = joint.shapes.devs.Model.define(
  `custom.GenericModel`,
  joint.util.defaultsDeep(
    {
      attrs: attrsConfig(),
      size: {width: 1, height: 1},
      inPorts: [],
      outPorts: [],
      ports: {
        groups: {
          in: {
            position: "top",
            attrs: portInAttrs(),
          },
          out: {
            position: "bottom",
            attrs: portOutAttrs(),
          },
        },
      },
    },
    joint.shapes.devs.Model.prototype.defaults,
  ),
  {
    markup: nodeMarkup,
    portMarkup: "<g class=\"port\"><circle class=\"port-body\"/></g>",
    portLabelMarkup: null,
  },
)

//FIXME: better jointjs with typescript connection
export const EspNodeShape = GenericModel as joint.dia.Cell.Constructor<joint.shapes.devs.Model, joint.shapes.devs.ModelAttributes>
