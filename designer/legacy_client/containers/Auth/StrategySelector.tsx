import React, {PropsWithChildren, useEffect, useMemo} from "react"
import {AuthenticationSettings} from "../../reducers/settings"
import {STRATEGIES} from "./strategies"
import {Strategy} from "./Strategy"

interface Props {
  onChange: (strategy: Strategy) => void,
  authenticationSettings: AuthenticationSettings,
}

export function StrategySelector({authenticationSettings, children, onChange}: PropsWithChildren<Props>): JSX.Element {
  const {strategy: strategyName} = authenticationSettings;
  const strategyConstructor = useMemo(() => strategyName && STRATEGIES[strategyName], [strategyName])
  const strategy = useMemo(
    () => strategyConstructor && new strategyConstructor(authenticationSettings),
    [strategyConstructor, authenticationSettings],
  )
  const Wrapper = strategy?.Wrapper || React.Fragment

  useEffect(() => onChange(strategy), [strategy, onChange])

  return (
    <Wrapper>{children}</Wrapper>
  )
}
