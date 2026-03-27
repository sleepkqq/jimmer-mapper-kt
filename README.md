# jimmer-mapper-kt

A lightweight KSP (Kotlin Symbol Processing) processor that generates mapper implementations for [Jimmer](https://github.com/babyfish-ct/jimmer) entities.

Jimmer entities are immutable interfaces that use a DSL builder pattern. Standard mapping libraries like MapStruct cannot generate code for them because they rely on setters. This library bridges that gap by generating Jimmer DSL code at compile time from annotated mapper interfaces.

## Setup

### Gradle (JitPack)

Add the JitPack repository and dependencies:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

```kotlin
// build.gradle.kts
plugins {
    id("com.google.devtools.ksp") version "2.1.10-1.0.31"
}

dependencies {
    implementation("com.github.sleepkqq.jimmer-mapper-kt:jimmer-mapper-kt-annotations:1.0.0")
    ksp("com.github.sleepkqq.jimmer-mapper-kt:jimmer-mapper-kt-processor:1.0.0")
}
```

### Composite build (local development)

```kotlin
// settings.gradle.kts
includeBuild("../jimmer-mapper-kt")
```

## Usage

### Basic mapping

Define a mapper interface annotated with `@JimmerMapper`. The processor matches source properties to target entity properties by name.

```kotlin
data class CreateBookInput(
    val title: String,
    val isbn: String,
    val pageCount: Int?,
)

@JimmerMapper
interface BookMapper {

    fun toNew(input: CreateBookInput): Book
}
```

Generated:

```kotlin
@ApplicationScoped
class BookMapperImpl : BookMapper {

    override fun toNew(input: CreateBookInput): Book = Book {
        title = input.title
        isbn = input.isbn
        pageCount = input.pageCount
    }
}
```

### Foreign key pattern

Parameters named `{entityProperty}Id` are automatically mapped to FK shorthand properties on the Jimmer Draft:

```kotlin
@JimmerMapper
interface BookMapper {

    fun toNew(title: String, authorId: UUID): Book
}
```

Generated:

```kotlin
override fun toNew(title: String, authorId: UUID): Book = Book {
    this.title = title
    this.authorId = authorId
}
```

### Update with @Base

Use `@Base` to mark a parameter as the existing entity. The generated code uses Jimmer's copy DSL:

```kotlin
@JimmerMapper
interface BookMapper {

    fun toUpdated(@Base existing: Book, title: String, pageCount: Int): Book
}
```

Generated:

```kotlin
override fun toUpdated(existing: Book, title: String, pageCount: Int): Book = Book(existing) {
    this.title = title
    this.pageCount = pageCount
}
```

### Explicit @Mapping

Override auto-matching with explicit source-to-target mapping:

```kotlin
data class ImportBookInput(
    val bookTitle: String,
    val cover: String?,
)

@JimmerMapper
interface BookMapper {

    @Mapping(source = "input.bookTitle", target = "title")
    @Mapping(source = "input.cover", target = "avatarKey")
    fun toNew(input: ImportBookInput): Book
}
```

### @IgnoreMapping

Skip specific target properties:

```kotlin
@JimmerMapper
interface BookMapper {

    @IgnoreMapping("reviews", "ratings")
    fun toNew(input: CreateBookInput): Book
}
```

### Nested entity mapping

When a target property is a `@ManyToOne` / `@OneToOne` Jimmer entity and the source has matching scalar fields, the processor generates a nested Jimmer DSL block:

```kotlin
// Target entity Book has: val publisher: Publisher (@ManyToOne)
// Publisher has: val name: String, val country: String

data class BookEntry(
    val title: String,
    val name: String,     // matches Publisher.name
    val country: String,  // matches Publisher.country
)

@JimmerMapper
interface BookMapper {

    fun toNew(entry: BookEntry): Book
}
```

Generated:

```kotlin
override fun toNew(entry: BookEntry): Book = Book {
    title = entry.title
    publisher = Publisher {
        name = entry.name
        country = entry.country
    }
}
```

### Collection element mapping

When a target has a `@OneToMany` list and the source has a matching collection, the processor looks for a sibling method in the same mapper interface that maps the element type:

```kotlin
data class BookEntry(
    val title: String,
    val chapters: List<ChapterEntry>,
)

data class ChapterEntry(
    val title: String,
    val pageCount: Int,
)

@JimmerMapper
interface BookMapper {

    fun toNew(entry: BookEntry): Book

    // Sibling method — used automatically for chapters mapping
    fun toChapter(entry: ChapterEntry): Chapter
}
```

The processor discovers `toChapter` and generates `chapters = entry.chapters.map { toChapter(it) }`.

### Collection merge with @Base

When updating an entity, list parameters are merged with the existing collection:

```kotlin
@JimmerMapper
interface BookMapper {

    fun toUpdated(@Base existing: Book, chapters: List<Chapter>): Book
}
```

Generated:

```kotlin
override fun toUpdated(existing: Book, chapters: List<Chapter>): Book =
    Book(existing) {
        this.chapters = existing.chapters + chapters
    }
```

## Auto-skip rules

The processor automatically skips:

- `@Id` + `@GeneratedValue` properties (auto-generated by DB)
- `@OneToMany` / `@ManyToMany` properties (unless explicitly mapped or sibling method found)
- `@ManyToOne` / `@OneToOne` properties without FK parameter or matching source (parent set by Jimmer on save)
- `@MappedSuperclass` properties (`version`, `createdAt`, `updatedAt`)
- Nullable properties without a source (default to `null`)

## KSP options

| Option | Values | Default | Description |
|---|---|---|---|
| `jimmerMapper.cdiAnnotation` | `applicationScoped`, `singleton`, `none` | `applicationScoped` | CDI annotation on generated classes |

```kotlin
ksp {
    arg("jimmerMapper.cdiAnnotation", "none")
}
```

## Requirements

- Kotlin 2.1+
- KSP 2.1+
- Jimmer 0.9+
- JVM 21+

## License

Apache License 2.0
