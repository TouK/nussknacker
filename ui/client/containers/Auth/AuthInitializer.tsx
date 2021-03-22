import React, {PropsWithChildren, useState} from "react"
import {Strategy} from "./Strategy"
import {StrategyInitializer} from "./StrategyInitializer"
import {StrategySelector} from "./StrategySelector"

interface Props {
  onAuthFulfilled: () => Promise<void>,
}

export function AuthInitializer({onAuthFulfilled, children}: PropsWithChildren<Props>): JSX.Element {
  const [strategy, setStrategy] = useState<Strategy>()
  return (
    <StrategySelector onChange={setStrategy}>
      {strategy && (
        <StrategyInitializer onAuthFulfilled={onAuthFulfilled} strategy={strategy}>
          {children}
        </StrategyInitializer>
      )}
    </StrategySelector>
  )
}
