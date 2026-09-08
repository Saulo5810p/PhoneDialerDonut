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
 * ADAPTADO (Rota A): o original era um ViewGroup 100% desenhado na mão
 * (onMeasure/onLayout/onDraw manuais, divisórias lidas de
 * com.android.internal.R.styleable.MenuView/IconMenuView) porque ele
 * simulava o "options panel" clássico do Android — aberto só com a tecla
 * física MENU.
 *
 * Sem tecla MENU nos aparelhos modernos, este grid agora fica sempre
 * visível, fixo na parte de baixo da tela de chamada (é o "controle da
 * ligação": atender, encerrar, mudo, viva-voz, espera, etc — sem ele a
 * tela de chamada não tem NENHUM jeito de desligar). Reimplementado sobre
 * android.widget.GridLayout (API pública) em vez do ViewGroup manual —
 * mesmo efeito visual (grid de botões com ícone+texto), sem precisar de
 * nenhum atributo de tema interno.
 */

package com.android.phone;

import android.content.Context;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.GridLayout;

import java.util.ArrayList;

/**
 * Layout do grid de controle de chamada (antigo "in-call menu").
 * Continua sendo a "View" pura (só organiza e desenha os itens); quem
 * decide o conteúdo/estado de cada item é a InCallMenu.
 */
class InCallMenuView extends GridLayout {
    private static final String LOG_TAG = "PHONE/InCallMenuView";
    private static final boolean DBG = false;

    private static final int NUM_ROWS = 3;
    private static final int MAX_ITEMS_PER_ROW = 10;
    private final InCallMenuItemView[][] mItems = new InCallMenuItemView[NUM_ROWS][MAX_ITEMS_PER_ROW];
    private final int[] mNumItemsForRow = new int[NUM_ROWS];

    private InCallScreen mInCallScreen;

    InCallMenuView(Context context, InCallScreen inCallScreen) {
        super(context);
        if (DBG) log("InCallMenuView constructor...");

        mInCallScreen = inCallScreen;

        setColumnCount(MAX_ITEMS_PER_ROW);
        setRowCount(NUM_ROWS);
        setBackgroundColor(0xE0303030);  // "caixinha cinza" translúcida, como no Donut original
        setPadding(4, 4, 4, 4);

        ViewGroup.LayoutParams lp =
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.WRAP_CONTENT);
        setLayoutParams(lp);
    }

    /**
     * Null out our reference to the InCallScreen activity.
     */
    void clearInCallScreenReference() {
        mInCallScreen = null;
    }

    /**
     * Adds an InCallMenuItemView to the specified row.
     */
    /* package */ void addItemView(InCallMenuItemView itemView, int row) {
        if (row >= NUM_ROWS) {
            throw new IllegalStateException("Row index " + row + " > NUM_ROWS");
        }
        int indexInRow = mNumItemsForRow[row];
        if (indexInRow >= MAX_ITEMS_PER_ROW) {
            throw new IllegalStateException("Too many items (" + indexInRow + ") in row " + row);
        }
        mNumItemsForRow[row]++;
        mItems[row][indexInRow] = itemView;

        GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                GridLayout.spec(row, 1f), GridLayout.spec(indexInRow, 1f));
        lp.width = 0;
        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        lp.setMargins(2, 2, 2, 2);

        addView(itemView, lp);
    }

    /**
     * Precomputa quantos itens visíveis existem em cada linha. O GridLayout
     * já não desenha um item GONE, então isso hoje é só um gatilho de
     * relayout — mantido pra não quebrar quem chama.
     */
    /* package */ void updateVisibility() {
        requestLayout();
    }

    /* package */ void dumpState() {
        if (DBG) log("============ dumpState() ============");
    }

    private void log(String msg) {
        Log.d(LOG_TAG, msg);
    }
}
