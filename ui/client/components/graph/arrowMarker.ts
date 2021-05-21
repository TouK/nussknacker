/* eslint-disable i18next/no-literal-string */
import * as joint from "jointjs"
import arrow from "!raw-loader!./arrow.svg"

export const arrowMarker = joint.V("marker", {
  viewBox: "0 0 10 10",
  refX: 8,
  refY: 5,
  markerWidth: 6,
  markerHeight: 20,
  orient: "auto",
  class: "arrow-marker",
}, joint.V(arrow).children())
