/* eslint-disable i18next/no-literal-string */
import NodeUtils from "../NodeUtils"
import {edgeStroke, rectWidth} from "./misc"
import * as joint from "jointjs"

export function makeLink(edge, forExport?) {
  const label = NodeUtils.edgeLabel(edge)

  const labels = []
  if (label.length !== 0) {
    labels.push({
      position: 0.5,
      attrs: {
        rect: {
          ref: "text",
          refX: -5,
          refY: -5,
          refWidth: "100%",
          refHeight: "100%",
          refWidth2: 10,
          refHeight2: 10,
          stroke: "#686868",
          fill: "#F5F5F5",
          strokeWidth: 1,
          rx: 5,
          ry: 5,
          cursor: "pointer",
        },
        text: {
          text: joint.util.breakText(label, {width: rectWidth}),
          fontWeight: 300,
          fontSize: 10,
          fill: "#686868",
          textAnchor: "middle",
          textVerticalAnchor: "middle",
        },
      },
    })
  }

  return new joint.dia.Link({
    //TODO: some different way to create id? Must be deterministic and unique
    id: `${edge.from}-${edge.to}-${label}`,
    source: {id: edge.from, port: "Out"},
    target: {id: edge.to, port: "In"},
    labels: labels,
    attrs: {
      line: {
        connection: true,
      },
      ".link-tools": forExport ? {display: "none"} : {},
      ".connection": forExport ? {stroke: edgeStroke, "stroke-width": 2, fill: edgeStroke} : {
        stroke: "white",
        "stroke-width": 2,
        fill: "none",
      },
      ".marker-target": {
        "stroke-width": forExport ? 1 : 0,
        stroke: forExport ? edgeStroke : "white",
        fill: "white",
        d: "M 10 0 L 0 5 L 10 10 L 8 5 z",
      },
    },
    edgeData: edge,
    definitionToCompare: {
      edge: edge,
      forExport: forExport,
    },
  })
}
