import React, { useEffect } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import { ComponentView } from "./usages";
import { ComponentsView } from "./listWithFilters";
import { UnavailableViewPlaceholder, View } from "../common";
import { ScenariosView } from "../scenarios";

export function RootRoutes({ inTab }: { inTab?: boolean }): JSX.Element {
    return (
        <View inTab={inTab}>
            <Routes>
                <Route index element={<ComponentsView />} />
                <Route path="usages">
                    <Route index element={<Navigate to="/invalid" replace />} />
                    <Route path=":componentId" element={<ComponentView />} />
                </Route>
                <Route path="scenarios" element={<ScenariosView />} />
                <Route path="test" element={<Test />} />
                <Route path="*" element={<UnavailableViewPlaceholder />} />
            </Routes>
        </View>
    );
}

const Test = () => {
    useEffect(() => {
        const receiver = window.top;
        const message = { redirectUrl: window.location.href };
        receiver.postMessage(message, { targetOrigin: "http://localhost:3000" });
    });
    return null;
};
