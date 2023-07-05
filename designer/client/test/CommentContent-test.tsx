import CommentContent from "../src/components/CommentContent";
import React from "react";
import { render } from "@testing-library/react";
import { describe, expect } from "@jest/globals";

describe("CommentContent#newContent", () => {
    const content = "This is a BUG-123";

    const multiContent = "This is a BUG-123, and this is another: BUG-124";

    it("replaces matched expressions with links", () => {
        const commentSettings = { substitutionPattern: "(BUG-[0-9]*)", substitutionLink: "http://bugs/$1" };
        const { container } = render(<CommentContent content={content} commentSettings={commentSettings} />);
        //TODO: Fix Jest types we need to import expect, but the types are not extended by @testing-library/jest-dom
        // @ts-ignore
        expect(container).toContainHTML('This is a <a href="http://bugs/BUG-123" target="_blank">BUG-123</a>');
    });

    it("replaces multiple matched expressions with links", () => {
        const commentSettings = { substitutionPattern: "(BUG-[0-9]*)", substitutionLink: "http://bugs/$1" };
        const { container } = render(<CommentContent content={multiContent} commentSettings={commentSettings} />);
        //TODO: Fix Jest types we need to import expect, but the types are not extended by @testing-library/jest-dom
        // @ts-ignore
        expect(container).toContainHTML(
            'This is a <a href="http://bugs/BUG-123" target="_blank">BUG-123</a>, and this is another: <a href="http://bugs/BUG-124" target="_blank">BUG-124</a>',
        );
    });

    it("leaves content unchanged when it does not match with expression", () => {
        const commentSettings = { substitutionPattern: "(BUGZ-[0-9]*)", substitutionLink: "http://bugs/$1" };
        const { container } = render(<CommentContent content={content} commentSettings={commentSettings} />);
        //TODO: Fix Jest types we need to import expect, but the types are not extended by @testing-library/jest-dom
        // @ts-ignore
        expect(container).toContainHTML("This is a BUG-123");
    });

    it("leaves content unchanged when settings are not provided", () => {
        const commentSettings = {};
        const { container } = render(<CommentContent content={content} commentSettings={commentSettings} />);
        //TODO: Fix Jest types we need to import expect, but the types are not extended by @testing-library/jest-dom
        // @ts-ignore
        expect(container).toContainHTML("This is a BUG-123");
    });
});
