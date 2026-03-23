import { deserialize, serialize } from "./nameValueRecordsListHelpers";
import type { NameValueRecord } from "./nameValueRecordsListHelpers";

describe("nameValueRecordsListHelpers", () => {
    describe("serialize", () => {
        it("should serialize single record", () => {
            const input: NameValueRecord[] = [{ name: "foo", value: "bar" }];
            const result = serialize(input);
            expect(result).toContain("{");
            expect(result).toContain("name:");
            expect(result).toContain("value:");
            expect(result).toContain("foo");
            expect(result).toContain("bar");
        });

        it("should serialize multiple records", () => {
            const input: NameValueRecord[] = [
                { name: "x", value: "1" },
                { name: "y", value: "2" },
            ];
            const result = serialize(input);
            expect(result).toContain("name:");
            expect(result).toContain("x");
            expect(result).toContain("y");
            expect(result).toContain("1");
            expect(result).toContain("2");
        });

        it("should serialize with SpEL expressions", () => {
            const input: NameValueRecord[] = [{ name: "result", value: "#input.x + #input.y" }];
            const result = serialize(input);
            expect(result).toContain("name:");
            expect(result).toContain("value:");
            expect(result).toContain("#input.x");
            expect(result).toContain("#input.y");
        });

        it("should handle empty value", () => {
            const input: NameValueRecord[] = [{ name: "key", value: "" }];
            const result = serialize(input);
            expect(result).toContain("name:");
            expect(result).toContain("key");
        });

        it("should handle record with only name", () => {
            const input: NameValueRecord[] = [{ name: "x", value: "" }];
            const result = serialize(input);
            expect(result).toContain("name:");
            expect(result).toContain("x");
        });

        it("should filter out falsy records", () => {
            const input = [{ name: "x", value: "1" }, null, { name: "y", value: "2" }] as NameValueRecord[];
            const result = serialize(input);
            expect(result).toContain("x");
            expect(result).toContain("y");
        });

        it("should return empty string for null input", () => {
            const result = serialize(null as unknown as NameValueRecord[]);
            expect(result).toBe("");
        });

        it("should handle empty array", () => {
            const result = serialize([]);
            expect(result).toBeTruthy();
        });
    });

    describe("deserialize", () => {
        it("should deserialize single record", () => {
            const input = '{{"name": "foo", "value": "bar"}}';
            const result = deserialize(input);
            expect(result).toHaveLength(1);
            expect(result[0].name).toBe('"foo"');
            expect(result[0].value).toBe('"bar"');
        });

        it("should deserialize multiple records", () => {
            const input = '{{"name": "x", "value": "1"}, {"name": "y", "value": "2"}}';
            const result = deserialize(input);
            expect(result).toHaveLength(2);
            expect(result[0].name).toBe('"x"');
            expect(result[0].value).toBe('"1"');
            expect(result[1].name).toBe('"y"');
            expect(result[1].value).toBe('"2"');
        });

        it("should deserialize with SpEL expressions", () => {
            const input = '{{"name": "result", "value": "#input.x + 1"}}';
            const result = deserialize(input);
            expect(result).toHaveLength(1);
            expect(result[0].name).toBe('"result"');
            expect(result[0].value).toContain("#input.x");
        });

        it("should return null for non-list", () => {
            const result = deserialize("42");
            expect(result).toBeNull();
        });

        it("should return null for unclosed list", () => {
            const result = deserialize('{{name: "x"}');
            expect(result).toBeNull();
        });

        it("should return null for invalid expression", () => {
            const result = deserialize("((((");
            expect(result).toBeNull();
        });

        it("should filter out non-map elements", () => {
            const input = '{{"name": "x", "value": "1"}, 42, {"name": "y", "value": "2"}}';
            const result = deserialize(input);
            expect(result).toHaveLength(2);
            expect(result[0].name).toBe('"x"');
            expect(result[0].value).toBe('"1"');
            expect(result[1].name).toBe('"y"');
            expect(result[1].value).toBe('"2"');
        });

        it("should handle graceful mode", () => {
            const input = '{{"name": "x", "value":';
            const result = deserialize(input, true);
            expect(result).toBeNull();
        });
    });

    describe("round-trip", () => {
        it("should serialize and deserialize back", () => {
            const original: NameValueRecord[] = [
                { name: "x", value: "1" },
                { name: "y", value: "2" },
            ];
            const serialized = serialize(original);
            const deserialized = deserialize(serialized);

            expect(deserialized).toHaveLength(2);
            expect(deserialized[0].name).toContain("x");
            expect(deserialized[0].value).toContain("1");
            expect(deserialized[1].name).toContain("y");
            expect(deserialized[1].value).toContain("2");
        });
    });
});
