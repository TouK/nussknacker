import {ensureArray} from "../../../common/arrayUtils"

export function getRoundedRectPath(
  size: number | [number, number],
  radius: number | [number, number, number, number] //css-like value
) {
  const [width, height] = ensureArray(size, 2)
  const [rTopLeft, rTopRight = 0, rBottomRight = rTopLeft, rBottomLeft = rTopRight] = ensureArray(radius, 4)

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
