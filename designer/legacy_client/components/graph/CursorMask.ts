import {css} from "@emotion/css"
import {CSSProperties} from "react"

export class CursorMask {
  private static instance: CursorMask
  private readonly cursorMask: HTMLElement

  constructor() {
    this.cursorMask = document.createElement("DIV")
    this.cursorMask.className = css({
      cursor: "not-allowed",
      position: "absolute",
      zIndex: 100000,
      top: 0,
      bottom: 0,
      left: 0,
      right: 0,
    })
  }

  enable(cursor: CSSProperties["cursor"] = "not-allowed"): void {
    this.cursorMask.style.cursor = cursor
    if (!this.cursorMask.parentElement) {
      document.body.appendChild(this.cursorMask)
    }
  }

  disable(): void { this.cursorMask.parentElement?.removeChild(this.cursorMask) }
}
