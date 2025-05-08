import * as tstyche from "tstyche";

import { setImmutable } from "./setImmutable";

type Data = {
    user: {
        name: string;
        profile: {
            age: number;
        };
        posts: { id: number; text: string }[];
    };
};

const data: Data = {
    user: {
        name: "Alice",
        profile: { age: 30 },
        posts: [
            { id: 1, text: "first" },
            { id: 2, text: "second" },
        ],
    },
};

describe("setImmutable", () => {
    it("works correctly with props", () => {
        const updated = setImmutable(data, "user.name", "Bob");
        expect(updated.user.name).toBe("Bob");
        expect(updated.user.profile).toBe(data.user.profile);
        expect(updated.user.posts).toBe(data.user.posts);
    });

    it("works correctly with array index", () => {
        const updated = setImmutable(data, "user.posts[1].text", "updated");
        expect(updated.user.posts).not.toBe(data.user.posts);
        expect(updated.user.posts[1].text).toBe("updated");
        expect(updated.user.posts[0]).toBe(data.user.posts[0]);
    });

    it("respects deep types", () => {
        tstyche.expect(setImmutable(data, "user.name2", "Test")).type.toRaiseError();
        tstyche.expect(setImmutable(data, "user.name", 123)).type.toRaiseError();
        tstyche.expect(setImmutable(data, "user.posts[5].id", "123")).type.toRaiseError();
        tstyche.expect(setImmutable(data, "user.posts.#.id", 123)).type.toRaiseError();
        tstyche.expect(setImmutable(data, "user.posts[].id", 123)).type.toRaiseError();
    });

    it("tries to recover from invalid path", () => {
        let updated: Data;
        tstyche.expect((updated = setImmutable(data, "user.posts[].text", "updated"))).type.toRaiseError();
        expect(updated).toBe(data);
        let updated2: Data;
        tstyche.expect((updated2 = setImmutable(data, "user.posts[]", []))).type.toRaiseError();
        expect(updated2).not.toBe(data);
        expect(updated2.user.posts).toBe([]);
        expect(updated2.user.profile).toBe(data.user.profile);
    });
});
