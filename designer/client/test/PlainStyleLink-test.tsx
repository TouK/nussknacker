//TODO: Temporary disable ts-check because of "TS1259: Module '"/Users/dawidpoliszak/Documents/repo/nussknacker/designer/client/node_modules/@types/react/ts5.0/index"' can only be default-imported using the 'esModuleInterop' flag" error
// @ts-ignore
import React from "react";
import { MemoryRouter } from "react-router";
import { isExternalUrl, PlainStyleLink } from "../src/containers/plainStyleLink";
import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "@jest/globals";

const Link = (props) => (
    <MemoryRouter>
        <PlainStyleLink {...props} />
    </MemoryRouter>
);

describe("PlainStyleLink", () => {
    describe("isExternalUrl", () => {
        it("should pass http://", () => {
            expect(isExternalUrl("http://google.com")).toBeTruthy();
        });
        it("should pass https://", () => {
            expect(isExternalUrl("https://google.com")).toBeTruthy();
        });
        it("should pass //", () => {
            expect(isExternalUrl("//google.com")).toBeTruthy();
        });
        it("should drop /", () => {
            expect(isExternalUrl("/google")).toBeFalsy();
        });
        it("should drop ?", () => {
            expect(isExternalUrl("?google")).toBeFalsy();
        });
        it("should drop #", () => {
            expect(isExternalUrl("#google")).toBeFalsy();
        });
        it("should drop other", () => {
            expect(isExternalUrl("google")).toBeFalsy();
        });
    });

    it.each([
        ["http://", "http://google.com", "http://google.com"],
        ["https://", "https://google.com", "https://google.com"],
        ["//", "//google", "//google"],
        ["/", "/google", "/google"],
        ["?", "?google", "/?google"],
        ["#", "#google", "/#google"],
        ["plain string", "google", "/google"],
    ])("should support %s", (_, to, expected) => {
        const { container } = render(<Link to={to} />);

        //TODO: Fix Jest types we need to import expect, but the types are not extended by @testing-library/jest-dom
        // @ts-ignore
        expect(screen.getByRole("link")).toHaveAttribute("href", expected);
    });
});
