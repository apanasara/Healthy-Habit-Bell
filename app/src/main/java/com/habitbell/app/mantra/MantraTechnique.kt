package com.habitbell.app.mantra

/**
 * # MantraTechnique
 *
 * Domain enumeration defining sacred chanting and recitation practices across spiritual
 * traditions, parameterizing acoustic detection, bead goals, and display semantics.
 *
 * ## Architectural Role & Component Relationships
 * - Used by [com.habitbell.app.data.model.MantraCounterConfig] to determine detection algorithms.
 * - Ingested by [AcousticMantraSensorProvider] to parameterize duration envelopes and pause bridging.
 * - Rendered by [com.habitbell.app.ui.components.MantraCounterContent] for scripts, titles, and themes.
 *
 * ## Lifecycle & Concurrency
 * Immutable compile-time enum. Thread-safe across all coroutine dispatchers and background services.
 *
 * @property displayName User-facing title for UI cards and selection chips.
 * @property traditionalName Classical Romanized title with diacritics.
 * @property scriptText Native script representation (Devanagari, Arabic, or English).
 * @property defaultMode Default acoustic detection modality ([MantraMode]).
 * @property defaultBeads Canonical bead target (e.g. 108, 100, 33).
 * @property defaultMinDurationSec Minimum cumulative vocal duration required in seconds.
 * @property defaultInterPauseSec Minimum concluding silence required to confirm completion in seconds.
 * @property description Pedagogical and spiritual summary of the recitation.
 */
enum class MantraTechnique(
    val displayName: String,
    val traditionalName: String,
    val scriptText: String,
    val defaultMode: MantraMode,
    val defaultBeads: Int,
    val defaultMinDurationSec: Float,
    val defaultInterPauseSec: Float,
    val description: String
) {
    /**
     * Gayatri Mantra (गायत्री मन्त्र - Rigveda 3.62.10):
     * Sacred Vedic solar metre invoking divine illumination of intellect and consciousness.
     * Four lines with natural poetic breath pauses: ~10 to 14 seconds per full recitation.
     */
    GAYATRI_MANTRA(
        displayName = "Gayatri Mantra",
        traditionalName = "Gāyatrī Mantra",
        scriptText = "ॐ भूर्भुवः स्वः तत्सवितुर्वरेण्यं भर्गो देवस्य धीमहि धियो यो नः प्रचोदयात्",
        defaultMode = MantraMode.EXTENDED_VERSE,
        defaultBeads = 108,
        defaultMinDurationSec = 2.5f,
        defaultInterPauseSec = 1.8f,
        description = "Sacred Vedic hymn of spiritual illumination and wisdom across 108 Mala beads."
    ),

    /**
     * Maha Mrityunjaya Mantra (महामृत्युंजय मन्त्र - Rigveda 7.59.12):
     * The Great Death-Conquering Mantra dedicated to Tryambaka (Lord Shiva),
     * bestowing rejuvenation, health, and liberation from worldly bondage.
     */
    MAHA_MRITYUNJAYA(
        displayName = "Maha Mrityunjaya",
        traditionalName = "Mahāmṛtyuñjaya Mantra",
        scriptText = "ॐ त्र्यम्बकं यजामहे सुगन्धिं पुष्टिवर्धनम् उर्वारुकमिव बन्धनान् मृत्योर्मुक्षीय मामृतात्",
        defaultMode = MantraMode.EXTENDED_VERSE,
        defaultBeads = 108,
        defaultMinDurationSec = 2.8f,
        defaultInterPauseSec = 1.8f,
        description = "The Great Death-Conquering Mantra for health, longevity, and liberation."
    ),

    /**
     * Aumkar / Omkar Chanting (ॐकार):
     * Sacred primordial cosmic vibration (Pranava). Prolonged vocal resonance
     * engaging the three states of consciousness (A-U-M) ending with silent nasal resonance.
     */
    AUMKAR(
        displayName = "Aumkar Chanting",
        traditionalName = "Praṇava Oṃkāra",
        scriptText = "ॐ",
        defaultMode = MantraMode.AUMKAR_DRONE,
        defaultBeads = 21,
        defaultMinDurationSec = 2.2f,
        defaultInterPauseSec = 1.2f,
        description = "Deep primordial resonance chanting aligning physical and psychic energies."
    ),

    /**
     * Ram Naam Japa (राम नाम जप):
     * Rhythmic repetition of the Taraka Mantra 'Ram'. Compact vocal burst
     * delivering tranquil stillness and mental clarity.
     */
    RAM_JAPA(
        displayName = "Ram Naam Japa",
        traditionalName = "Rāma Nāma Japa",
        scriptText = "श्री राम जय राम जय जय राम",
        defaultMode = MantraMode.SHORT_JAPA,
        defaultBeads = 108,
        defaultMinDurationSec = 0.12f,
        defaultInterPauseSec = 0.45f,
        description = "Rhythmic continuous Taraka mantra japa across traditional 108-bead Mala."
    ),

    /**
     * Islamic Tasbih / Dhikr (تسبيح):
     * Devotional glorification of the Divine (SubhanAllah, Alhamdulillah, Allahu Akbar)
     * across 33, 99, or 100 beads.
     */
    TASBIH_DHIKR(
        displayName = "Tasbih / Dhikr",
        traditionalName = "Tasbīḥ Fāṭimah",
        scriptText = "سُبْحَانَ ٱللَّٰهِ • ٱلْحَمْدُ لِلَّٰهِ • ٱللَّٰهُ أَكْبَرُ",
        defaultMode = MantraMode.SHORT_JAPA,
        defaultBeads = 100,
        defaultMinDurationSec = 0.18f,
        defaultInterPauseSec = 0.50f,
        description = "Islamic prayer beads remembrance (33 SubhanAllah, 33 Alhamdulillah, 34 Allahu Akbar)."
    ),

    /**
     * The Jesus Prayer / Christian Prayer Rope (Chotki):
     * Eastern Christian contemplative prayer ('Lord Jesus Christ, Son of God, have mercy on me').
     * Traditionally counted on a 33 or 100-knot prayer rope.
     */
    JESUS_PRAYER(
        displayName = "Jesus Prayer",
        traditionalName = "Oratio Iesu (Chotki)",
        scriptText = "Kyrie Eleison • Lord Jesus Christ, have mercy on me",
        defaultMode = MantraMode.SHORT_JAPA,
        defaultBeads = 33,
        defaultMinDurationSec = 0.8f,
        defaultInterPauseSec = 0.6f,
        description = "Contemplative Christian hesychastic prayer counted on a 33 or 100-knot prayer rope."
    ),

    /**
     * Universal Sacred Scripture / Verse:
     * Unconstrained open-ended recitation counter for any personal sloka, surah,
     * psalm, or affirmation with customizable verse duration.
     */
    UNIVERSAL_VERSE(
        displayName = "Universal Scripture",
        traditionalName = "Sārvajanīna Śloka",
        scriptText = "Sacred Verse & Scripture",
        defaultMode = MantraMode.EXTENDED_VERSE,
        defaultBeads = 108,
        defaultMinDurationSec = 2.2f,
        defaultInterPauseSec = 1.6f,
        description = "Customizable verse and scripture recitation counter supporting any spiritual tradition."
    )
}
