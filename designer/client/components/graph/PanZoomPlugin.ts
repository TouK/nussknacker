import {css} from "@emotion/css"
import {dia, g} from "jointjs"
import {debounce, throttle} from "lodash"
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

  constructor(private paper: dia.Paper) {
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

    //appear animation starting point, fitSmallAndLargeGraphs will set animation end point in componentDidMount
    this.instance.zoom(0.001)

    this.animationClassHolder = paper.el
    this.animationClassHolder.addEventListener("transitionend", debounce(() => {
      this.setAnimationClass({enabled: false})
    }, 500))

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
    if (window["Cypress"]) {
      return
    }
    this.animationClassHolder.classList.remove(getAnimationClass(enabled))
    this.animationClassHolder.classList.add(getAnimationClass(!enabled))
  }

  get zoom(): number {
    return this.instance.getZoom()
  }

  private cleanup(event: Event) {
    delete event.data?.panStart
    this.cursorMask.disable()
  }

  fitSmallAndLargeGraphs = debounce((): void => {
    this.setAnimationClass({enabled: true})
    this.instance.zoom(1)
    this.instance.resize()
    this.instance.updateBBox()
    this.instance.fit()
    const {realZoom} = this.instance.getSizes()
    const toZoomBy = realZoom > 1.2 ? 1 / realZoom : 0.78  //the bigger zoom, the further we get
    this.instance.zoomBy(toZoomBy)
    this.instance.center()
  }, 100)

  zoomIn = throttle((): void => {
    this.setAnimationClass({enabled: true})
    this.instance.zoomIn()
  }, 500)

  zoomOut = throttle((): void => {
    this.setAnimationClass({enabled: true})
    this.instance.zoomOut()
  }, 500)

  private pan = throttle((point: { x: number, y: number }) => {
    requestAnimationFrame(() => {
      this.instance.panBy(point)
    })
  }, 16 /*~60 pleasant fps*/)

  panToCells = (cells: dia.Cell[], viewport: g.Rect = new g.Rect(this.paper.el.getBoundingClientRect())) => {
    const localViewport = this.paper.clientToLocalRect(viewport)
    const cellsBBox = this.paper.model.getCellsBBox(cells).inflate(10, 50)

    if (localViewport.containsRect(cellsBBox)) {
      return
    }

    const [top, right, bottom, left] = [
      -localViewport.topLine().pointOffset(cellsBBox.topMiddle()),
      -localViewport.rightLine().pointOffset(cellsBBox.rightMiddle()),
      localViewport.bottomLine().pointOffset(cellsBBox.bottomMiddle()),
      localViewport.leftLine().pointOffset(cellsBBox.leftMiddle()),
    ].map(offset => Math.min(20, Math.max(0, offset) / 20))

    const panOffset = {
      y: top - bottom,
      x: left - right,
    }

    this.pan(panOffset)
    requestAnimationFrame(() => {
      this.panToCells(cells, viewport)
    })
  }
}

