/* eslint-disable i18next/no-literal-string */
import React, {useMemo} from "react"
import {useUserSettings} from "../../../../../common/userSettings"
import AceWrapper, {AceWrapperProps} from "./AceWrapper"

export default function AceWithFeatureFlags(props: Omit<AceWrapperProps, "noWrap" | "showLines">): JSX.Element {
  const [userSettings, toggleSettings] = useUserSettings()

  const [showLinesName, noWrapName] = useMemo(
    () => ["showLines", "noWrap"].map(name => `${props.inputProps.language}.${name}`),
    [props],
  )

  const commands = useMemo(() => [
    {
      name: "showLines",
      bindKey: {win: "F1", mac: "F1"},
      exec: () => toggleSettings([showLinesName]),
      readonly: true,
    },
    {
      name: "noWrap",
      bindKey: {win: "F2", mac: "F2"},
      exec: () => toggleSettings([noWrapName]),
      readonly: true,
    },
  ], [toggleSettings, showLinesName, noWrapName])

  return (
    <AceWrapper
      {...props}
      commands={commands}
      showLineNumbers={userSettings[showLinesName]}
      wrapEnabled={!userSettings[noWrapName]}
    />
  )
}
