import ValidateDeployComment from "../components/ValidateDeployComment"

describe("validating comments on deploy", () => {
  const pattern = "(jira-[0-9]*)"

  it("is valid if comment is empty and comment is not required", () => {
    const validated = ValidateDeployComment("", {requireComment: false, matchExpression: undefined})
    expect(validated).toEqual({ isValid: true, toolTip: undefined })
  })

  it("is invalid if comment is empty and comment is required", () => {
    const validated = ValidateDeployComment("", {requireComment: true, matchExpression: undefined})
    expect(validated).toEqual({ isValid: false, toolTip: "Comment is required." })
  })

  it("is valid if match pattern is not provided", () => {
    const validated = ValidateDeployComment("Some comment", { requireComment: true, matchExpression: undefined })
    expect(validated).toEqual({ isValid: true, toolTip: undefined })
  })

  it("is invalid if comment does not match provided pattern", () => {
    const validated = ValidateDeployComment("Some comment jiraz-123", { requireComment: true, matchExpression: pattern })
    expect(validated).toEqual({ isValid: false, toolTip: "Comment does not match required pattern." })
  })

  it("is valid if comment matches provided pattern", () => {
    const validated = ValidateDeployComment("Some comment jira-123", { requireComment: true, matchExpression: pattern })
    expect(validated).toEqual({ isValid: true, toolTip: undefined })
  })

  it("is valid if comment matches provided pattern more than once", () => {
    const validated = ValidateDeployComment("Some comment jira-123 jira-234", { requireComment: true, matchExpression: pattern })
    expect(validated).toEqual({ isValid: true, toolTip: undefined })
  })
})
