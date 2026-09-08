/*
 * Copyright (C) 2006 The Android Open Source Project
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
 * ============================================================================
 * ADAPTADO — habilitar/desabilitar o bloqueio de PIN do SIM
 * (IccCard.setIccLockEnabled/getIccLockEnabled) é signature-only, sem
 * equivalente público, igual ChangeIccPinScreen. Layout original mantido; o
 * botão mostra a mensagem de "não faço milagre" em vez de chamar a API
 * interna. Como não dá mais pra consultar o estado real do bloqueio, o
 * título sempre mostra "Enable" (não há como saber se já está habilitado).
 * ============================================================================
 */

package com.android.phone;

import android.app.Activity;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.DigitsKeyListener;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.phone.compat.NotPortedYet;

/**
 * UI to enable/disable the ICC PIN.
 */
public class EnableIccPinScreen extends Activity {

    private LinearLayout mPinFieldContainer;
    private EditText mPinField;
    private TextView mStatusField;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        setContentView(R.layout.enable_sim_pin_screen);
        setupView();

        setTitle(getResources().getText(R.string.enable_sim_pin));
    }

    private void setupView() {
        mPinField = (EditText) findViewById(R.id.pin);
        mPinField.setKeyListener(DigitsKeyListener.getInstance());
        mPinField.setMovementMethod(null);
        mPinField.setOnClickListener(mClicked);

        mPinFieldContainer = (LinearLayout) findViewById(R.id.pinc);
        mStatusField = (TextView) findViewById(R.id.status);
    }

    private View.OnClickListener mClicked = new View.OnClickListener() {
        public void onClick(View v) {
            if (TextUtils.isEmpty(mPinField.getText())) {
                return;
            }
            // Habilitar/desabilitar PIN do SIM exige API interna
            // signature-only -- ver nota no topo do arquivo.
            NotPortedYet.show(EnableIccPinScreen.this);
        }
    };
}
