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
 * tela de chamada não tem NENHUM jeito de desligar).
 *
 * CORREÇÃO (botões que não respondiam a toque nenhum): a primeira
 * reimplementação usava android.widget.GridLayout com 10 colunas fixas,
 * das quais só 2-4 eram realmente usadas por linha. As colunas vazias
 * ficavam como "zona morta" dentro da célula de cada botão -- a maior
 * parte da área de cada linha não pertencia a nenhum botão de verdade.
 * Como este ViewGroup fica sobreposto à alça do SlidingDrawer do teclado
 * numérico (ver incall_screen.xml), um toque nessa zona morta atravessava
 * o grid (que não tem clique próprio) e era engolido pela alça do teclado
 * embaixo, sem efeito nenhum -- exatamente o bug relatado.
 *
 * Reimplementado como linhas horizontais (LinearLayout) de peso igual:
 * cada botão recebe layout_weight=1 e width=0 dentro da sua linha, então
 * SEMPRE ocupa sua fatia inteira, sem sobra nem zona morta -- garante que
 * qualquer toque dentro da linha caia em cima de algum botão real, e
 * deixa a barra de controle centralizada e esticada pra tela inteira em
 * qualquer aparelho. A linha extra (Gerenciar conferência/Adicionar
 * chamada) some sozinha quando nenhum dos dois se aplica, já que
 * LinearLayout redistribui peso só entre filhos VISÍVEIS.
 */

package com.android.phone;

import android.content.Context;
import android.util.Log;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;

class InCallMenuView extends LinearLayout {
    private static final String LOG_TAG = "PHONE/InCallMenuView";
    private static final boolean DBG = false;

    /** Linha de cima: Mostrar / Trocar / Mesclar / Finalizar (ou Atender variantes). */
    static final int ROW_TOP = 0;
    /** Linha de baixo: Em espera / Mudo / Viva-voz / Bluetooth. */
    static final int ROW_BOTTOM = 1;
    /** Linha extra: Gerenciar conferência / Adicionar chamada (só aparecem quando fazem sentido). */
    static final int ROW_EXTRA = 2;
    private static final int NUM_ROWS = 3;

    private final LinearLayout[] mRows = new LinearLayout[NUM_ROWS];

    private InCallScreen mInCallScreen;

    InCallMenuView(Context context, InCallScreen inCallScreen) {
        super(context);
        if (DBG) log("InCallMenuView constructor...");

        mInCallScreen = inCallScreen;

        setOrientation(VERTICAL);
        setBackgroundColor(0xE0303030);  // "caixinha cinza" translúcida, como no Donut original
        setPadding(4, 4, 4, 4);
        // Consome qualquer toque residual nas margens entre botões, pra
        // ele nunca vazar pro SlidingDrawer do teclado logo abaixo.
        setClickable(true);

        for (int row = 0; row < NUM_ROWS; row++) {
            LinearLayout rowLayout = new LinearLayout(context);
            rowLayout.setOrientation(HORIZONTAL);
            rowLayout.setGravity(Gravity.CENTER);
            rowLayout.setClickable(true);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            addView(rowLayout, rowLp);
            mRows[row] = rowLayout;
        }

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
     * Adds an InCallMenuItemView to the specified row (ROW_TOP ou
     * ROW_BOTTOM). Cada item recebe peso igual (1) dentro da linha, então
     * a linha inteira é sempre dividida em fatias iguais entre os itens
     * VISÍVEIS -- se um item estiver GONE, os outros crescem pra ocupar o
     * espaço dele automaticamente.
     */
    /* package */ void addItemView(InCallMenuItemView itemView, int row) {
        if (row >= NUM_ROWS) {
            throw new IllegalStateException("Row index " + row + " > NUM_ROWS");
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(2, 2, 2, 2);
        mRows[row].addView(itemView, lp);
    }

    /**
     * Hoje é só um gatilho de relayout (LinearLayout já redistribui peso
     * sozinho quando um item vira GONE) -- mantido pra não quebrar quem
     * chama.
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
