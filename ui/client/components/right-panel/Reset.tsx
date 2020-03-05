import {useDispatch} from "react-redux"
import SvgDiv from "../SvgDiv"
import {resetToolbars} from "../../actions/nk/toolbars"
import React from "react"

export function Reset() {
  const dispatch = useDispatch()
  return <SvgDiv className="zoom" title={"reset"} svgFile="buttons/zoomin.svg" onClick={() => dispatch(resetToolbars())}/>
}
