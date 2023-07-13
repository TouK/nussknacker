import React from "react";
import { describe, expect, it } from "@jest/globals";
import { render, screen } from "@testing-library/react";
import { Notifications } from "./Notifications";
import { ConnectionErrorProvider } from "./connectionErrorProvider";
import { StoreProvider } from "../store/provider";
import { MuiThemeProvider } from "./muiThemeProvider";

describe(Notifications.name, () => {
    it("should display no network error when there is no network access after notifications call", () => {
        render(
            <MuiThemeProvider>
                <StoreProvider>
                    <ConnectionErrorProvider>
                        <Notifications refreshTime={200} />
                    </ConnectionErrorProvider>
                </StoreProvider>
            </MuiThemeProvider>,
        );

        expect(screen.findByText(""));
    });
});
