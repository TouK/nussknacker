import { CSSProperties } from "react";

export class CursorMask {
    private readonly cursorMask: HTMLElement;

    constructor(element: HTMLElement) {
        this.cursorMask = element;
    }

    enable(cursor: CSSProperties["cursor"] = "not-allowed"): void {
        this.cursorMask.style.cursor = cursor;
    }

    disable(): void {
        this.cursorMask.style.cursor = "initial";
    }
}
