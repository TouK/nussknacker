/* eslint-disable i18next/no-literal-string */
import {dia, shapes, util} from "jointjs"
import "../graphTheme.styl"
import {getStringWidth} from "./element"
import {getRoundedRectPath} from "./getRoundedRectPath"

export const CONTENT_COLOR = "#1E1E1E"
export const PORT_COLOR = "#FFFFFF"
export const BORDER_COLOR = "#B5B5B5"

export const RECT_WIDTH = 300
export const RECT_HEIGHT = 60
export const BORDER_RADIUS = 5
export const CONTENT_PADDING = 10
export const iconBackgroundSize = RECT_HEIGHT
export const iconSize = iconBackgroundSize / 2

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
    x: iconBackgroundSize + CONTENT_PADDING,
    y: RECT_HEIGHT / 2,
    fill: CONTENT_COLOR,
    "font-size": 15,
    "pointer-events": "none",
    "font-weight": 400,
  },
}

const portSize = iconSize / 3
const portMarkup: dia.MarkupNodeJSON = {
  selector: "port",
  tagName: "circle",
  attributes: {
    magnet: true,
    r: portSize,
    fill: PORT_COLOR,
    stroke: BORDER_COLOR,
    "stroke-width": 5,
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
        y: RECT_HEIGHT - testResultsHeight/2,
      },
    },
    {
      selector: "testResultsSummary",
      tagName: "text",
      className: "testResultsSummary",
      attributes: {
        height: testResultsHeight,
        y: RECT_HEIGHT + testResultsHeight / 2 + 1 - testResultsHeight/2,
      },
    },
  ],
  attributes: {
    noExport: "",
  },
}

const refX = RECT_HEIGHT - getStringWidth("1")/2
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
        refX,
        rx: 5,
        z: 2,
      },
      testResultsSummary: {
        textAnchor: "middle",
        textVerticalAnchor: "middle",
        refX,
        z: 2,
      },
    },
    inPorts: [],
    outPorts: [],
    ports: {
      groups: {
        in: {
          position: {name: `top`, args: {dx: 90}},
          attrs: {
            magnet: "passive",
            type: "input",
            z: 1,
          },
        },
        out: {
          position: {name: `bottom`, args: {dx: 90}},
          attrs: {
            type: "output",
            z: 1,
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

