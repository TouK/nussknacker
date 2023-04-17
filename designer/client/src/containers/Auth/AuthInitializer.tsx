import React, {PropsWithChildren, useState} from "react"
import {AuthenticationSettings} from "../../reducers/settings"
import {Strategy} from "./Strategy"
import {StrategyInitializer} from "./StrategyInitializer"
import {StrategySelector} from "./StrategySelector"
import {InitErrorComponentProps} from "./InitErrorComponent"

interface Props {
  onAuthFulfilled: () => Promise<void>,
  authenticationSettings?: AuthenticationSettings,
  errorComponent?: React.ComponentType<PropsWithChildren<InitErrorComponentProps>>,
}

export function AuthInitializer({
  authenticationSettings,
  onAuthFulfilled,
  children,
  errorComponent = ({children}) => <>{children}</>,
}: PropsWithChildren<Props>): JSX.Element {
  const [strategy, setStrategy] = useState<Strategy>()
  return authenticationSettings ?
    (
      <StrategySelector authenticationSettings={authenticationSettings} onChange={setStrategy}>
        {strategy && (
          <StrategyInitializer
            onAuthFulfilled={onAuthFulfilled}
            strategy={strategy}
            errorComponent={errorComponent}
          >
            {children}
          </StrategyInitializer>
        )}
      </StrategySelector>
    ) :
    null
}
