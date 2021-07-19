import React, {PropsWithChildren, useState} from "react"
import {AuthenticationSettings} from "../../reducers/settings"
import {Strategy} from "./Strategy"
import {StrategyInitializer} from "./StrategyInitializer"
import {StrategySelector} from "./StrategySelector"

interface Props {
  onAuthFulfilled: () => Promise<void>,
  authenticationSettings?: AuthenticationSettings,
}

export function AuthInitializer({authenticationSettings, onAuthFulfilled, children}: PropsWithChildren<Props>): JSX.Element {
  const [strategy, setStrategy] = useState<Strategy>()
  return authenticationSettings ?
    (
      <StrategySelector authenticationSettings={authenticationSettings} onChange={setStrategy}>
        {strategy && (
          <StrategyInitializer onAuthFulfilled={onAuthFulfilled} strategy={strategy}>
            {children}
          </StrategyInitializer>
        )}
      </StrategySelector>
    ) :
    null
}
