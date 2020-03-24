import * as joint from "jointjs"
import {dia} from "jointjs"
import {makeLink} from "./EspNode"

type Attrs = {
  el: $TodoType,
  validateConnection: $TodoType,
  model: dia.Graph,
  height: dia.Paper.Dimension,
  width: dia.Paper.Dimension,
  canWrite?: boolean,
}

export function getPaper({canWrite, validateConnection, ...opts}: Attrs) {
  const options = {
    ...opts,
    validateConnection,
    gridSize: 1,
    snapLinks: {radius: 75},
    interactive: (cellView: dia.CellView) => {
      const {model} = cellView
      if (!canWrite) {
        return false
      } else if (model instanceof joint.dia.Link) {
        // Disable the default vertex add and label move functionality on pointerdown.
        return {vertexAdd: false, labelMove: false}
        // eslint-disable-next-line i18next/no-literal-string
      } else if (model.get && model.get("backgroundObject")) {
        //Disable moving group rect
        return false
      } else {
        return true
      }
    },
    linkPinning: false,
    defaultLink: makeLink({}),
  }
  return new joint.dia.Paper(options)
}
