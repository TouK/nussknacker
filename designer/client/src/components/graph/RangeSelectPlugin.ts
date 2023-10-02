/* eslint-disable i18next/no-literal-string */
import { dia, g, shapes } from "jointjs";
import { CursorMask } from "./CursorMask";
import { Events } from "./types";
import { pressedKeys } from "./KeysObserver";
import { isTouchEvent, LONG_PRESS_TIME } from "../../helpers/detectDevice";

export enum SelectionMode {
    replace = "replace",
    toggle = "toggle",
}

export interface RangeSelectedEventData {
    mode: SelectionMode;
    elements: dia.Element[];
}

export class RangeSelectPlugin {
    private readonly cursorMask = new CursorMask();
    private readonly rectangle = new shapes.standard.Rectangle();
    private readonly pressedKeys = pressedKeys.map((events) => events.map((e) => e.key));
    private keys: string[];
    private selectStart: {
        x: number;
        y: number;
    };
    private getPinchEventActive: () => boolean;

    constructor(private paper: dia.Paper, getPinchEventActive: () => boolean) {
        this.getPinchEventActive = getPinchEventActive;
        this.pressedKeys.onValue(this.onPressedKeysChange);
        paper.on(Events.BLANK_POINTERDOWN, this.onInit.bind(this));
        paper.on(Events.BLANK_POINTERMOVE, this.onChange.bind(this));
        paper.on(Events.BLANK_POINTERUP, this.onExit.bind(this));
        paper.model.once("destroy", this.destroy.bind(this));
    }

    get isActive(): boolean {
        const box = this.rectangle.getBBox();
        return !!(box.width && box.height);
    }

    private get mode(): SelectionMode {
        if (this.keys.includes("Shift")) {
            return SelectionMode.toggle;
        }
        return SelectionMode.replace;
    }

    private onPressedKeysChange = (keys: string[]) => {
        this.keys = keys;
        this.rectangle.attr({
            body:
                this.mode === SelectionMode.toggle
                    ? {
                          fill: "rgba(0,255,85,0.15)",
                          stroke: "#009966",
                      }
                    : {
                          fill: "rgba(0,183,255,0.2)",
                          stroke: "#0066FF",
                      },
        });
    };

    private handleLongPress(action: (event: dia.Event, ...args: unknown[]) => void) {
        let pressTimer;

        const releasePress = () => {
            clearTimeout(pressTimer);
        };

        return (event: dia.Event, ...args: unknown[]) => {
            const paper = this.paper;

            paper.once(Events.BLANK_POINTERMOVE, releasePress);
            paper.once(Events.BLANK_POINTERUP, releasePress);

            pressTimer = window.setTimeout(() => {
                paper.off(Events.BLANK_POINTERMOVE, releasePress);

                if (this.getPinchEventActive()) {
                    return;
                }

                action(event, ...args);
            }, LONG_PRESS_TIME);
        };
    }

    private startSelection(event: dia.Event, x: number, y: number) {
        event.stopImmediatePropagation();
        this.cursorMask.enable("crosshair");
        this.updateRectangleSize(
            {
                x,
                y,
                width: 0,
                height: 0,
            },
            isTouchEvent(event),
        ).addTo(this.paper.model);

        this.selectStart = {
            x,
            y,
        };
    }

    private onInit(event: dia.Event, x: number, y: number) {
        if (isTouchEvent(event)) {
            this.handleLongPress(this.startSelection.bind(this))(event, x, y);
        } else {
            this.hasModifier(event) && this.startSelection(event, x, y);
        }
    }

    private hasModifier(event: dia.Event): boolean {
        return event?.shiftKey || event?.ctrlKey || event?.metaKey;
    }

    private onChange(event: dia.Event, x: number, y: number) {
        if (this.selectStart) {
            const { selectStart } = this;
            const dx = x - selectStart.x;
            const dy = y - selectStart.y;

            event.stopImmediatePropagation();
            event.stopPropagation();

            this.updateRectangleSize(
                {
                    x: selectStart.x,
                    y: selectStart.y,
                    width: dx,
                    height: dy,
                },
                isTouchEvent(event),
            )
                .toFront()
                .attr("body/strokeDasharray", dx < 0 ? "5 5" : null);
        } else {
            this.cleanup();
        }
    }

    private updateRectangleSize(plainRect: g.PlainRect, inflate?: boolean) {
        const rect = new g.Rect(plainRect).normalize();
        const bbox = inflate ? rect.inflate(30, 30) : rect;
        return this.rectangle.position(bbox.x, bbox.y).size(Math.max(bbox.width, 1), Math.max(bbox.height, 1));
    }

    private onExit(event: dia.Event): void {
        if (this.selectStart) {
            const strict = !this.rectangle.attr("body/strokeDasharray");
            const elements = this.paper.model.findModelsInArea(this.rectangle.getBBox(), { strict });
            if (this.isActive) {
                event.stopPropagation();
                const eventData: RangeSelectedEventData = { elements, mode: this.mode };
                this.paper.trigger("rangeSelect:selected", eventData);
            }
        }
        this.cleanup();
    }

    private cleanup(): void {
        this.cursorMask.disable();
        this.rectangle.remove();
        this.selectStart = null;
    }

    private destroy(): void {
        this.cleanup();
        this.pressedKeys.offValue(this.onPressedKeysChange);
    }
}
