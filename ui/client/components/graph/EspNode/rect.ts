import collapseIcon from "../../../assets/img/collapse.svg"
import * as GraphUtils from "../GraphUtils"
import boundingMarkup from "../markups/bounding.html"
import {rectHeight} from "./misc"
import * as joint from "jointjs"
import {Layout} from "../../../actions/nk"
import {GroupType, NodeType} from "../../../types"
import NodeUtils from "../NodeUtils"

export function boundingRect(nodes: joint.dia.Element[], layout: Layout, nodesWithGroups: NodeType[]) {
  return (expandedGroup: GroupType) => {
    const group = NodeUtils.createGroupNode(nodesWithGroups, expandedGroup)
    //TODO: replace with embed & fitToEmbeds
    const boundingRect = GraphUtils.computeBoundingRect(expandedGroup, layout, nodes, rectHeight, 15)

    return new joint.shapes.basic.Rect({
      id: group.id,
      markup: boundingMarkup,
      position: {x: boundingRect.x, y: boundingRect.y},
      backgroundObject: true,
      nodeData: group,
      size: {width: boundingRect.width, height: boundingRect.height},
      attrs: {
        rect: {
          width: boundingRect.width,
          height: boundingRect.height,
          fill: "green",
          opacity: 0.1,
        },
        ".collapseIcon": {
          xlinkHref: collapseIcon,
          refX: "100%",
          x: -13,
          y: -13,
          width: 26,
          height: 26,
        },
      },
      definitionToCompare: {
        boundingRect,
        group,
      },
    })
  }
}

