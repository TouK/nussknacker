// https://dev.to/pffigueiredo/typescript-utility-keyof-nested-object-2pa3
export type NestedKeyOf<ObjectType extends NonNullable<unknown>> = {
    [Key in keyof ObjectType & (string | number)]: ObjectType[Key] extends NonNullable<unknown>
        ? `${Key}` | `${Key}.${NestedKeyOf<ObjectType[Key]>}`
        : `${Key}`;
}[keyof ObjectType & (string | number)];
