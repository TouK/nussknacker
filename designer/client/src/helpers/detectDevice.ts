import { dia } from "jointjs";

export function isTouchDevice() {
    return (
        "ontouchstart" in window ||
        navigator.maxTouchPoints > 0 ||
        // eslint-disable-next-line @typescript-eslint/ban-ts-comment
        // @ts-ignore
        navigator.msMaxTouchPoints > 0
    );
}

function getOriginalEvent<E extends Event | dia.Event>(event: E) {
    return ("originalEvent" in event && event?.originalEvent) || event;
}

export function isTouchEvent<E extends Event | dia.Event>(event: E) {
    return "ontouchstart" in window && getOriginalEvent(event) instanceof TouchEvent;
}

export const LONG_PRESS_TIME = 500;

export const isMultiTouchEvent = (e: TouchEvent | dia.Event) => e.touches.length > 1;
