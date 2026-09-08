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
 * ADAPTADO — o estado máquina de troca de PIN2/habilitar FDN (~230 linhas,
 * IccCard.setIccFdnEnabled/changeIccFdnPassword/supplyPuk2) dependia 100% de
 * API interna signature-only, sem equivalente público -- mesma parede de
 * EnableFdnScreen/ChangeIccPinScreen. Os dois botões de PIN2 agora só mostram
 * a mensagem de "não faço milagre". O terceiro item desta tela
 * ("button_fdn_list_key", definido no fdn_setting.xml) continua funcionando
 * normalmente -- ele já navega pro FdnList, que lê content://icc/fdn do
 * próprio sistema, sem depender de nada daqui.
 * ============================================================================
 */

package com.android.phone;

import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;

import com.android.phone.compat.NotPortedYet;

/**
 * FDN settings UI for the Phone app.
 */
public class FdnSetting extends PreferenceActivity
        implements EditPinPreference.OnPinEnteredListener {

    private static final String BUTTON_FDN_ENABLE_KEY = "button_fdn_enable_key";
    private static final String BUTTON_CHANGE_PIN2_KEY = "button_change_pin2_key";

    private EditPinPreference mButtonEnableFDN;
    private EditPinPreference mButtonChangePin2;

    /**
     * Delegate to the respective handlers -- os dois exigem API interna sem
     * equivalente público, então ambos só mostram o aviso.
     */
    public void onPinEntered(EditPinPreference preference, boolean positiveResult) {
        if (!positiveResult) {
            return;
        }
        if (preference == mButtonEnableFDN) {
            mButtonEnableFDN.setText("");
            NotPortedYet.show(this);
        } else if (preference == mButtonChangePin2) {
            mButtonChangePin2.setText("");
            NotPortedYet.show(this);
        }
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        addPreferencesFromResource(R.xml.fdn_setting);

        //get UI object references
        PreferenceScreen prefSet = getPreferenceScreen();
        mButtonEnableFDN = (EditPinPreference) prefSet.findPreference(BUTTON_FDN_ENABLE_KEY);
        mButtonChangePin2 = (EditPinPreference) prefSet.findPreference(BUTTON_CHANGE_PIN2_KEY);

        mButtonEnableFDN.setOnPinEnteredListener(this);
        mButtonChangePin2.setOnPinEnteredListener(this);
    }
}
