import {css} from "emotion"
import {dia} from "jointjs"
import {debounce} from "lodash"
import svgPanZoom from "svg-pan-zoom"
import {CursorMask} from "./CursorMask"
import {Events} from "./joint-events"

type EventData = {panStart?: {x: number, y: number, touched?: boolean}}
type Event = JQuery.MouseEventBase<any, EventData>

const getAnimationClass = (disabled?: boolean) => css({
  ".svg-pan-zoom_viewport": {
    transition: disabled ? "none" : `transform .5s cubic-bezier(0.86, 0, 0.07, 1)`,
  },
})

export class PanZoomPlugin {
  private cursorMask: CursorMask
  private instance: SvgPanZoom.Instance
  private animationClassHolder: HTMLElement

  constructor(paper: dia.Paper) {
    this.cursorMask = new CursorMask()
    this.instance = svgPanZoom(paper.svg, {
      fit: false,
      contain: false,
      zoomScaleSensitivity: 0.4,
      controlIconsEnabled: false,
      panEnabled: false,
      dblClickZoomEnabled: false,
      minZoom: 0.005,
      maxZoom: 500,
    })

    this.animationClassHolder = document.querySelector(".svg-pan-zoom_viewport").parentElement
    this.animationClassHolder.addEventListener("transitionend", () => {
      this.setAnimationClass({enabled: true})
    })

    paper.on(Events.BLANK_POINTERDOWN, (event: Event, x, y) => {
      const isModified = event.shiftKey || event.ctrlKey || event.altKey || event.metaKey
      if (!isModified) {
        this.cursorMask.enable("move")
        event.data = {...event.data, panStart: {x, y}}
      }
    })

    paper.on(Events.BLANK_POINTERMOVE, (event: Event, eventX, eventY) => {
      const isModified = event.shiftKey || event.ctrlKey || event.altKey || event.metaKey
      const panStart = event.data?.panStart
      if (!isModified && panStart) {
        const zoom = this.instance.getZoom()
        const dx = (eventX - panStart.x) * zoom
        const dy = (eventY - panStart.y) * zoom
        const {movementX: x = dx, movementY: y = dy} = event.originalEvent
        this.instance.panBy({x, y})
        panStart.touched = true
      } else {
        this.cleanup(event)
      }
    })

    paper.on(Events.BLANK_POINTERUP, (event: Event) => {
      if (event.data?.panStart?.touched) {
        event.stopImmediatePropagation()
      }
      this.cleanup(event)
    })
  }

  private setAnimationClass({enabled}: {enabled: boolean}) {
    this.animationClassHolder.classList.add(getAnimationClass(enabled))
    this.animationClassHolder.classList.remove(getAnimationClass(!enabled))
  }

  get zoom(): number {
    return this.instance.getZoom()
  }

  private cleanup(event: Event) {
    delete event.data?.panStart
    this.cursorMask.disable()
  }

  fitSmallAndLargeGraphs = debounce((): void => {
    this.setAnimationClass({enabled: false})
    this.instance.zoom(1)
    this.instance.resize()
    this.instance.updateBBox()
    this.instance.fit()
    const {realZoom} = this.instance.getSizes()
    const toZoomBy = realZoom > 1.2 ? 1 / realZoom : 0.8 //the bigger zoom, the further we get
    this.instance.zoomBy(toZoomBy)
    this.instance.center()
  }, 200)

  zoomIn = () => {
    this.instance.zoomIn()
  }

  zoomOut = () => {
    this.instance.zoomOut()
  }
}
