import React from "react";
import { TruncatedList, TruncatedListProps } from "react-truncate-list";
import "react-truncate-list/dist/styles.css";

const defaultRenderTruncator = ({ hiddenItemsCount }) => <div>{hiddenItemsCount} more...</div>;

export function Truncate({ renderTruncator = defaultRenderTruncator, ...props }: TruncatedListProps): JSX.Element {
    return <TruncatedList renderTruncator={renderTruncator} {...props} />;
}
