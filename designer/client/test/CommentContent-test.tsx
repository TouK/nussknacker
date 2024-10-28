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
            '<div><div class="css-11j7oje"><span class="MuiTypography-root MuiTypography-caption css-1w8legj-MuiTypography-root">This is a <a class="MuiTypography-root MuiTypography-inherit MuiLink-root MuiLink-underlineAlways css-1xhj5go-MuiTypography-root-MuiLink-root" href="http://bugs/BUG-123" target="_blank">BUG-123</a></span></div></div>',
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
            '<div><div class="css-11j7oje"><span class="MuiTypography-root MuiTypography-caption css-1w8legj-MuiTypography-root">This is a <a class="MuiTypography-root MuiTypography-inherit MuiLink-root MuiLink-underlineAlways css-1xhj5go-MuiTypography-root-MuiLink-root" href="http://bugs/BUG-123" target="_blank">BUG-123</a>, and this is another: <a class="MuiTypography-root MuiTypography-inherit MuiLink-root MuiLink-underlineAlways css-1xhj5go-MuiTypography-root-MuiLink-root" href="http://bugs/BUG-124" target="_blank">BUG-124</a></span></div></div>',
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

    it.each<[string, string, string]>([
        [
            "issues",
            '<a class="MuiTypography-root MuiTypography-inherit MuiLink-root MuiLink-underlineAlways css-1xhj5go-MuiTypography-root-MuiLink-root" href="https://github.com/TouK/nussknacker/issues/234" target="_blank">issues/234</a>',
            '<div><div class="css-11j7oje"><span class="MuiTypography-root MuiTypography-caption css-1w8legj-MuiTypography-root"><a class="MuiTypography-root MuiTypography-inherit MuiLink-root MuiLink-underlineAlways css-1xhj5go-MuiTypography-root-MuiLink-root" href="https://github.com/TouK/nussknacker/issues/234" target="_blank"><span><span class=" " style="color:#FF9A4D;background:#242F3E;font-weight:bold">issues</span><span class="">/234</span></span></a></span></div></div>',
        ],
        [
            "val",
            "single value",
            '<div><div class="css-11j7oje"><span class="MuiTypography-root MuiTypography-caption css-1w8legj-MuiTypography-root"><span><span class="">single </span><span class=" " style="color:#FF9A4D;background:#242F3E;font-weight:bold">val</span><span class="">ue</span></span></span></div></div>',
        ],
        [
            "issues",
            'issues <a class="MuiTypography-root MuiTypography-inherit MuiLink-root MuiLink-underlineAlways css-1xhj5go-MuiTypography-root-MuiLink-root" href="https://github.com/TouK/nussknacker/issues/234" target="_blank">issues/234</a> end value',
            '<div><div class="css-11j7oje"><span class="MuiTypography-root MuiTypography-caption css-1w8legj-MuiTypography-root"><span><span class=" " style="color:#FF9A4D;background:#242F3E;font-weight:bold">issues</span><span class=""> </span></span><a class="MuiTypography-root MuiTypography-inherit MuiLink-root MuiLink-underlineAlways css-1xhj5go-MuiTypography-root-MuiLink-root" href="https://github.com/TouK/nussknacker/issues/234" target="_blank"><span><span class=" " style="color:#FF9A4D;background:#242F3E;font-weight:bold">issues</span><span class="">/234</span></span></a> end value</span></div></div>',
        ],
        [
            "ITDEVRTM",
            '<a class="MuiTypography-root MuiTypography-inherit MuiLink-root MuiLink-underlineAlways css-f06hvx" href="https://jira.playmobile.pl/jira/browse/ITDEVRTM-2279" target="_blank">ITDEVRTM-2279</a> issues <a class="MuiTypography-root MuiTypography-inherit MuiLink-root MuiLink-underlineAlways css-1xhj5go-MuiTypography-root-MuiLink-root" href="https://github.com/TouK/nussknacker/issues/234" target="_blank">issues/234</a> end value <a class="MuiTypography-root MuiTypography-inherit MuiLink-root MuiLink-underlineAlways css-1xhj5go-MuiTypography-root-MuiLink-root" href="https://github.com/TouK/nussknacker/issues/555" target="_blank">issues/555</a>',
            '<div><div class="css-11j7oje"><span class="MuiTypography-root MuiTypography-caption css-1w8legj-MuiTypography-root"><a class="MuiTypography-root MuiTypography-inherit MuiLink-root MuiLink-underlineAlways css-f06hvx" href="https://jira.playmobile.pl/jira/browse/ITDEVRTM-2279" target="_blank"><span><span class=" " style="color:#FF9A4D;background:#242F3E;font-weight:bold">ITDEVRTM</span><span class="">-2279</span></span></a> issues <a class="MuiTypography-root MuiTypography-inherit MuiLink-root MuiLink-underlineAlways css-1xhj5go-MuiTypography-root-MuiLink-root" href="https://github.com/TouK/nussknacker/issues/234" target="_blank">issues/234</a> end value <a class="MuiTypography-root MuiTypography-inherit MuiLink-root MuiLink-underlineAlways css-1xhj5go-MuiTypography-root-MuiLink-root" href="https://github.com/TouK/nussknacker/issues/555" target="_blank">issues/555</a></span></div></div>',
        ],
    ])("should replace string value when %s search and %s value", (searchWord, content, expected) => {
        const { container } = render(
            <NuThemeProvider>
                <CommentContent content={content} commentSettings={{}} searchWords={[searchWord]} />
            </NuThemeProvider>,
        );
        expect(container).toContainHTML(expected);
    });
});
