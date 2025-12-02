import React, { memo } from "react";

export interface ItemsProps<I> {
    items: { item: I; el: React.JSX.Element }[];
}

export const Items = memo(function Items<I>(props: ItemsProps<I>): React.JSX.Element {
    return <>{props.items.map(({ el }) => el)}</>;
});
