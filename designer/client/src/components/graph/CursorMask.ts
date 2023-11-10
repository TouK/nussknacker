import { CSSProperties } from "react";

export class CursorMask {
    private cursorMask: HTMLElement;

    enable(cursor: CSSProperties["cursor"] = "not-allowed"): void {
        this.cursorMask = document.createElement("style");
        this.cursorMask.innerHTML = `*{cursor: ${cursor}!important;}`;
        this.cursorMask.id = "cursor-style";
        document.head.appendChild(this.cursorMask);
    }

    disable(): void {
        if (this.cursorMask) {
            this.cursorMask.remove();
        }
    }
}
