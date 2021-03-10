/* eslint-disable i18next/no-literal-string */
import React, {useCallback, useMemo} from "react"
import {useFFlags} from "../../../../../common/FeatureFlagsUtils"
import AceWrapper, {AceWrapperProps} from "./AceWrapper"

export default function AceWithFeatureFlags(props: Omit<AceWrapperProps, "noWrap" | "showLines">): JSX.Element {
  const [featureFlags, toggleFeatureFlags] = useFFlags()

  const getFlagName = useCallback((flag: string) => `${props.inputProps.language}.${flag}`, [props])

  const [showLines, noWrap] = useMemo(
    () => [
      `showLines`,
      `noWrap`,
    ].map(flag => !!featureFlags[getFlagName(flag)]),
    [featureFlags, getFlagName],
  )

  const commands = useMemo(() => [
    {
      name: "showLines",
      bindKey: {win: "F1", mac: "F1"},
      exec: () => toggleFeatureFlags([getFlagName(`showLines`)]),
    },
    {
      name: "noWrap",
      bindKey: {win: "F2", mac: "F2"},
      exec: () => toggleFeatureFlags([getFlagName(`noWrap`)]),
    },
  ], [toggleFeatureFlags, getFlagName])

  return (
    <AceWrapper
      {...props}
      commands={commands}
      showLineNumbers={showLines}
      wrapEnabled={!noWrap}
    />
  )
}
