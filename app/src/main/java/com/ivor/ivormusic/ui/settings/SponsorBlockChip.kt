package com.ivor.ivormusic.ui.settings

import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.SponsorCategory

/** The user-facing name of a category, shared with the settings page. */
internal fun categoryLabel(category: SponsorCategory): Int = when (category) {
    SponsorCategory.SPONSOR -> R.string.sb_cat_sponsor
    SponsorCategory.SELF_PROMO -> R.string.sb_cat_selfpromo
    SponsorCategory.INTERACTION -> R.string.sb_cat_interaction
    SponsorCategory.INTRO -> R.string.sb_cat_intro
    SponsorCategory.OUTRO -> R.string.sb_cat_outro
    SponsorCategory.PREVIEW -> R.string.sb_cat_preview
    SponsorCategory.MUSIC_OFFTOPIC -> R.string.sb_cat_music_offtopic
    SponsorCategory.FILLER -> R.string.sb_cat_filler
}

/** The one-line explanation of what a category actually covers. */
internal fun categoryDescription(category: SponsorCategory): Int = when (category) {
    SponsorCategory.SPONSOR -> R.string.sb_cat_sponsor_sub
    SponsorCategory.SELF_PROMO -> R.string.sb_cat_selfpromo_sub
    SponsorCategory.INTERACTION -> R.string.sb_cat_interaction_sub
    SponsorCategory.INTRO -> R.string.sb_cat_intro_sub
    SponsorCategory.OUTRO -> R.string.sb_cat_outro_sub
    SponsorCategory.PREVIEW -> R.string.sb_cat_preview_sub
    SponsorCategory.MUSIC_OFFTOPIC -> R.string.sb_cat_music_offtopic_sub
    SponsorCategory.FILLER -> R.string.sb_cat_filler_sub
}
