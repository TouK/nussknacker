import { ensureArray } from "../../../common/arrayUtils";

export function getRoundedRectPath(
    size: number | [number, number],
    radius: number | [number, number, number, number], //css-like value,
    marginTop: number,
) {
    const [width, height] = ensureArray(size, 2);
    const [rTopLeft, rTopRight = 0, rBottomRight = rTopLeft, rBottomLeft = rTopRight] = ensureArray(radius, 4);

    return `
      M${marginTop - rTopLeft} ${marginTop}
      a${rTopLeft} ${rTopLeft} 0 0 1 ${rTopLeft}-${rTopLeft}
      h${width - marginTop * 2}
      a${rTopRight} ${rTopRight} 0 0 1 ${rTopRight} ${rTopRight}
      v${height - marginTop * 2}
      a${rBottomRight} ${rBottomRight} 0 0 1-${rBottomRight} ${rBottomRight}
      H${marginTop}
      a${rBottomLeft} ${rBottomLeft} 0 0 1-${rBottomLeft}-${rBottomLeft}
      V${marginTop}
      z
  `;
}
