import { RootRoutes } from "./components/rootRoutes";
import React from "react";
import { NkView, NkViewProps } from "./common/nkView";

export default function ComponentsNkView(props: NkViewProps): JSX.Element {
    return (
        <NkView {...props}>
            <RootRoutes inTab />
        </NkView>
    );
}
