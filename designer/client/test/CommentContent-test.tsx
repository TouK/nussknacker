import CommentContent from "../src/components/comment/CommentContent";
import React from "react";
import { render } from "@testing-library/react";
import { describe, expect } from "@jest/globals";
import { NuThemeProvider } from "../src/containers/theme/nuThemeProvider";

describe("CommentContent#newContent", () => {
    const content = "This is a BUG-123";

    const multiContent = "This is a BUG-123, and this is another: BUG-124";

    it("replaces matched expressions with links", () => {
        const commentSettings = { substitutionPattern: "(BUG-[0-9]*)", substitutionLink: "http://bugs/$1" };
        const { container } = render(
            <NuThemeProvider>
                <CommentContent content={content} commentSettings={commentSettings} />
            </NuThemeProvider>,
        );
        expect(container).toContainHTML(
            '<div><div class="css-10wuv5k"><span class="MuiTypography-root MuiTypography-caption css-1w8legj-MuiTypography-root">This is a <a class="MuiTypography-root MuiTypography-inherit MuiLink-root MuiLink-underlineAlways css-1xhj5go-MuiTypography-root-MuiLink-root" href="http://bugs/BUG-123" target="_blank">BUG-123</a></span></div></div>',
        );
    });

    it("replaces multiple matched expressions with links", () => {
        const commentSettings = { substitutionPattern: "(BUG-[0-9]*)", substitutionLink: "http://bugs/$1" };
        const { container } = render(
            <NuThemeProvider>
                <CommentContent content={multiContent} commentSettings={commentSettings} />
            </NuThemeProvider>,
        );
        expect(container).toContainHTML(
            '<div><div class="css-10wuv5k"><span class="MuiTypography-root MuiTypography-caption css-1w8legj-MuiTypography-root">This is a <a class="MuiTypography-root MuiTypography-inherit MuiLink-root MuiLink-underlineAlways css-1xhj5go-MuiTypography-root-MuiLink-root" href="http://bugs/BUG-123" target="_blank">BUG-123</a>, and this is another: <a class="MuiTypography-root MuiTypography-inherit MuiLink-root MuiLink-underlineAlways css-1xhj5go-MuiTypography-root-MuiLink-root" href="http://bugs/BUG-124" target="_blank">BUG-124</a></span></div></div>',
        );
    });

    it("leaves content unchanged when it does not match with expression", () => {
        const commentSettings = { substitutionPattern: "(BUGZ-[0-9]*)", substitutionLink: "http://bugs/$1" };
        const { container } = render(
            <NuThemeProvider>
                <CommentContent content={content} commentSettings={commentSettings} />
            </NuThemeProvider>,
        );
        expect(container).toContainHTML("This is a BUG-123");
    });

    it("leaves content unchanged when settings are not provided", () => {
        const commentSettings = {};
        const { container } = render(
            <NuThemeProvider>
                <CommentContent content={content} commentSettings={commentSettings} />
            </NuThemeProvider>,
        );
        expect(container).toContainHTML("This is a BUG-123");
    });
});
