package com.babegetthis.android.core.ui

// Semantics tags for elements that Compose tests cannot address by user-visible
// text. Lives in `main`, not `test`, because both sides reference these — a
// rename is then a compile error in both places rather than a test that silently
// finds nothing.
//
// Add a tag ONLY when text won't do. If a button's label is unique on its screen,
// the test should match on that label and the composable stays untouched;
// over-tagging turns every copy tweak into test churn.
//
// Entries are added per screen as the Compose test tasks are worked (groups 3, 5,
// 7, 9, 11 of openspec/changes/add-test-suite/tasks.md).
object TestTags {
    // Populated per screen — see tasks 3.1, 5.1, 7.3, 9.1, 11.3.
}
