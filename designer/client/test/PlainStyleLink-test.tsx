import React from "react";
import { MemoryRouter } from "react-router";
import { isExternalUrl, PlainStyleLink } from "../src/containers/plainStyleLink";
import { render, screen } from "@testing-library/react";

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
        render(<Link to={to} />);

        expect(screen.getByRole("link")).toHaveAttribute("href", expected);
    });
});
