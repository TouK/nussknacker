import React, {PropsWithChildren, useEffect, useMemo} from "react"
import {AuthenticationSettings} from "../../reducers/settings"
import {STRATEGIES} from "./strategies"
import {RemoteAuthStrategy} from "./strategies/RemoteAuthStrategy"
import {Strategy} from "./Strategy"

interface Props {
  onChange: (strategy: Strategy) => void,
  authenticationSettings: AuthenticationSettings,
}

export function StrategySelector({authenticationSettings, children, onChange}: PropsWithChildren<Props>): JSX.Element {
  const {backend} = authenticationSettings

  const strategyConstructor = useMemo(() => backend && (STRATEGIES[backend] || RemoteAuthStrategy), [backend])
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
