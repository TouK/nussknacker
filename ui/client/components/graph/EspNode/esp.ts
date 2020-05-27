/* eslint-disable i18next/no-literal-string */
import * as joint from "jointjs"
import {edgeStroke, rectWidth, rectHeight, nodeLabelFontSize} from "./misc"
import expandIcon from "../../../assets/img/expand.svg"

const background = {
  selector: "background",
  tagName: "rect",
  attributes: {
    class: "background",
    width: rectWidth,
    height: rectHeight,
    refWidth: "100%",
    refHeight: "100%",
  },
  children: [
    {
      selector: "title",
      tagName: "title",
    },
  ],
}
const iconBackground = {
  selector: "iconBackground",
  tagName: "rect",
  attributes: {
    height: rectHeight,
    width: rectHeight,
  },
}
const border = {
  selector: "border",
  tagName: "rect",
  attributes: {
    class: "body",
    width: rectWidth,
    height: rectHeight,
    strokeWidth: 1,
    fill: "none",
  },
}
const icon = {
  selector: "icon",
  tagName: "image",
  attributes: {
    width: rectHeight / 2,
    height: rectHeight / 2,
    x: rectHeight / 4,
    y: rectHeight / 4,
  },
}
const content = {
  selector: "content",
  tagName: "text",
  attributes: {
    x: rectHeight + 10,
    y: rectHeight / 2,
  },
}
const groupElements = {
  selector: "groupElements",
  tagName: "g",
  children: [
    {
      selector: "expand",
      tagName: "image",
      attributes: {
        "xlink:href": expandIcon,
        class: "expandIcon nodeIcon",
        width: 26,
        height: 26,
        x: rectWidth - 13,
        y: -13,
      },
    },
  ],
}
const portMarkup = {
  selector: "port",
  tagName: "circle",
  attributes: {
    magnet: true,
    r: 5,
    fill: "#FFFFFF",
    stroke: edgeStroke,
    strokeWidth: "1",
  },
}

const defaults = joint.util.defaultsDeep(
  {
    size: {
      width: rectWidth,
      height: rectHeight,
    },
    attrs: {
      text: {
        fill: "#1E1E1E",
        pointerEvents: "none",
        fontWeight: 400,
        fontSize: nodeLabelFontSize,
        textVerticalAnchor: "middle",
      },
      // ".testResultsPlaceholder": {
      //   ref: ".nodeIconPlaceholder",
      //   refX: rectWidth,
      //   y: 0,
      //   height: rectHeight,
      //   width: rectHeight,
      // },
      // ".testResultsSummary": {
      //   textAnchor: "middle",
      //   alignmentBaseline: "middle",
      // },
    },
    inPorts: [],
    outPorts: [],
    ports: {
      groups: {
        in: {
          position: `top`,
          attrs: {
            magnet: "passive",
            type: "input",
          },
        },
        out: {
          position: `bottom`,
          attrs: {
            type: "output",
          },
        },
      },
    },
  },
  joint.shapes.devs.Model.prototype.defaults,
)
const protoProps = {
  portMarkup: [portMarkup],
  portLabelMarkup: null,

  markup: [
    background,
    iconBackground,
    border,
    icon,
    content,
  ],
}

export const EspNodeShape = joint.shapes.devs.Model.define(
  `esp.Model`,
  defaults,
  protoProps,
) as typeof joint.shapes.devs.Model

export const EspGroupShape = joint.shapes.devs.Model.define(
  `esp.Group`,
  defaults,
  {
    ...protoProps,
    markup: [...protoProps.markup, groupElements],
  },
) as typeof EspNodeShape
