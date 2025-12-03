import type { PropsWithChildren } from "react";
import React, { useState } from "react";

import type { AuthenticationSettings } from "../../reducers/settings";
import type { InitErrorComponentProps } from "./InitErrorComponent";
import type { Strategy } from "./Strategy";
import { StrategyInitializer } from "./StrategyInitializer";
import { StrategySelector } from "./StrategySelector";

interface Props {
    onAuthFulfilled: () => Promise<void>;
    authenticationSettings?: AuthenticationSettings;
    errorComponent?: React.ComponentType<PropsWithChildren<InitErrorComponentProps>>;
}

export function AuthInitializer({
    authenticationSettings,
    onAuthFulfilled,
    children,
    errorComponent = ({ children }) => <>{children}</>,
}: PropsWithChildren<Props>): React.JSX.Element {
    const [strategy, setStrategy] = useState<Strategy>();
    return authenticationSettings ? (
        <StrategySelector authenticationSettings={authenticationSettings} onChange={setStrategy}>
            {strategy && (
                <StrategyInitializer onAuthFulfilled={onAuthFulfilled} strategy={strategy} errorComponent={errorComponent}>
                    {children}
                </StrategyInitializer>
            )}
        </StrategySelector>
    ) : null;
}
