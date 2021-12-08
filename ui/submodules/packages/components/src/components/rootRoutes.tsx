import React from "react";
import { Route, Routes } from "react-router-dom";
import { ListWithFilters } from "./listWithFilters";
import { UnavailableViewPlaceholder } from "./unavailableViewPlaceholder";
import { View } from "./view";

export function RootRoutes({ inTab }: { inTab?: boolean }): JSX.Element {
    return (
        <View inTab={inTab}>
            <Routes>
                <Route path="/" element={<ListWithFilters />} />
                <Route path="*" element={<UnavailableViewPlaceholder />} />
            </Routes>
        </View>
    );
}
