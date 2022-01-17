import React, { useCallback } from "react";
import TruncatedList from "react-truncate-list";
import "react-truncate-list/dist/styles.css";

export function Truncate(...params: Parameters<typeof TruncatedList>): JSX.Element {
    const [props] = params;
    const renderTruncator = useCallback(({ hiddenItemsCount }) => (
        <div>{hiddenItemsCount} more...</div>
    ), []);
    return <TruncatedList renderTruncator={renderTruncator} {...props} />;
}
