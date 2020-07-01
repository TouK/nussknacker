/* eslint-disable i18next/no-literal-string */
import * as joint from "jointjs"
import {dia, util, shapes} from "jointjs"
import expandIcon from "../../../assets/img/expand.svg"
import "../graphTheme.styl"

const CONTENT_COLOR = "#1E1E1E"
const PORT_COLOR = "#FFFFFF"
const BORDER_COLOR = "#B5B5B5"

const rectWidth = 300
const rectHeight = 60

const background: dia.MarkupNodeJSON = {
  selector: "background",
  tagName: "rect",
  className: "background",
  attributes: {
    width: rectWidth,
    height: rectHeight,
  },
  children: [
    {
      selector: "title",
      tagName: "title",
    },
  ],
}

const iconBackgroundSize = rectHeight
const iconBackground: dia.MarkupNodeJSON = {
  selector: "iconBackground",
  tagName: "rect",
  attributes: {
    width: iconBackgroundSize,
    height: iconBackgroundSize,
  },
}

const border: dia.MarkupNodeJSON = {
  selector: "border",
  tagName: "rect",
  className: "body",
  attributes: {
    width: rectWidth,
    height: rectHeight,
    "stroke-width": 1,
    fill: "none",
  },
}

const iconSize = iconBackgroundSize / 2
const icon: dia.MarkupNodeJSON = {
  selector: "icon",
  tagName: "image",
  attributes: {
    width: iconSize,
    height: iconSize,
    x: iconSize / 2,
    y: iconSize / 2,
  },
}

const content: dia.MarkupNodeJSON = {
  selector: "content",
  tagName: "text",
  attributes: {
    x: iconBackgroundSize + 10,
    y: rectHeight / 2,
    fill: CONTENT_COLOR,
    "font-size": 15,
    "pointer-events": "none",
    "font-weight": 400,
  },
}

const expandIconSize = 26
const groupElements: dia.MarkupNodeJSON = {
  selector: "groupElements",
  tagName: "g",
  children: [
    {
      selector: "expand",
      tagName: "image",
      className: "expandIcon nodeIcon",
      attributes: {
        width: expandIconSize,
        height: expandIconSize,
        x: rectWidth - expandIconSize / 2,
        y: -expandIconSize / 2,
        "xlink:href": expandIcon,
      },
    },
  ],
  attributes: {
    noExport: "",
  },
}

const portSize = 5
const portMarkup: dia.MarkupNodeJSON = {
  selector: "port",
  tagName: "circle",
  attributes: {
    magnet: true,
    r: portSize,
    fill: PORT_COLOR,
    stroke: BORDER_COLOR,
    "stroke-width": 1,
  },
}

const testResultsHeight = 24
const testResults: dia.MarkupNodeJSON = {
  tagName: "g",
  children: [
    {
      selector: "testResults",
      tagName: "rect",
      className: "testResultsPlaceholder",
      attributes: {
        height: testResultsHeight,
        y: rectHeight,
      },
    },
    {
      selector: "testResultsSummary",
      tagName: "text",
      className: "testResultsSummary",
      attributes: {
        height: testResultsHeight,
        y: rectHeight + testResultsHeight / 2 + 1,
      },
    },
  ],
  attributes: {
    noExport: "",

  },
}

const defaults = util.defaultsDeep(
  {
    size: {
      width: rectWidth,
      height: rectHeight,
    },
    attrs: {
      content: {
        textVerticalAnchor: "middle",
      },
      border: {
        stroke: BORDER_COLOR,
      },
      testResults: {
        refX: rectWidth,
      },
      testResultsSummary: {
        textAnchor: "middle",
        textVerticalAnchor: "middle",
        refX: rectWidth,
      },
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
  shapes.devs.Model.prototype.defaults,
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
    testResults,
  ],
}

export const EspNodeShape = shapes.devs.Model.define(
  `esp.Model`,
  defaults,
  protoProps,
) as typeof shapes.devs.Model

export const EspGroupShape = shapes.devs.Model.define(
  `esp.Group`,
  defaults,
  {
    ...protoProps,
    markup: [...protoProps.markup, groupElements],
  },
) as typeof EspNodeShape
