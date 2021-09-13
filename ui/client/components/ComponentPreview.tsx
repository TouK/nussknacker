import {useToolIcon} from "./toolbars/creator/Tool"
import {css, cx} from "emotion"
import {BORDER_RADIUS, CONTENT_COLOR, CONTENT_PADDING, RECT_HEIGHT, RECT_WIDTH} from "./graph/EspNode/esp"
import customAttrs from "../assets/json/nodeAttributes.json"
import React from "react"
import {NodeType} from "../types"

export function ComponentPreview({node, isActive, isOver}: { node: NodeType, isActive?: boolean, isOver?: boolean }) {
  const icon = useToolIcon(node)
  const nodeStyles = css({
    width: RECT_WIDTH,
    height: RECT_HEIGHT,
    borderRadius: BORDER_RADIUS,
    overflow: "hidden",
    boxSizing: "content-box",
    display: "inline-flex",
    boxShadow: "0 5px 20px rgba(0,0,0,.5)",
    borderWidth: 2,
    borderStyle:"solid",
    transform: `translate(-80%, -50%) rotate(${isActive ? -2 : 0}deg) scale(${isActive ? 1 : 1.5})`,
    opacity: isActive ? undefined : 0,
    transition: "all .5s, opacity .3s",
    willChange: "transform, opacity, border-color, background-color",
  })

  const nodeColors = css({
    opacity: .5,
    borderColor: "hsl(110,0%,20%)",
    backgroundColor: "hsl(110,0%,100%)",
  })
  const nodeColorsHover = css({
    borderColor: "hsl(110,100%,33%)",
    backgroundColor: "hsl(110,100%,95%)",
  })

  const imageStyles = css({
    padding: 15,
    "> img": {
      height: 30,
      width: 30,
    },
  })

  const imageColors = css({
    background: customAttrs[node?.type]?.styles.fill,
  })

  const contentStyles = css({
    color: CONTENT_COLOR,
    flex: 1,
    whiteSpace: "nowrap",
    display: "flex",
    alignItems: "center",
    overflow: "hidden",
    span: {
      margin: CONTENT_PADDING,
      flex: 1,
      textOverflow: "ellipsis",
      overflow: "hidden",
    },
  })
  return (
    <div className={cx(isOver ? nodeColorsHover : nodeColors, nodeStyles)}>
      <div className={cx(imageStyles, imageColors)}>
        <img src={icon}/>
      </div>
      <div className={contentStyles}>
        <span>{node?.id}</span>
      </div>
    </div>
  )
}
