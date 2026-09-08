/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * ADAPTADO: a única dependência interna era a leitura do
 * "itemTextAppearance" do tema de menu do sistema
 * (com.android.internal.R.styleable.MenuView), só pra manter a fonte
 * consistente com os menus nativos de 2009. Trocado por uma aparência de
 * texto pública fixa — efeito visual equivalente, sem precisar de API
 * interna.
 */

package com.android.phone;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.TextUtils.TruncateAt;
import android.util.Log;
import android.view.Gravity;
import android.widget.TextView;

/**
 * A View for each item do grid de controle de chamada (antigo "in-call
 * menu", hoje sempre visível na tela de chamada em vez de aparecer só com
 * a tecla física MENU).
 *
 * Each item has a text label, an optional "green LED" on/off indicator
 * below the text, and an optional icon above the text.
 */
class InCallMenuItemView extends TextView {
    private static final String LOG_TAG = "PHONE/InCallMenuItemView";
    private static final boolean DBG = false;

    private boolean mIndicatorVisible;
    private boolean mIndicatorState;
    private Drawable mIndicatorDrawable;
    private Drawable mIcon;

    public InCallMenuItemView(Context context) {
        super(context);
        if (DBG) log("InCallMenuItemView constructor...");

        setGravity(Gravity.CENTER);
        setClickable(true);
        setFocusable(true);
        setTextAppearance(context, android.R.style.TextAppearance_Small);
        setTextColor(0xFFFFFFFF);
        setPadding(3, getPaddingTop(), 3, getPaddingBottom());
    }

    // Visibility: we only ever use the VISIBLE and GONE states.

    public void setVisible(boolean isVisible) {
        setVisibility(isVisible ? VISIBLE : GONE);
    }

    public boolean isVisible() {
        return (getVisibility() == VISIBLE);
    }

    /** Sets whether or not this item's "green LED" state indicator should be visible. */
    public void setIndicatorVisible(boolean isVisible) {
        mIndicatorVisible = isVisible;
        updateIndicator();
        updateCompoundDrawables();
    }

    /** Turns this item's "green LED" state indicator on or off. */
    public void setIndicatorState(boolean onoff) {
        mIndicatorState = onoff;
        updateIndicator();
        updateCompoundDrawables();
    }

    /** Sets this item's icon, to be drawn above the text label. */
    public void setIcon(Drawable icon) {
        mIcon = icon;
        updateCompoundDrawables();
        if (icon != null) setSingleLineMarquee();
    }

    /** Sets this item's icon, to be drawn above the text label. */
    public void setIconResource(int resId) {
        Drawable iconDrawable = getResources().getDrawable(resId);
        setIcon(iconDrawable);
    }

    private void updateIndicator() {
        if (mIndicatorVisible) {
            int resId = mIndicatorState ? android.R.drawable.button_onoff_indicator_on
                    : android.R.drawable.button_onoff_indicator_off;
            mIndicatorDrawable = getResources().getDrawable(resId);
        } else {
            mIndicatorDrawable = null;
        }
    }

    private void updateCompoundDrawables() {
        if (mIcon != null) {
            setCompoundDrawablePadding(-10);
        }
        int topPadding = (mIcon != null) ? 5 : 0;
        int bottomPadding = (mIndicatorDrawable != null) ? 5 : 0;
        setPadding(0, topPadding, 0, bottomPadding);
        setCompoundDrawablesWithIntrinsicBounds(null, mIcon, null, mIndicatorDrawable);
    }

    /** Forces this menu item into "single line" mode, with marqueeing enabled. */
    private void setSingleLineMarquee() {
        setEllipsize(TruncateAt.MARQUEE);
        setHorizontalFadingEdgeEnabled(true);
        setSingleLine(true);
    }

    @Override
    public String toString() {
        return "'" + getText() + "' (" + super.toString() + ")";
    }

    private void log(String msg) {
        Log.d(LOG_TAG, msg);
    }
}
