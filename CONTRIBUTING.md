# Contributing

## Setting up development environment

- For working on engine - standard `sbt` setup should be enough
- For working/running GUI please see `ui/README.md` for details instructions

## Java interoperability

The Nussknacker project is developed in Scala, but we want to keep API interoperable with Java in most of places.
Below you can find out some hints how to achieve that:
- Use abstract classes instead of traits in places where the main inheritance tree is easy to recognize
- Don't use package objects. If you need to have more classes in one file, prefer using files with name as a root class
  and subclasses next to root class or class with non-package object
- Be careful with abstract types - in some part of API will be better to use generics instead
- Be careful with `ClassTag`/`TypeTag` - should be always option to pass simple `Class[_]` or sth similar
- Prefer methods intead of function members (def foo: (T) => R)