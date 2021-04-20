import React, {PropsWithChildren, useEffect, useState} from "react"
import {useDispatch, useSelector} from "react-redux"
import {reportEvent} from "../../../actions/nk"
import {ReactComponent as FallbackIcon} from "../../../assets/img/toolbarButtons/properties.svg"
import * as LoaderUtils from "../../../common/LoaderUtils"
import {absoluteBePath} from "../../../common/UrlUtils"
import {PlainStyleLink} from "../../../containers/plainStyleLink"
import {getProcessId} from "../../../reducers/selectors/graph"
import ToolbarButton from "../../toolbarComponents/ToolbarButton"

export interface LinkButtonProps {
  name: string,
  href: string, // template z processId
  icon?: string, // url albo default na podstawie typu
  disabled?: boolean,
}

export function LinkButton({href, icon, name, disabled}: LinkButtonProps): JSX.Element {
  const dispatch = useDispatch()
  const processId = useSelector(getProcessId)

  const to = href.replace(/\{\{(\w+)\}\}/g, processId)

  return (
    <PlainStyleLink disabled={disabled} to={to}>
      <ToolbarButton
        name={name || to}
        onClick={() => dispatch(reportEvent({
          category: name,
          action: "custom-link-click",
          name: to,
        }))}
        disabled={disabled}
        icon={<Icon path={icon}><FallbackIcon/></Icon>}
      />
    </PlainStyleLink>
  )
}

function Icon({path, children}: PropsWithChildren<{path?: string}>) {
  const [error, setError] = useState(!path)

  useEffect(() => {
    setError(!path)
  }, [path])

  if (error) {
    return <>{children}</>
  }

  try {
    const svgContent = LoaderUtils.loadSvgContent(path)
    return <div dangerouslySetInnerHTML={{__html: svgContent}}/>
  } catch (e) {
    return <img onError={() => setError(true)} src={absoluteBePath(path)}/>
  }
}
