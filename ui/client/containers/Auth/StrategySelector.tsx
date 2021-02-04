import React, {PropsWithChildren, useEffect, useMemo} from "react"
import {useSelector} from "react-redux"
import {RootState} from "../../reducers"
import {AuthenticationSettings} from "../../reducers/settings"
import {STRATEGIES} from "./strategies"
import {Strategy, StrategyConstructor} from "./Strategy"

export function StrategySelector({children, onChange, fallback = STRATEGIES.fallback}: PropsWithChildren<{
  onChange: (strategy: Strategy) => void,
  fallback?: StrategyConstructor,
}>): JSX.Element {
  const authenticationSettings = useSelector<RootState, AuthenticationSettings>(state => state.settings.authenticationSettings)
  const {backend} = authenticationSettings

  const strategyConstructor = useMemo(() => STRATEGIES[backend] || fallback, [backend, fallback])
  const strategy = useMemo(() => new strategyConstructor(authenticationSettings), [strategyConstructor, authenticationSettings])
  const {Wrapper = React.Fragment} = strategy

  useEffect(() => onChange(strategy), [strategy, onChange])

  return (
    <Wrapper>{children}</Wrapper>
  )
}
