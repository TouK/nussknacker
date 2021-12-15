import React from "react";
import TruncatedList from "react-truncate-list";
import "react-truncate-list/dist/styles.css";

export function Truncate(...params: Parameters<typeof TruncatedList>): JSX.Element {
    const [props] = params;
    return <TruncatedList renderTruncator={({ hiddenItemsCount }) => <div>{hiddenItemsCount} more...</div>} {...props} />;
}
