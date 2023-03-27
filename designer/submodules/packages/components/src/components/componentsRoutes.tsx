import { Navigate, Route, Routes } from "react-router-dom";
import { ComponentsView } from "./listWithFilters";
import { ComponentView } from "./usages";
import React from "react";

export function ComponentsRoutes() {
    return (
        <Routes>
            <Route index element={<ComponentsView />} />
            <Route path="usages">
                <Route index element={<Navigate to=".." replace />} />
                <Route path=":componentId" element={<ComponentView />} />
            </Route>
            <Route path="*" element={<Navigate to=".." replace />} />
        </Routes>
    );
}
