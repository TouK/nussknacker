import type { PropsWithChildren } from "react";
import React from "react";

import { ElementsRow } from "./elementsRow";
import { ElementWithSeparator } from "./elementWithSeparator";

export function SecondaryElementsRow({ children }: PropsWithChildren) {
    const childrenArray = React.Children.toArray(children);
    if (childrenArray.length <= 0) {
        return null;
    }
    return (
        <ElementsRow sx={{ flexWrap: "wrap" }}>
            {childrenArray.map((child, i, { length }) => (
                <ElementWithSeparator key={i} showDivider={i < length - 1}>
                    {child}
                </ElementWithSeparator>
            ))}
        </ElementsRow>
    );
}
