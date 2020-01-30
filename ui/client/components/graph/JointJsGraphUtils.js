import * as joint from "jointjs/index"

export function findLinkBelowCell(jointJsGraph, cellView, paper) {
  return jointJsGraph.get("cells").find((cell) => {
    if (cell.id === cellView.model.id) {
      return false
    } else if (cell instanceof joint.dia.Link) {
      if (cellView.model.attributes.position) {
        const cellAttributes = cellView.model.attributes
        const {cellTopSideLine, cellBottomSideLine} = cellHorizontalLines(cellAttributes)
        const linkView = paper.findViewByModel(cell)
        const linkLine = joint.g.line(linkView.sourcePoint, linkView.targetPoint)
        return linkLine.intersection(cellTopSideLine) || linkLine.intersection(cellBottomSideLine)
      } else {
        return false
      }
    } else {
      return false
    }
  })
}

function cellHorizontalLines(cellAttributes) {
  const cellTopLeftPoint = joint.g.point(cellAttributes.position.x, cellAttributes.position.y)
  const cellTopRightPoint = joint.g.point(cellAttributes.position.x + cellAttributes.size.width, cellAttributes.position.y)

  const cellBottomLeftPoint = joint.g.point(cellAttributes.position.x, cellAttributes.position.y + cellAttributes.size.height)
  const cellBottomRightPoint = joint.g.point(cellAttributes.position.x + cellAttributes.size.width, cellAttributes.position.y + cellAttributes.size.height)

  const cellTopSideLine = joint.g.line(cellTopLeftPoint, cellTopRightPoint)
  const cellBottomSideLine =  joint.g.line(cellBottomLeftPoint, cellBottomRightPoint)
  return {
    cellTopSideLine: cellTopSideLine,
    cellBottomSideLine: cellBottomSideLine,
  }
}

export function findCell(jointJsGraph, cellId) {
  return jointJsGraph.get("cells").find((cell) => {
    return cell.id === cellId
  })
}
