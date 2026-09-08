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
 * ADAPTADO — escanear/selecionar operadora manualmente (Phone.selectNetwork
 * Manually/setNetworkSelectionModeAutomatic, e o próprio NetworkQueryService
 * que fazia o scan via RIL interno) é signature-only, sem equivalente
 * público. NetworkQueryService.java e os .aidl que o acompanhavam foram
 * excluídos do build (ver STATUS-PROJETO.md). O layout desta tela continua
 * (lista vazia + os dois botões), mas "buscar redes" e "selecionar
 * automaticamente" só mostram a mensagem de "não faço milagre".
 * ============================================================================
 */

package com.android.phone;

import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;

import com.android.phone.compat.NotPortedYet;

/**
 * "Networks" settings UI for the Phone app.
 */
public class NetworkSetting extends PreferenceActivity {

    //String keys for preference lookup
    private static final String LIST_NETWORKS_KEY = "list_networks_key";
    private static final String BUTTON_SRCH_NETWRKS_KEY = "button_srch_netwrks_key";
    private static final String BUTTON_AUTO_SELECT_KEY = "button_auto_select_key";

    private PreferenceGroup mNetworkList;
    private Preference mSearchButton;
    private Preference mAutoSelect;

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference == mSearchButton || preference == mAutoSelect) {
            // Buscar/selecionar operadora exige API interna signature-only
            // -- ver nota no topo do arquivo.
            NotPortedYet.show(this);
            return true;
        }
        return false;
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        addPreferencesFromResource(R.xml.carrier_select);

        mNetworkList = (PreferenceGroup) getPreferenceScreen().findPreference(LIST_NETWORKS_KEY);
        mNetworkList.setTitle(R.string.empty_networks_list);

        mSearchButton = getPreferenceScreen().findPreference(BUTTON_SRCH_NETWRKS_KEY);
        mAutoSelect = getPreferenceScreen().findPreference(BUTTON_AUTO_SELECT_KEY);
    }
}
