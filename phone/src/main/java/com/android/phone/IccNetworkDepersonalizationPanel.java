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
 * ============================================================================
 * ADAPTADO — desbloqueio de "SIM network lock" (carrier unlock,
 * IccCard.supplyNetworkDepersonalization) é signature-only, sem equivalente
 * público. Além disso este painel só era mostrado quando o processo interno
 * de telefonia (que na Donut original era o próprio app Phone, via
 * sharedUserId) disparava o evento PhoneApp.EVENT_SIM_NETWORK_LOCKED -- esse
 * evento não existe mais pra um app comum, então na prática nada aciona mais
 * este painel hoje. Mantido do jeito que o Saulo pediu (layout intacto, botão
 * vira aviso) para o caso de um caminho de entrada ser reconstruído depois.
 * ============================================================================
 */

package com.android.phone;

import android.content.Context;
import android.os.Bundle;
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
 * "SIM network unlock" PIN entry screen.
 */
public class IccNetworkDepersonalizationPanel extends IccPanel {

    //UI elements
    private EditText     mPinEntry;
    private LinearLayout mEntryPanel;
    private LinearLayout mStatusPanel;
    private TextView     mStatusText;

    private Button       mUnlockButton;
    private Button       mDismissButton;

    //constructor
    public IccNetworkDepersonalizationPanel(Context context) {
        super(context);
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.sim_ndp);

        // PIN entry text field
        mPinEntry = (EditText) findViewById(R.id.pin_entry);
        mPinEntry.setKeyListener(DialerKeyListener.getInstance());
        mPinEntry.setOnClickListener(mUnlockListener);

        mEntryPanel = (LinearLayout) findViewById(R.id.entry_panel);

        mUnlockButton = (Button) findViewById(R.id.ndp_unlock);
        mUnlockButton.setOnClickListener(mUnlockListener);

        // The "Dismiss" button is present in some (but not all) products,
        // based on the "sim_network_unlock_allow_dismiss" resource.
        mDismissButton = (Button) findViewById(R.id.ndp_dismiss);
        if (getContext().getResources().getBoolean(R.bool.sim_network_unlock_allow_dismiss)) {
            mDismissButton.setVisibility(View.VISIBLE);
            mDismissButton.setOnClickListener(mDismissListener);
        } else {
            mDismissButton.setVisibility(View.GONE);
        }

        //status panel is used since we're having problems with the alert dialog.
        mStatusPanel = (LinearLayout) findViewById(R.id.status_panel);
        mStatusText = (TextView) findViewById(R.id.status_text);
    }

    //Mirrors IccPinUnlockPanel.onKeyDown().
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    View.OnClickListener mUnlockListener = new View.OnClickListener() {
        public void onClick(View v) {
            String pin = mPinEntry.getText().toString();

            if (TextUtils.isEmpty(pin)) {
                return;
            }

            // Desbloqueio de rede do SIM exige API interna signature-only
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
