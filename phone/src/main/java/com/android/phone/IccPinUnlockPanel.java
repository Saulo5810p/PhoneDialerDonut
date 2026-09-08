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
 * ADAPTADO — desbloquear o SIM com PIN/PUK (IccCard.supplyPin/supplyPuk, e a
 * própria consulta de estado IccCard.getState()) é signature-only, sem
 * equivalente público -- mesma parede de ChangeIccPinScreen/EnableIccPinScreen.
 * PhoneApp também não expõe mais um campo "phone" (Phone interno) desde a
 * reescrita do PhoneApp.java. Layout original do painel mantido; o botão de
 * desbloqueio mostra a mensagem de "não faço milagre". Como não dá mais pra
 * consultar se o SIM pede PIN ou PUK, o painel sempre assume o fluxo de PIN.
 * ============================================================================
 */

package com.android.phone;

import android.content.Context;
import android.os.Bundle;
import android.text.Spannable;
import android.text.TextUtils;
import android.text.method.DialerKeyListener;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.phone.compat.NotPortedYet;

/**
 * Panel where you enter your PIN to unlock the SIM card.
 */
public class IccPinUnlockPanel extends IccPanel {

    private EditText mEntry;
    private TextView mFailure;
    private TextView mLabel;
    private TextView mStatus;
    private Button mUnlockButton;
    private Button mDismissButton;
    private LinearLayout mUnlockPane;
    private LinearLayout mUnlockInProgressPane;

    public IccPinUnlockPanel(Context context) {
        super(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.sim_unlock);
        initView();
        updateView();
    }

    void initView() {
        mUnlockPane = (LinearLayout) findViewById(R.id.simPINPane);
        mUnlockInProgressPane = (LinearLayout) findViewById(R.id.progress);

        mEntry = (EditText) findViewById(R.id.entry);
        mEntry.setKeyListener(DialerKeyListener.getInstance());
        mEntry.setMovementMethod(null);
        mEntry.setOnClickListener(mUnlockListener);

        mFailure = (TextView) findViewById(R.id.failure);
        mLabel = (TextView) findViewById(R.id.label);
        mStatus = (TextView) findViewById(R.id.status);

        mUnlockButton = (Button) findViewById(R.id.unlock);
        mUnlockButton.setOnClickListener(mUnlockListener);

        mDismissButton = (Button) findViewById(R.id.dismiss);
        mDismissButton.setOnClickListener(mDismissListener);
    }

    void updateView() {
        Context context = getContext();
        mLabel.setText(context.getText(R.string.enterPin));
        mEntry.getText().clear();
        mEntry.requestFocus(View.FOCUS_FORWARD);
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    View.OnClickListener mUnlockListener = new View.OnClickListener() {
        public void onClick(View v) {
            String code = mEntry.getText().toString();

            if (TextUtils.isEmpty(code)) {
                return;
            }

            // Desbloquear o SIM com PIN/PUK exige API interna signature-only
            // -- ver nota no topo do arquivo.
            NotPortedYet.show(getContext());
        }
    };

    View.OnClickListener mDismissListener = new View.OnClickListener() {
        public void onClick(View v) {
            dismiss();
        }
    };
}
