package com.yura.app.library

object BookImportRules {
    fun isDuplicate(hasMatchingIdentifier: Boolean, hasMatchingTitleAndAuthor: Boolean): Boolean =
        hasMatchingIdentifier || hasMatchingTitleAndAuthor
}
