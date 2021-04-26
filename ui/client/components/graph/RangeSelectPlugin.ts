/* eslint-disable i18next/no-literal-string */
import {dia, g, shapes} from "jointjs"
import {CursorMask} from "./CursorMask"
import {Events} from "./joint-events"
import {pressedKeys} from "./KeysObserver"

type EventData = {selectStart?: {x: number, y: number}}
type Event = JQuery.TriggeredEvent<any, EventData>

export enum SelectionMode {
  replace = "replace",
  toggle = "toggle",
}

export class RangeSelectPlugin {
  private readonly cursorMask = new CursorMask()
  private readonly rectangle = new shapes.standard.Rectangle()
  private readonly pressedKeys = pressedKeys.map(events => events.map(e => e.key))
  private keys: string[]

  constructor(private paper: dia.Paper) {
    this.pressedKeys.onValue(this.onPressedKeysChange)
    paper.on(Events.BLANK_POINTERDOWN, this.onInit.bind(this))
    paper.on(Events.BLANK_POINTERMOVE, this.onChange.bind(this))
    paper.on(Events.BLANK_POINTERUP, this.onExit.bind(this))
    paper.model.once("destroy", this.destroy.bind(this))
  }

  private get mode(): SelectionMode {
    if (this.keys.includes("Shift")) {
      return SelectionMode.toggle
    }
    return SelectionMode.replace

  }

  private onPressedKeysChange = (keys: string[]) => {
    this.keys = keys
    this.rectangle.attr({
      body: this.mode === SelectionMode.toggle ?
        {
          fill: "rgba(0,255,85,0.15)",
          stroke: "#009966",
        } :
        {
          fill: "rgba(0,183,255,0.2)",
          stroke: "#0066FF",
        },
    })
  }

  private onInit(event: Event, x, y) {
    if (this.hasModifier(event)) {
      event.stopImmediatePropagation()
      this.cursorMask.enable("crosshair")
      this.rectangle
        .position(x, y)
        .size(0, 0)
        .addTo(this.paper.model)

      event.data = {...event.data, selectStart: {x, y}}
    }
  }

  private hasModifier(event: JQuery.TriggeredEvent<any, EventData>): boolean {
    return event?.shiftKey || event?.ctrlKey || event?.metaKey
  }

  private onChange(event: Event, x, y) {
    if (this.hasModifier(event) && event.data?.selectStart) {
      const {selectStart} = event.data
      const dx = x - selectStart.x
      const dy = y - selectStart.y

      const bbox = new g.Rect(selectStart.x, selectStart.y, dx, dy)
        .normalize()

      this.rectangle
        .position(bbox.x, bbox.y)
        .size(Math.max(bbox.width, 1), Math.max(bbox.height, 1))
        .toFront()
        .attr("body/strokeDasharray", dx < 0 ? "5 5" : null)
    } else {
      this.cleanup(event.data)
    }
  }

  private onExit({data}: Event): void {
    if (data?.selectStart) {
      const strict = !this.rectangle.attr("body/strokeDasharray")
      const elements = this.paper.model.findModelsInArea(this.rectangle.getBBox(), {strict})
      this.paper.trigger("rangeSelect:selected", {elements, mode: this.mode})
    }
    this.cleanup(data)
  }

  private cleanup(data?: EventData): void {
    this.cursorMask.disable()
    this.rectangle.remove()
    delete data?.selectStart
  }

  private destroy(): void {
    this.cleanup()
    this.pressedKeys.offValue(this.onPressedKeysChange)
  }
}
