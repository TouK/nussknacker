import React from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import { ComponentView } from "./usages";
import { ListWithFilters } from "./listWithFilters";
import { UnavailableViewPlaceholder, View } from "../common";
import { Prototype } from "../scenarios";

export function RootRoutes({ inTab }: { inTab?: boolean }): JSX.Element {
    return (
        <View inTab={inTab}>
            <Routes>
                <Route path="/" element={<ListWithFilters />} />
                <Route path="usages">
                    <Route index element={<Navigate to="/invalid" replace />} />
                    <Route path=":componentId" element={<ComponentView />} />
                </Route>
                <Route path="prototype" element={<Prototype />} />
                <Route path="*" element={<UnavailableViewPlaceholder />} />
            </Routes>
        </View>
    );
}
