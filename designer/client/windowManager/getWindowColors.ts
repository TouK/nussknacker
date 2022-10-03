import {css} from "@emotion/css"
import {WindowKind} from "./WindowKind"

export function getWindowColors(type = WindowKind.default): string {
  switch (type) {
    case WindowKind.calculateCounts:
    case WindowKind.compareVersions:
      return css({backgroundColor: "#1ba1af", color: "white"})
    case WindowKind.customAction:
      return css({backgroundColor: "white", color: "black"})
    case WindowKind.addProcess:
    case WindowKind.addSubProcess:
    case WindowKind.default:
    default:
      return css({backgroundColor: "#2D8E54", color: "white"})
  }
}
