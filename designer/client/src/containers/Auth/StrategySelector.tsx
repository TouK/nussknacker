import type { PropsWithChildren } from "react";
import React, { useEffect, useMemo } from "react";

import type { AuthenticationSettings } from "../../reducers/settings";
import { STRATEGIES } from "./strategies";
import type { Strategy } from "./Strategy";

interface Props {
    onChange: (strategy: Strategy) => void;
    authenticationSettings: AuthenticationSettings;
}

export function StrategySelector({ authenticationSettings, children, onChange }: PropsWithChildren<Props>): React.JSX.Element {
    const { strategy: strategyName } = authenticationSettings;
    const strategyConstructor = useMemo(() => strategyName && STRATEGIES[strategyName], [strategyName]);
    const strategy = useMemo(
        () => strategyConstructor && new strategyConstructor(authenticationSettings),
        [strategyConstructor, authenticationSettings],
    );
    const Wrapper = strategy?.Wrapper || React.Fragment;

    useEffect(() => onChange(strategy), [strategy, onChange]);

    return <Wrapper>{children}</Wrapper>;
}
