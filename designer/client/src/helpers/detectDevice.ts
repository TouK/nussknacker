import { dia } from "jointjs";
import { eventFrom } from "event-from";

export function isTouchDevice() {
    return (
        "ontouchstart" in window ||
        navigator.maxTouchPoints > 0 ||
        // eslint-disable-next-line @typescript-eslint/ban-ts-comment
        // @ts-ignore
        navigator.msMaxTouchPoints > 0
    );
}

export function isTouchEvent<E extends Event | dia.Event>(event: E) {
    return eventFrom(event) === "touch";
}

export const LONG_PRESS_TIME = 500;
