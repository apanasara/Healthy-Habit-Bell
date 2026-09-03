package com.habitbell.app.data.model

/**
 * Chime pattern configuration specifying how bell strikes are grouped at interval boundaries or completion.
 */
enum class BellPattern {
    /** Single resonant Tibetan bell strike. */
    SINGLE,

    /** Two consecutive rhythmic bell strikes separated by a brief pause. */
    DOUBLE,

    /** Traditional 3-bell meditative sequence (crescendo / decrescendo harmonic cadence). */
    THREE_BELL
}
