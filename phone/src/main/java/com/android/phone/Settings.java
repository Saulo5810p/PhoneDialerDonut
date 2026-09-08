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
 * ADAPTADO — data roaming (Phone.setDataRoamingEnabled/getDataRoamingEnabled),
 * tipo de rede preferido (Phone.setPreferredNetworkType/getPreferredNetworkType)
 * e roaming CDMA são todos signature-only, sem equivalente público -- mesma
 * parede do resto do grupo de rede/PIN. PhoneFactory.getDefaultPhone() também
 * não existe mais. GSM vs CDMA agora é decidido por
 * TelephonyManager.getPhoneType() (API pública), só pra escolher o layout
 * certo. Layout original mantido (os dois toggles + os dois atalhos de tela);
 * os toggles agora só mostram a mensagem de "não faço milagre" e voltam pro
 * estado desmarcado (não fica um estado "ligado" mentiroso na tela). Os
 * atalhos de "seleção de operadora" e "APN" continuam navegando normalmente
 * (o segundo é resolvido pelo próprio Settings do sistema, via intent do xml).
 * ============================================================================
 */

package com.android.phone;

import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.telephony.TelephonyManager;

import com.android.phone.compat.NotPortedYet;

/**
 * List of Phone-specific settings screens.
 */
public class Settings extends PreferenceActivity {

    private static final String BUTTON_ROAMING_KEY = "button_roaming_key";
    private static final String BUTTON_PREFER_2G_KEY = "button_prefer_2g_key";
    private static final String BUTTON_CDMA_SYSTEM_SELECT_KEY = "cdma_system_select_key";

    private CheckBoxPreference mButtonDataRoam;
    private CheckBoxPreference mButtonPrefer2g;
    private Preference mButtonCdmaRoam;

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == mButtonDataRoam || preference == mButtonPrefer2g
                || preference == mButtonCdmaRoam) {
            // Data roaming / tipo de rede preferido / roaming CDMA exigem API
            // interna signature-only -- ver nota no topo do arquivo.
            if (preference instanceof CheckBoxPreference) {
                ((CheckBoxPreference) preference).setChecked(false);
            }
            NotPortedYet.show(this);
            return true;
        }
        // outros itens (seleção de operadora, APN) seguem pelo fluxo normal de intent do XML.
        return false;
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        boolean isCdma = (tm != null)
                && tm.getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA;

        if (isCdma) {
            addPreferencesFromResource(R.xml.network_setting_cdma);
        } else {
            addPreferencesFromResource(R.xml.network_setting);
        }

        PreferenceScreen prefSet = getPreferenceScreen();
        mButtonDataRoam = (CheckBoxPreference) prefSet.findPreference(BUTTON_ROAMING_KEY);

        if (isCdma) {
            mButtonCdmaRoam = prefSet.findPreference(BUTTON_CDMA_SYSTEM_SELECT_KEY);
        } else {
            mButtonPrefer2g = (CheckBoxPreference) prefSet.findPreference(BUTTON_PREFER_2G_KEY);
        }
    }
}
