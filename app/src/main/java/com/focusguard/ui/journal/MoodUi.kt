package com.focusguard.ui.journal

import com.focusguard.decision.Mood

internal val Mood.emoji: String
    get() = when (this) {
        Mood.VERY_BAD -> "😣"
        Mood.BAD -> "🙁"
        Mood.OK -> "😐"
        Mood.GOOD -> "🙂"
        Mood.GREAT -> "😄"
    }

internal val Mood.label: String
    get() = when (this) {
        Mood.VERY_BAD -> "Muy mal"
        Mood.BAD -> "Mal"
        Mood.OK -> "Normal"
        Mood.GOOD -> "Bien"
        Mood.GREAT -> "Muy bien"
    }
