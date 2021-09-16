import {dia, V} from "jointjs"
import {defaults} from "lodash"
import {defaultLink} from "../EspNode/link"
import {Events} from "../joint-events"
import {isBackgroundObject} from "./cellUtils"
import {arrowMarker} from "../arrowMarker"

function getPaper(opts: dia.Paper.Options, canEditFrontend: boolean) {
  const uniqueArrowMarker = arrowMarker.clone()
  const paper = new dia.Paper({
    ...opts,
    height: "100%",
    width: "100%",
    gridSize: 1,
    clickThreshold: 2,
    async: false,
    snapLinks: {radius: 75},
    interactive: (cellView: dia.CellView) => {
      const {model} = cellView
      if (!canEditFrontend) {
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
    defaultLink: defaultLink(uniqueArrowMarker.attr("id")),
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
  V(paper.defs).append(uniqueArrowMarker)
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
  const canEditFrontend = this.props.loggedUser.canEditFrontend(this.props.processCategory) && !this.props.readonly
  const paper = getPaper(
    {
      async: true,
      model: this.graph,
      el: this.getEspGraphRef(),
      validateConnection: this.validateConnection,
    },
    canEditFrontend,
  )

  return paper
    //we want to inject node during 'Drag and Drop' from toolbox
    .on(Events.CELL_POINTERUP, (cell: dia.CellView) => {
      this.changeLayoutIfNeeded()
      this.handleInjectBetweenNodes(cell.model)
    })
    .on(Events.LINK_CONNECT, (cell) => {
      this.props.actions.nodesConnected(
        cell.sourceView.model.attributes.nodeData,
        cell.targetView.model.attributes.nodeData,
      )
    })
    .on(Events.LINK_DISCONNECT, (cell) => {
      this.disconnectPreviousEdge(cell.model.attributes.edgeData.from, cell.model.attributes.edgeData.to)
    })
}
