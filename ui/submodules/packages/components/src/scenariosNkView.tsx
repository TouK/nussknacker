import React from "react";
import { RootRoutes } from "./scenarios";
import { NkView, NkViewProps } from "./common";

export default function ScenariosNkView(props: NkViewProps): JSX.Element {
    return (
        <NkView {...props}>
            <RootRoutes inTab />
        </NkView>
    );
}
