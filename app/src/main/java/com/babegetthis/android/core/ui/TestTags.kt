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

    // --- shoppinglist ---
    //
    // Only two entries, and both earn their place: the list card is a Card with
    // Modifier.combinedClickable and the tab is a Surface with Modifier.clickable,
    // neither of which merges its descendants' semantics. So the name/label Text
    // inside is a separate node with no click action, and a test can't reach the
    // clickable through it. Material3 Button/TextButton DO merge, which is why the
    // dialogs, the FAB and the empty-state CTA carry no tags — their tests match
    // on visible text.
    fun listCard(listId: String) = "shoppinglist.card.$listId"

    fun listTab(index: Int) = "shoppinglist.tab.$index"
}
