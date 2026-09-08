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
 * ADAPTADO — trocar PIN/PUK do SIM não tem NENHUM equivalente público no
 * Android moderno (com.android.internal.telephony.IccCard.changeIccLockPassword/
 * changeIccFdnPassword/supplyPuk2 são signature-only, exclusivas do processo de
 * sistema, mesmo com root). Por decisão do Saulo: o layout original de 2009
 * continua aqui intacto (todos os campos, foco por Enter, painel de PUK), só
 * o botão final não tenta mais chamar a API interna -- mostra a mensagem de
 * "não faço milagre" (compat/NotPortedYet) no lugar.
 * ============================================================================
 */

package com.android.phone;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.method.DigitsKeyListener;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.android.phone.compat.NotPortedYet;

/**
 * "Change ICC PIN" UI for the Phone app.
 */
public class ChangeIccPinScreen extends Activity {

    private boolean mChangePin2;
    private TextView mBadPinError;
    private TextView mMismatchError;
    private EditText mOldPin;
    private EditText mNewPin1;
    private EditText mNewPin2;
    private EditText mPUKCode;
    private Button mButton;
    private Button mPUKSubmit;
    private ScrollView mScrollView;

    private LinearLayout mIccPUKPanel;

    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        resolveIntent();

        setContentView(R.layout.change_sim_pin_screen);

        mOldPin = (EditText) findViewById(R.id.old_pin);
        mOldPin.setKeyListener(DigitsKeyListener.getInstance());
        mOldPin.setMovementMethod(null);
        mOldPin.setOnClickListener(mClicked);

        mNewPin1 = (EditText) findViewById(R.id.new_pin1);
        mNewPin1.setKeyListener(DigitsKeyListener.getInstance());
        mNewPin1.setMovementMethod(null);
        mNewPin1.setOnClickListener(mClicked);

        mNewPin2 = (EditText) findViewById(R.id.new_pin2);
        mNewPin2.setKeyListener(DigitsKeyListener.getInstance());
        mNewPin2.setMovementMethod(null);
        mNewPin2.setOnClickListener(mClicked);

        mBadPinError = (TextView) findViewById(R.id.bad_pin);
        mMismatchError = (TextView) findViewById(R.id.mismatch);

        mButton = (Button) findViewById(R.id.button);
        mButton.setOnClickListener(mClicked);

        mScrollView = (ScrollView) findViewById(R.id.scroll);

        mPUKCode = (EditText) findViewById(R.id.puk_code);
        mPUKCode.setKeyListener(DigitsKeyListener.getInstance());
        mPUKCode.setMovementMethod(null);
        mPUKCode.setOnClickListener(mClicked);

        mPUKSubmit = (Button) findViewById(R.id.puk_submit);
        mPUKSubmit.setOnClickListener(mClicked);

        mIccPUKPanel = (LinearLayout) findViewById(R.id.puk_panel);

        int id = mChangePin2 ? R.string.change_pin2 : R.string.change_pin;
        setTitle(getResources().getText(id));
    }

    private void resolveIntent() {
        Intent intent = getIntent();
        mChangePin2 = intent.getBooleanExtra("pin2", mChangePin2);
    }

    private View.OnClickListener mClicked = new View.OnClickListener() {
        public void onClick(View v) {
            if (v == mOldPin) {
                mNewPin1.requestFocus();
            } else if (v == mNewPin1) {
                mNewPin2.requestFocus();
            } else if (v == mNewPin2) {
                mButton.requestFocus();
            } else if (v == mButton || v == mPUKSubmit) {
                // Trocar PIN/PUK do SIM exige API interna signature-only que
                // nenhum app comum recebe -- ver nota no topo do arquivo.
                NotPortedYet.show(ChangeIccPinScreen.this);
            } else if (v == mPUKCode) {
                mPUKSubmit.requestFocus();
            }
        }
    };
}
