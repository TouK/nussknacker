# Modifying the documentation

## The hyperlinks

**TL;DR**
Use only links relative root of this project; start them always with `/docs`. 

**More details**
Documentation relies heavily on internal hyperlinks. IDE path hints and preview functionality are crucial to get the links right. So we want paths that 'resolve' in IDE. 

## Component name capitalization

In Nussknacker documentation, component names appear in two contexts — as **UI labels** (specific components available in the Component Palette) and as **generic terms** (concepts describing a type or role).  
To keep the text natural and consistent, follow these rules:

1. **Capitalize** component names when you refer to a specific component as it appears in the UI.  
   Example: *Add a **Filter** node after the **Kafka Source** to remove invalid records.*

2. **Use lowercase** when you refer to a general concept or category of components.  
   Example: *Each scenario starts with a **source** and ends with a **sink**.  
   Some scenarios include multiple **enrichers**.*

3. **Preserve acronyms and proper names** as written — do not force them to lowercase.  
   Examples: *HTTP Endpoint*, *ML Enricher*, *PMML Enricher*, *OpenAPI Enricher*.

4. Avoid mixing styles in one sentence.  
   ❌ *“Every Scenario starts with a Source.”*  
   ✅ *“Every scenario starts with a source.”*


## Image metadata guidelines (ALT, title, caption)

When adding images to the documentation, every image must include:

- ALT text  
- title attribute  
- figure caption  

Each of these is used by different systems (screen readers, search engines, LLMs, humans) and must be written with a different purpose in mind.
Check [this document](README-image-metadata) for more details if not certain - metadata are important!

---
