# SicMu Neo - Technical Overview for Agents

SicMu Neo is a file-based Android music player (Java 11, SDK 36, Min SDK 26).

## Key Components & Architecture
- **Main.java**: Entry point Activity. Handles UI, permissions, and service binding.
- **MusicService.java**: Core playback engine using **Media3 (ExoPlayer)**. Implements `MediaBrowserServiceCompat`.
- **Data Model**: Hierarchical tree structure using `Row`, `RowGroup`, and `RowSong`.
- **Metadata**: Uses a hard-forked `org.jaudiotagger` for audio tags.
- **Persistence**: **Room Database** (`SongDatabase.java`, `Database.java`) for metadata caching and configuration.
- **UI**: Minimalist, uses `RowsAdapter.java` for the main list. Supports dark/light themes via `Theme.java`.

## Critical Patterns
- **File System Centric**: Primarily navigates via `Path.java`. Metadata mode is a secondary view of the same structure.
- **Service Communication**: Main activity communicates with `MusicService` via AIDL/Binder and MediaSession.
- **Audio Processing**: Custom `BypassAudioProcessor` and `MergeAudioProcessor` handle mono/stereo downmixing.

---

**1. Think Before Coding -- Don't assume. Don't hide confusion. Surface tradeoffs.**
Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

**2. Simplicity First -- Minimum code that solves the problem. Nothing speculative.**
- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- No fallback mechanisms that were'nt requested
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a team of the senior engineers Russ Cox, Rob Pike, Ken Thompson and Dennis Richie say this is overcomplicated?" If yes, simplify.

**3. Surgical Changes -- Touch only what you must. Clean up only your own mess.**
When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

**4. Goal-Driven Execution -- Define success criteria. Loop until verified.**
Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan:
```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.


