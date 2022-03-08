import React from "react";
import { Route, Routes } from "react-router-dom";
import { UnavailableViewPlaceholder, View } from "../common";
import { ScenariosView } from "./scenariosView";

export function RootRoutes({ inTab }: { inTab?: boolean }): JSX.Element {
    return (
        <View inTab={inTab}>
            <Routes>
                <Route path="/" element={<ScenariosView />} />
                <Route path="*" element={<UnavailableViewPlaceholder />} />
            </Routes>
        </View>
    );
}
