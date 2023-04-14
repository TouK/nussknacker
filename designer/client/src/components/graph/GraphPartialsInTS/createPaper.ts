import {dia, V} from "jointjs"
import {defaults} from "lodash"
import {defaultLink} from "../EspNode/link"
import {arrowMarker} from "../arrowMarker"

export function createPaper(options: dia.Paper.Options): dia.Paper {
  const uniqueArrowMarker = arrowMarker.clone()
  const paper = new dia.Paper({
    height: "100%",
    width: "100%",
    gridSize: 1,
    clickThreshold: 2,
    async: false,
    snapLinks: {radius: 75},
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
    defaultRouter: {
      name: `manhattan`,
      args: {
        startDirections: [`bottom`],
        endDirections: [`top`],
        excludeTypes: [`basic.Rect`],
        step: 30,
        padding: 20,
      },
    },
    defaultConnector: {
      name: `rounded`,
      args: {
        radius: 60,
      },
    },
    ...options,
  })
  V(paper.defs).append(uniqueArrowMarker)
  return paper
}

