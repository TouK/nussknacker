import type { ReactNode } from "react";
import React, { useMemo, useState } from "react";

import { PrimaryElementsRow } from "./primaryElementsRow";
import { SecondaryElementsRow } from "./secondaryElementsRow";

export const useWrappedStack = (children: ReactNode, onSpacingChange?: (box?: DOMRectReadOnly | null) => void) => {
    const [hiddenChildren, setHiddenChildren] = useState<Array<Exclude<React.ReactNode, boolean | null | undefined>>>([]);

    const primaryLine = useMemo(
        () => (
            <PrimaryElementsRow onSpacingChange={onSpacingChange} onHiddenElementsChanged={setHiddenChildren}>
                {children}
            </PrimaryElementsRow>
        ),
        [children, onSpacingChange],
    );

    const secondaryLine = useMemo(() => <SecondaryElementsRow>{hiddenChildren}</SecondaryElementsRow>, [hiddenChildren]);

    return useMemo(
        () => ({
            primaryLine,
            secondaryLine,
        }),
        [primaryLine, secondaryLine],
    );
};
