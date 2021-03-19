import React, {PropsWithChildren, useEffect, useMemo} from "react"
import {useSelector} from "react-redux"
import {RootState} from "../../reducers"
import {AuthenticationSettings} from "../../reducers/settings"
import {STRATEGIES} from "./strategies"
import {Strategy} from "./Strategy"

interface Props {
  onChange: (strategy: Strategy) => void,
}

export function StrategySelector({children, onChange}: PropsWithChildren<Props>): JSX.Element {
  const authenticationSettings = useSelector<RootState, AuthenticationSettings>(state => state.settings.authenticationSettings)
  const {backend} = authenticationSettings

  const strategyConstructor = useMemo(() => STRATEGIES[backend], [backend])
  const strategy = useMemo(() => strategyConstructor && new strategyConstructor(authenticationSettings), [strategyConstructor, authenticationSettings])
  const Wrapper = strategy?.Wrapper || React.Fragment

  useEffect(() => onChange(strategy), [strategy, onChange])

  return (
    <Wrapper>{children}</Wrapper>
  )
}
