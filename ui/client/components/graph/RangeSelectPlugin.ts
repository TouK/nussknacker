/* eslint-disable i18next/no-literal-string */
import {dia, g, shapes} from "jointjs"
import {CursorMask} from "./CursorMask"
import {Events} from "./joint-events"

type EventData = {selectStart?: {x: number, y: number}, rect?: shapes.standard.Rectangle}
type Event = JQuery.TriggeredEvent<any, EventData>

export enum SelectionMode {
  replace = "replace",
  toggle = "toggle",
}

export class RangeSelectPlugin {
  private cursorMask: CursorMask
  private model: dia.Graph

  constructor(private paper: dia.Paper) {
    this.model = paper.model
    this.cursorMask = new CursorMask()

    paper.on(Events.BLANK_POINTERDOWN, this.onInit.bind(this))
    paper.on(Events.BLANK_POINTERMOVE, this.onChange.bind(this))
    paper.on(Events.BLANK_POINTERUP, this.onExit.bind(this))
  }

  private onSelect(elements: dia.Element[], mode: SelectionMode) {
    this.paper.trigger("rangeSelect:selected", {elements, mode})
  }

  private onInit(event: Event, x, y) {
    if (event.shiftKey || event.ctrlKey || event.metaKey) {
      event.stopImmediatePropagation()
      this.cursorMask.enable("crosshair")
      const rect = new shapes.standard.Rectangle()
      rect.position(x, y)
      rect.attr({
        body: event.shiftKey ?
          {
            fill: "rgba(0,255,85,0.15)",
            stroke: "#009966",
          } :
          {
            fill: "rgba(0,183,255,0.2)",
            stroke: "#0066FF",
          },
      })
      rect.addTo(this.model)
      event.data = {...event.data, rect, selectStart: {x, y}}
    }
  }

  private onChange(event: Event, x, y) {
    const {selectStart, rect} = event.data
    if ((event.shiftKey || event.ctrlKey || event.metaKey) && selectStart && rect) {
      const dx = x - selectStart.x
      const dy = y - selectStart.y
      const bbox = new g.Rect(selectStart.x, selectStart.y, dx, dy)
      bbox.normalize()
      rect.set({
        position: {x: bbox.x, y: bbox.y},
        size: {width: Math.max(bbox.width, 1), height: Math.max(bbox.height, 1)},
      })
      rect.attr("body/strokeDasharray", dx < 0 ? "5 5" : null)
    } else {
      this.cleanup(event.data)
    }
  }

  private onExit(event: Event) {
    this.handleSelection(event)
    this.cleanup(event.data)
  }

  private cleanup(data: EventData): void {
    this.cursorMask.disable()
    if (data?.rect) {
      data.rect.remove()
      delete data.selectStart
      delete data.rect
    }
  }

  private handleSelection({data, shiftKey}: Event): void {
    if (data?.rect && this.onSelect.bind(this)) {
      const strict = !data.rect.attr("body/strokeDasharray")
      const elements = this.model.findModelsInArea(data.rect.getBBox(), {strict})
      this.onSelect(elements, shiftKey ? SelectionMode.toggle : SelectionMode.replace)
    }
  }
}
