import React from "react";
import { Route, Routes } from "react-router-dom";
import { UnavailableViewPlaceholder, View } from "../common";
import { ScenariosWithActions } from "./scenariosView";

interface Props {
    inTab?: boolean;
    isTable?: boolean;
    addScenario?: () => void;
    addFragment?: () => void;
}

export function RootRoutes({ inTab, isTable, ...props }: Props): JSX.Element {
    return (
        <View inTab={inTab}>
            <Routes>
                <Route path="/" element={<ScenariosWithActions {...props} isTable={isTable}/>} />
                <Route path="*" element={<UnavailableViewPlaceholder />} />
            </Routes>
        </View>
    );
}
