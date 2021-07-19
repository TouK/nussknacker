import {dia, V} from "jointjs"
import {isBackgroundObject} from "./cellUtils"
import {defaults} from "lodash"
import {defaultLink} from "../EspNode/link"
import {arrowMarker} from "../arrowMarker"
import {Events} from "../joint-events"

function getPaper(opts: dia.Paper.Options, canWrite: boolean) {
  const paper = new dia.Paper({
    ...opts,
    gridSize: 1,
    clickThreshold: 2,
    async: false,
    snapLinks: {radius: 75},
    interactive: (cellView: dia.CellView) => {
      const {model} = cellView
      if (!canWrite) {
        return false
      } else if (model instanceof dia.Link) {
        // Disable the default vertex add and label move functionality on pointerdown.
        return {vertexAdd: false, labelMove: false}
      } else if (isBackgroundObject(model)) {
        //Disable moving group rect
        return false
      } else {
        return true
      }
    },
    linkPinning: false,
    defaultLink: defaultLink,
    linkView: dia.LinkView.extend({
      options: defaults<dia.LinkView.Options, dia.LinkView.Options>({
        shortLinkLength: 60,
        longLinkLength: 180,
        linkToolsOffset: 20,
        doubleLinkToolsOffset: 20,
        doubleLinkTools: true,
      }, dia.LinkView.prototype.options),
    }),
  })
  V(paper.defs).append(arrowMarker)
  paper.options.defaultRouter = {
    name: `manhattan`,
    args: {
      startDirections: [`bottom`],
      endDirections: [`top`],
      excludeTypes: [`basic.Rect`],
      step: 10,
      padding: 20,
    },
  }
  paper.options.defaultConnector = {
    name: `rounded`,
    args: {
      radius: 60,
    },
  }
  return paper
}

export function createPaper(): dia.Paper {
  const canWrite = this.props.loggedUser.canWrite(this.props.processCategory) && !this.props.readonly
  const {height = "100%", width = "100%"} = this.props
  const paper = getPaper(
    {
      async: true,
      height,
      width,
      model: this.graph,
      el: this.getEspGraphRef(),
      validateConnection: this.validateConnection,
    },
    canWrite,
  )

  return paper
    .on(Events.CELL_POINTERUP, (cell: dia.CellView) => {
      this.changeLayoutIfNeeded()
      this.handleInjectBetweenNodes(cell.model)
    })
    .on(Events.LINK_CONNECT, (cell) => {
      this.disconnectPreviousEdge(cell.model.id)
      this.props.actions.nodesConnected(
        cell.sourceView.model.attributes.nodeData,
        cell.targetView.model.attributes.nodeData,
      )
    })
}
