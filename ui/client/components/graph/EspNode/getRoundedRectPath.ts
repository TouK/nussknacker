import {isArray} from "lodash"

function toArray(r: number | number[]) {
  if (isArray(r)) {
    return r
  } else {
    return Array(10).fill(r)
  }
}

export function getRoundedRectPath(size: number | [number, number], r: number | [number, number, number, number]) {
  const [width, height] = toArray(size)
  const [rTopLeft, rTopRight = 0, rBottomRight = rTopLeft, rBottomLeft = rTopRight] = toArray(r)

  return `
      M${width - rTopRight},0 
      q${rTopRight},0 ${rTopRight},${rTopRight}
      v${height - rTopRight - rBottomRight}
      q0,${rBottomRight} ${-rBottomRight},${rBottomRight}
      h${-(width - rBottomRight - rBottomLeft)}
      q${-rBottomLeft},0 ${-rBottomLeft},${-rBottomLeft}
      v${-(height - rBottomLeft - rTopLeft)}
      q0,${-rTopLeft} ${rTopLeft},${-rTopLeft}
      z
  `
}
