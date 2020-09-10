/* eslint-disable i18next/no-literal-string */
import {dia, shapes, util} from "jointjs"
import expandIcon from "../../../assets/img/expand.svg"
import "../graphTheme.styl"
import {getRoundedRectPath} from "./getRoundedRectPath"

const CONTENT_COLOR = "#1E1E1E"
const PORT_COLOR = "#FFFFFF"
const BORDER_COLOR = "#B5B5B5"

const RECT_WIDTH = 300
const RECT_HEIGHT = 60
const BORDER_RADIUS = 5

const background: dia.MarkupNodeJSON = {
  selector: "background",
  tagName: "rect",
  className: "background",
  attributes: {
    width: RECT_WIDTH,
    height: RECT_HEIGHT,
    rx: BORDER_RADIUS,
  },
  children: [
    {
      selector: "title",
      tagName: "title",
    },
  ],
}

const iconBackgroundSize = RECT_HEIGHT
const iconBackground: dia.MarkupNodeJSON = {
  selector: "iconBackground",
  tagName: "path", //TODO: check if it's fast enough
  attributes: {
    d: getRoundedRectPath(iconBackgroundSize, [BORDER_RADIUS, 0, 0, BORDER_RADIUS]),
  },
}

const border: dia.MarkupNodeJSON = {
  selector: "border",
  tagName: "rect",
  className: "body",
  attributes: {
    width: RECT_WIDTH,
    height: RECT_HEIGHT,
    "stroke-width": 1,
    fill: "none",
    rx: BORDER_RADIUS,
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
    y: RECT_HEIGHT / 2,
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
        x: RECT_WIDTH - expandIconSize / 2,
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
        y: RECT_HEIGHT,
      },
    },
    {
      selector: "testResultsSummary",
      tagName: "text",
      className: "testResultsSummary",
      attributes: {
        height: testResultsHeight,
        y: RECT_HEIGHT + testResultsHeight / 2 + 1,
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
      width: RECT_WIDTH,
      height: RECT_HEIGHT,
    },
    attrs: {
      content: {
        textVerticalAnchor: "middle",
      },
      border: {
        stroke: BORDER_COLOR,
      },
      testResults: {
        refX: RECT_WIDTH,
      },
      testResultsSummary: {
        textAnchor: "middle",
        textVerticalAnchor: "middle",
        refX: RECT_WIDTH,
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
