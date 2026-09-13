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
 */

package com.android.phone;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

public class ButtonGridLayout extends ViewGroup {

    private final int mColumns = 3;
    
    public ButtonGridLayout(Context context) {
        super(context);
    }

    public ButtonGridLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ButtonGridLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }
    
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int y = getPaddingTop();
        final int rows = getRows();
        final View child0 = getChildAt(0);
        final int yInc = (getHeight() - getPaddingTop() - getPaddingBottom()) / rows;
        final int xInc = (getWidth() - getPaddingLeft() - getPaddingRight()) / mColumns;
        final int childWidth = child0.getMeasuredWidth();
        final int childHeight = child0.getMeasuredHeight();
        final int xOffset = (xInc - childWidth) / 2;
        final int yOffset = (yInc - childHeight) / 2;
        
        for (int row = 0; row < rows; row++) {
            int x = getPaddingLeft();
            for (int col = 0; col < mColumns; col++) {
                int cell = row * mColumns + col;
                if (cell >= getChildCount()) {
                    break;
                }
                View child = getChildAt(cell);
                child.layout(x + xOffset, y + yOffset, 
                        x + xOffset + childWidth, 
                        y + yOffset + childHeight);
                x += xInc;
            }
            y += yInc;
        }
    }

    private int getRows() {
        return (getChildCount() + mColumns - 1) / mColumns; 
    }

    /*
     * CORREÇÃO (layout "finger" comprimido em telas modernas):
     *
     * A versão original media cada botão com MeasureSpec.UNSPECIFIED nos
     * dois eixos. Nesse modo, uma View comum IGNORA o android:layout_width/
     * height declarado no XML (96dip x 76dip no dialpad.xml) e volta pro seu
     * tamanho intrínseco mínimo (o do drawable de fundo) -- por isso os
     * botões apareciam minúsculos/"comprimidos" dentro da célula do grid,
     * em qualquer aparelho, independente da densidade de tela.
     *
     * A correção mede cada botão com MeasureSpec.EXACTLY, calculando o
     * tamanho da célula a partir da largura REAL disponível na tela (em vez
     * de um valor fixo pensado pra ~320dp do Donut original). Isso resolve
     * os dois problemas de uma vez: honra o tamanho pretendido no XML E
     * adapta o grid pra caber certinho em qualquer largura de tela (do
     * Galaxy A35 a um tablet), mantendo a mesma grade de 3 colunas ("finger"
     * grid) e a proporção original dos ícones (sem esticar/achatar).
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int rows = getRows();
        final int paddingH = getPaddingLeft() + getPaddingRight();
        final int paddingV = getPaddingTop() + getPaddingBottom();
        final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        final int widthSize = MeasureSpec.getSize(widthMeasureSpec);

        final View child0 = getChildAt(0);
        final ViewGroup.LayoutParams refLp = child0.getLayoutParams();

        // Proporção original do botão (altura/largura), pra escalar sem
        // distorcer os ícones do teclado.
        final float aspect = (refLp != null && refLp.width > 0 && refLp.height > 0)
                ? (float) refLp.height / (float) refLp.width
                : (76f / 96f);

        int columnWidth;
        if (widthMode == MeasureSpec.EXACTLY || widthMode == MeasureSpec.AT_MOST) {
            // Caso normal: divide a largura real disponível em 3 colunas.
            columnWidth = (widthSize - paddingH) / mColumns;
        } else if (refLp != null && refLp.width > 0) {
            // Sem restrição de largura do pai: cai de volta pro tamanho
            // declarado no XML de cada botão.
            columnWidth = refLp.width;
        } else {
            // Último recurso: mede o botão livremente pra descobrir seu
            // tamanho intrínseco.
            child0.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
            columnWidth = child0.getMeasuredWidth();
        }

        // Pequena folga entre botões (mesma sensação "finger" do original).
        final int hGap = Math.max(0, (int) (columnWidth * 0.08f));
        final int childWidth = Math.max(1, columnWidth - hGap);
        final int childHeight = Math.max(1, Math.round(childWidth * aspect));

        final int childWidthSpec = MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY);
        final int childHeightSpec = MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.EXACTLY);
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).measure(childWidthSpec, childHeightSpec);
        }

        final int vGap = Math.max(0, (int) (childHeight * 0.08f));
        int width = paddingH + mColumns * columnWidth;
        int height = paddingV + rows * (childHeight + vGap);

        width = resolveSize(width, widthMeasureSpec);
        height = resolveSize(height, heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

}
