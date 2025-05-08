import * as tstyche from "tstyche";

import type { NormalizePath, Paths, PathValue } from "./typeHelpers";

type Data = {
    user: {
        name: string;
        profile: {
            age: number;
        };
        posts: { id: number; text: string }[];
    };
};

describe("setImmutable typeHelpers", () => {
    describe("Paths", () => {
        it("resolves deep values", () => {
            tstyche.expect<Paths<Data>>().type.not.toBeAssignableWith<string>();
            tstyche.expect<Paths<Data>>().type.toBe<
                | "user"
                | "user.name"
                | "user.profile"
                | "user.profile.age"
                | "user.posts"
                | `user.posts[${number}]`
                | `user.posts[${number}].id`
                | `user.posts[${number}].text`
                | `user.posts.${number}`
                | `user.posts.${number}.id`
                | `user.posts.${number}.text`
                // for editor completion only
                | `user.posts[]`
                | `user.posts[].id`
                | `user.posts[].text`
                | `user.posts.#`
                | `user.posts.#.id`
                | `user.posts.#.text`
            >();
            tstyche.expect<Paths<Data>>().type.toBeAssignableWith<`user.posts.128.text`>();
            tstyche.expect<Paths<Data>>().type.toBeAssignableWith<`user.posts[128].text`>();
        });

        it("blocks invalid paths", () => {
            tstyche.expect<Paths<Data>>().type.not.toBeAssignableWith<"user.name2">();
            tstyche.expect<Paths<Data>>().type.not.toBeAssignableWith<"user.posts.id">();
            tstyche.expect<Paths<Data>>().type.not.toBeAssignableWith<"user.posts.0.sad">();
        });
    });

    describe("PathValue", () => {
        it("resolves deep values", () => {
            tstyche.expect<PathValue<Data, "user.name">>().type.toBe<string>();
            tstyche.expect<PathValue<Data, "user.profile.age">>().type.toBe<number>();
            tstyche.expect<PathValue<Data, "user.posts[0].id">>().type.toBe<number>();
            tstyche.expect<PathValue<Data, "user.posts.1.text">>().type.toBe<string>();
        });

        it("blocks invalid paths", () => {
            tstyche.expect<PathValue<Data, "user.invalid">>().type.toBe<never>();
            tstyche.expect<PathValue<Data, "user.posts[99].nonexistent">>().type.toBe<never>();
            tstyche.expect<PathValue<Data, "user.posts[99].1">>().type.toBe<never>();
            tstyche.expect<PathValue<Data, "user.posts[#]">>().type.toBe<never>();
            tstyche.expect<PathValue<Data, "user.posts.#">>().type.toBe<never>();
            tstyche.expect<PathValue<Data, "user.posts.1.1">>().type.toBe<never>();
        });
    });

    describe("NormalizePath", () => {
        it("should omit brackets", () => {
            tstyche.expect<NormalizePath<"a.b">>().type.toBe<"a.b">();
            tstyche.expect<NormalizePath<"a[1].b">>().type.toBe<"a.1.b">();
            tstyche.expect<NormalizePath<"a[1].b[2].c">>().type.toBe<"a.1.b.2.c">();
            tstyche.expect<NormalizePath<"a[].b">>().type.toBe<never>();
        });
    });
});
