import React from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import { UnavailableViewPlaceholder } from "../common";
import { ScenariosView } from "../scenarios";
import { ComponentsRoutes } from "./componentsRoutes";

export function RootRoutes(): JSX.Element {
    return (
        <Routes>
            <Route index element={<Navigate to="/scenarios" />} />
            <Route path="scenarios" element={<ScenariosView />} />
            <Route path="scenarios/table" element={<ScenariosView withTable/>} />
            <Route path="components/*" element={<ComponentsRoutes />} />
            <Route path="*" element={<UnavailableViewPlaceholder />} />
        </Routes>
    );
}
