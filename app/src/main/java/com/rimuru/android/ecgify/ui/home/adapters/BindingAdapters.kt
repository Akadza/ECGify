package com.rimuru.android.ecgify.ui.home.adapters

import androidx.annotation.ColorRes
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.databinding.BindingAdapter

@BindingAdapter("cardBackgroundColorRes")
fun setCardBackgroundColorRes(cardView: CardView, @ColorRes colorRes: Int) {
    cardView.setCardBackgroundColor(ContextCompat.getColor(cardView.context, colorRes))
}