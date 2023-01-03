import React from "react"
import "../stylesheets/visualization.styl"
import styled from "@emotion/styled"

export const Page = styled.div({
  position: "relative",
  overflow: "hidden",
  height: "100%",
  display: "flex",
  flexDirection: "column",
})

export const GraphPage = styled(Page)({
  backgroundColor: "#b3b3b3",
  zIndex: 1,
})
