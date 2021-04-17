import {css} from "emotion"
import {dia} from "jointjs"
import svgPanZoom from "svg-pan-zoom"
import {Events} from "./joint-events"

type EventData = {panStart?: {x: number, y: number}}
type Event = JQuery.TriggeredEvent<any, EventData>

class CursorMask {
  enable = () => document.body.appendChild(this.cursorMask)

  disable = () => this.cursorMask.parentElement?.removeChild(this.cursorMask)

  private init = (): HTMLElement => {
    const cursorMask = document.createElement("DIV")
    cursorMask.className = css({
      cursor: "move",
      position: "absolute",
      zIndex: 100000,
      top: 0,
      bottom: 0,
      left: 0,
      right: 0,
    })
    return cursorMask
  }

  private cursorMask = this.init()
}

export class PanZoomPlugin {
  private cursorMask: CursorMask
  private instance: SvgPanZoom.Instance

  constructor(paper: dia.Paper) {
    this.cursorMask = new CursorMask()
    this.instance = svgPanZoom(paper.svg, {
      viewportSelector: ".svg-pan-zoom_viewport",
      fit: false,
      contain: false,
      zoomScaleSensitivity: 0.4,
      controlIconsEnabled: false,
      panEnabled: false,
      dblClickZoomEnabled: false,
      minZoom: 0.05,
      maxZoom: 5,
    })

    paper.on(Events.BLANK_POINTERDOWN, (event: Event, x, y) => {
      const isModified = event.shiftKey || event.ctrlKey || event.altKey || event.metaKey
      if (!isModified) {
        this.cursorMask.enable()
        event.data = {...event.data, panStart: {x, y}}
      }
    })

    paper.on(Events.BLANK_POINTERMOVE, (event: Event, x, y) => {
      const isModified = event.shiftKey || event.ctrlKey || event.altKey || event.metaKey
      const panStart = event.data?.panStart
      if (!isModified && panStart) {
        const dx = x - panStart.x
        const dy = y - panStart.y
        const zoom = this.instance.getZoom()
        this.instance.panBy({x: dx * zoom, y: dy * zoom})
      } else {
        this.cleanup(event)
      }
    })

    paper.on(Events.BLANK_POINTERUP, (event: Event) => {
      this.cleanup(event)
    })
  }

  private cleanup(event: Event) {
    delete event.data?.panStart
    this.cursorMask.disable()
  }

  fitSmallAndLargeGraphs = (): void => {
    this.instance.updateBBox()
    this.instance.fit()
    const {realZoom} = this.instance.getSizes()
    const toZoomBy = realZoom > 1.2 ? 1 / realZoom : 0.8 //the bigger zoom, the further we get
    this.instance.zoomBy(toZoomBy)
    this.instance.center()
  }

  zoomIn = () => {
    this.instance.zoomIn()
  }

  zoomOut = () => {
    this.instance.zoomOut()
  }
}
