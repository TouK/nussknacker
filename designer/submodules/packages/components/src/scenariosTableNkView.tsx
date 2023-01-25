import React, { PropsWithChildren } from "react";
import { RootRoutes } from "./scenarios";
import { NkView, NkViewProps } from "./common";

export default React.memo(function ScenariosTableNkView({ basepath, onNavigate, ...passProps }: PropsWithChildren<NkViewProps>): JSX.Element {
    return (
        <NkView basepath={basepath} onNavigate={onNavigate}>
            <RootRoutes inTab {...passProps} isTable={true}/>
        </NkView>
    );
});
