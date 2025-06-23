import type { CSSProperties } from "react";

export class GlobalCursor {
    private id = "cursor-style-mask";
    private cursorMask: HTMLElement;

    enable(cursor: CSSProperties["cursor"] = "not-allowed"): void {
        this.cursorMask = document.getElementById(this.id) || document.createElement("style");
        this.cursorMask.id = this.id;
        this.cursorMask.innerHTML = `
            * {
                cursor: ${cursor} !important;
            }

            html, body, * {
                user-select: none !important;
                -webkit-user-select: none !important;
            }
        `;
        document.head.appendChild(this.cursorMask);
    }

    disable(): void {
        if (this.cursorMask) {
            this.cursorMask.remove();
        }
    }
}
