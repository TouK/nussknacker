/* eslint-disable i18next/no-literal-string */
import { dia, g, shapes } from "jointjs";
import { GlobalCursor } from "./GlobalCursor";
import { pressedKeys } from "./KeysObserver";
import { isTouchEvent } from "../../helpers/detectDevice";
import { pointers, select, Selection } from "d3-selection";
import { alpha, Theme } from "@mui/material";

export enum SelectionMode {
    replace = "replace",
    toggle = "toggle",
}

export enum RangeSelectEvents {
    START = "rangeSelect:start",
    STOP = "rangeSelect:stop",
    RESET = "rangeSelect:reset",
    SELECTED = "rangeSelect:selected",
}

export interface RangeSelectedEventData {
    mode: SelectionMode;
    elements: dia.Element[];
}

export interface RangeSelectStartEventData {
    x: number;
    y: number;
    fingerSize: number;
    source: "pointerWithModifier" | "pointer";
}

function getFingerSize(event: Event) {
    return isTouchEvent(event) ? 40 : 0;
}

export class RangeSelectPlugin {
    private readonly globalCursor = new GlobalCursor();
    private readonly rectangle = new shapes.standard.Rectangle();
    private readonly pressedKeys = pressedKeys.map((events) => events.map((e) => e.key));
    private keys: string[];
    private selectStart: g.PlainPoint | null;

    constructor(private paper: dia.Paper, private readonly theme: Theme) {
        this.element.on("pointerdown.range", this.onStart.bind(this));
        this.body.on("pointermove.range", this.onRangeChange.bind(this)).on("pointerup.range", this.onEndSelection.bind(this));
        this.svg.on("touchmove.range", this.onAbortByMultitouch.bind(this), { passive: true });

        this.pressedKeys.onValue(this.onPressedKeysChange);

        paper.on(RangeSelectEvents.START, this.startSelection.bind(this));

        paper.model.once("destroy", this.destroy.bind(this));
    }

    private get body(): Selection<HTMLElement, unknown, null, undefined> {
        return select(document.body);
    }

    private get element(): Selection<HTMLElement, unknown, null, undefined> {
        return select(this.paper.el);
    }

    private get svg(): Selection<SVGSVGElement, unknown, null, undefined> {
        return select(this.paper.svg);
    }

    private onRangeChange(event: PointerEvent) {
        if (!this.selectStart) {
            return this.cleanup();
        }

        const selectStart = this.getLocalPoint(this.selectStart);
        const selectEnd = this.getLocalPoint({
            x: event.clientX,
            y: event.clientY,
        });
        const dx = selectEnd.x - selectStart.x;
        const dy = selectEnd.y - selectStart.y;

        this.updateRectangleSize(
            {
                x: selectStart.x,
                y: selectStart.y,
                width: dx,
                height: dy,
            },
            getFingerSize(event),
        )
            .toFront()
            .attr("body/strokeDasharray", dx < 0 ? "5 5" : null);
    }

    private resetTimeout: number;
    private cleanupTimeout: number;
    private onStart(event: PointerEvent) {
        if (!event.isPrimary) return;
        if (!this.isBlank(event)) return;
        if (this.selectStart) return;

        window.clearTimeout(this.resetTimeout);
        window.clearTimeout(this.cleanupTimeout);

        if (this.hasModifier(event)) {
            const data: RangeSelectStartEventData = {
                source: "pointerWithModifier",
                fingerSize: getFingerSize(event),
                x: event.clientX,
                y: event.clientY,
            };
            return this.paper.trigger(RangeSelectEvents.START, data);
        }

        const cleanupEvents = () => {
            window.clearTimeout(this.resetTimeout);
            window.clearTimeout(this.cleanupTimeout);
            this.svg.on(".range-started", null);
        };

        this.cleanupTimeout = window.setTimeout(() => {
            cleanupEvents();
        }, 500);

        const firstEventPoint = new g.Point(event.clientX, event.clientY);

        this.svg
            .on("pointerup.range-started", (e) => {
                this.resetTimeout = window.setTimeout(() => {
                    this.paper.trigger(RangeSelectEvents.RESET);
                    cleanupEvents();
                }, 200);
            })
            .on("pointermove.range-started", (e: PointerEvent) => {
                const distance = firstEventPoint.distance({
                    x: e.clientX,
                    y: e.clientY,
                });
                if (distance < 10) return;

                cleanupEvents();
            })
            .on("pointerdown.range-started", (e: PointerEvent) => {
                cleanupEvents();

                if (!e.isPrimary) return;
                if (!this.isBlank(e)) return;

                const distance = firstEventPoint.distance({
                    x: e.clientX,
                    y: e.clientY,
                });
                if (distance > 60) return;

                const data: RangeSelectStartEventData = {
                    source: "pointer",
                    fingerSize: getFingerSize(event),
                    x: event.clientX,
                    y: event.clientY,
                };
                this.paper.trigger(RangeSelectEvents.START, data);
            });
    }

    private onEndSelection() {
        if (!this.selectStart) return;

        const strict = !this.rectangle.attr("body/strokeDasharray");
        const box = this.rectangle.getBBox();
        const elements = this.paper.model.findModelsInArea(box, { strict }).filter((e) => e !== this.rectangle);
        if (box.width && box.height) {
            const eventData: RangeSelectedEventData = {
                elements,
                mode: this.mode,
            };
            this.paper.trigger(RangeSelectEvents.SELECTED, eventData);
        }
        this.cleanup();
    }

    private onAbortByMultitouch(event: TouchEvent) {
        if (pointers(event).length > 1) {
            this.cleanup();
        }
    }

    private isBlank(event: Event) {
        return event.target === this.paper.svg;
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
                          fill: alpha(this.theme.palette.success.main, 0.2),
                          stroke: this.theme.palette.success.main,
                      }
                    : {
                          fill: alpha(this.theme.palette.primary.main, 0.2),
                          stroke: this.theme.palette.primary.main,
                      },
        });
    };

    private startSelection({ x, y, fingerSize }: RangeSelectStartEventData) {
        this.globalCursor.enable("crosshair");

        this.selectStart = { x, y };

        this.updateRectangleSize(
            {
                ...this.getLocalPoint(this.selectStart),
                width: 0,
                height: 0,
            },
            fingerSize,
        ).addTo(this.paper.model);
    }

    private getLocalPoint(point: g.PlainPoint): g.Point {
        return this.paper.pageToLocalPoint(point).round();
    }

    private hasModifier(event: MouseEvent): boolean {
        return event?.shiftKey || event?.ctrlKey || event?.metaKey;
    }

    private updateRectangleSize(plainRect: g.PlainRect, fingerSize?: number) {
        const rect = new g.Rect(plainRect).normalize();
        const bbox = this.inflateInClientScale(rect, fingerSize, fingerSize);
        return this.rectangle.position(bbox.x, bbox.y).size(Math.max(bbox.width, 1), Math.max(bbox.height, 1));
    }

    private inflateInClientScale(rect: g.Rect, dx: number, dy: number) {
        if (!dx && !dy) {
            return rect;
        }
        return this.paper.clientToLocalRect(this.paper.localToClientRect(rect).inflate(dx, dy));
    }

    private cleanup(): void {
        if (this.selectStart) {
            this.paper.trigger(RangeSelectEvents.STOP);
            this.selectStart = null;
        }
        this.globalCursor.disable();
        this.rectangle.remove();
    }

    private destroy(): void {
        this.cleanup();
        this.element.on(".range", null);
        this.body.on(".range", null);
        this.pressedKeys.offValue(this.onPressedKeysChange);
    }
}
