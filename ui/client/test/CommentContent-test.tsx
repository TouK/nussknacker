import CommentContent from "../components/CommentContent"
import Adapter from "@wojtekmaj/enzyme-adapter-react-17"
import Enzyme, {mount} from "enzyme"
import React from "react"

describe("CommentContent#newContent", () => {
  Enzyme.configure({adapter: new Adapter()})

  const content = "This is a BUG-123"

  const multiContent = "This is a BUG-123, and this is another: BUG-124"

  it("replaces matched expressions with links", () => {
    const commentSettings = {matchExpression: "(BUG-[0-9]*)", link: "http://bugs/$1"}
    const commentContent = mount(<CommentContent content={content} commentSettings={commentSettings}/>)
    expect(commentContent.html()).toContain("This is a <a href=\"http://bugs/BUG-123\" target=\"_blank\">BUG-123</a>")
  })

  it("replaces mulitple matched expressions with links", () => {
    const commentSettings = {matchExpression: "(BUG-[0-9]*)", link: "http://bugs/$1"}
    const commentContent = mount(<CommentContent content={multiContent} commentSettings={commentSettings}/>)
    expect(commentContent.html()).toContain("This is a <a href=\"http://bugs/BUG-123\" target=\"_blank\">BUG-123</a>, and this is another: <a href=\"http://bugs/BUG-124\" target=\"_blank\">BUG-124</a>")
  })

  it("leaves content unchanged when it does not match with expression", () => {
    const commentSettings = {matchExpression: "(BUGZ-[0-9]*)", link: "http://bugs/$1"}
    const commentContent = mount(<CommentContent content={content} commentSettings={commentSettings}/>)
    expect(commentContent.html()).toContain("This is a BUG-123")
  })

  it("leaves content unchanged when settings are not provided", () => {
    const commentSettings = {}
    const commentContent = mount(<CommentContent content={content} commentSettings={commentSettings}/>)
    expect(commentContent.html()).toContain("This is a BUG-123")
  })
})
