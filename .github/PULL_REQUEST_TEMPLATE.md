## What does this change?

<!-- One or two sentences. If it is a questline, name the trail and say what it teaches. -->

## Why?

<!-- The problem it solves, or the branch it extends. Link the issue if there is one. -->

## How was it checked?

- [ ] `./gradlew test` passes (engine tests and content validation)
- [ ] `./gradlew build` passes and the jar loads in a 26.3 dev client
- [ ] For UI changes: a screenshot is attached, taken at GUI scale 2 or smaller
- [ ] For content: every new trail has a parent unless it is a root, a child unless it is the top level,
      a ritual finale, and `koreanLevel` matching `Topik.levelForDepth(depth)`
- [ ] No personal data, server addresses or machine paths in the diff, the screenshots or the log excerpts

## Notes for the reviewer

<!-- Anything to look at twice, or content you deliberately left for a follow-up. -->
